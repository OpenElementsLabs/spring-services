package com.openelements.spring.base.services.slack.authorization;

import com.openelements.spring.base.problem.ProblemException;
import com.openelements.spring.base.problem.ProblemFactory;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.oauth.OAuthV2AccessResponse;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Lets a Slack user allow the application to edit their messages, through Slack's OAuth v2 flow.
 *
 * <p>{@link #authorize} sends the browser to Slack's authorization dialog, requesting only the user
 * scope {@code chat:write}. {@link #callback} receives Slack's answer, exchanges the code for the
 * user's token and stores it for the user Slack names in its answer — never for a user named in the
 * request, so whoever opens the link can only authorize as themselves.
 *
 * <p>A random state travels both as query parameter and as cookie; a callback whose state does not
 * match the cookie was not started in the same browser and is rejected. No server-side state is
 * needed, so this works across instances.
 *
 * <p>Rejections are logged without code, state, cookie or token and answered with the status of
 * their {@link SlackUserAuthorizationProblemType}: as an RFC 9457 problem while problem details are
 * enabled, with an empty body otherwise.
 */
public final class SlackUserAuthorizationEndpoint {

  /** The name of the cookie that carries the state between the two requests. */
  public static final String STATE_COOKIE = "slack_oauth_state";

  /** The user scope that is requested; it allows editing the user's messages. */
  public static final String USER_SCOPE = "chat:write";

  static final String SLACK_AUTHORIZE_URL = "https://slack.com/oauth/v2/authorize";

  static final String SUCCESS_MESSAGE = "Slack authorization complete. You can close this window.";

  private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);

  private static final int STATE_BYTES = 32;

  private static final Logger LOG = LoggerFactory.getLogger(SlackUserAuthorizationEndpoint.class);

  private final SlackUserAuthorizationProperties properties;

  private final @Nullable SlackUserTokenStore store;

  private final Slack slack;

  private final @Nullable String teamId;

  private final ObjectProvider<ProblemFactory> problemFactory;

  private final SecureRandom random = new SecureRandom();

  /**
   * Creates the endpoint.
   *
   * @param properties the configuration; if a required value is missing, every request is answered
   *     with {@code 503}
   * @param store the token store, or {@code null} if none is available, in which case every request
   *     is answered with {@code 503}
   * @param slack the Slack SDK that exchanges the code
   * @param teamId if set, only authorizations for this Slack workspace are accepted
   * @param problemFactory builds the problem for a rejection; absent while problem details are
   *     disabled
   */
  public SlackUserAuthorizationEndpoint(
      final SlackUserAuthorizationProperties properties,
      final @Nullable SlackUserTokenStore store,
      final Slack slack,
      final @Nullable String teamId,
      final ObjectProvider<ProblemFactory> problemFactory) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.store = store;
    this.slack = Objects.requireNonNull(slack, "slack must not be null");
    this.teamId = teamId;
    this.problemFactory = Objects.requireNonNull(problemFactory, "problemFactory must not be null");
  }

  /**
   * Starts the authorization: sets the state cookie and redirects to Slack's dialog.
   *
   * @param request the request
   * @return a redirect to Slack, or a problem if the feature is not configured
   */
  public ServerResponse authorize(final ServerRequest request) {
    try {
      requireConfigured();
      final String state = newState();
      final Map<String, String> values = new LinkedHashMap<>();
      values.put("clientId", properties.clientId());
      values.put("scope", USER_SCOPE);
      values.put("redirectUri", properties.redirectUri());
      values.put("state", state);
      final UriComponentsBuilder location =
          UriComponentsBuilder.fromUriString(SLACK_AUTHORIZE_URL)
              .queryParam("client_id", "{clientId}")
              .queryParam("user_scope", "{scope}")
              .queryParam("redirect_uri", "{redirectUri}")
              .queryParam("state", "{state}");
      if (teamId != null) {
        location.queryParam("team", "{team}");
        values.put("team", teamId);
      }
      final URI uri = location.encode().buildAndExpand(values).toUri();
      return ServerResponse.status(HttpStatus.FOUND)
          .location(uri)
          .header(HttpHeaders.SET_COOKIE, stateCookie(state, STATE_LIFETIME))
          .build();
    } catch (final ProblemException e) {
      return problem(e, request);
    }
  }

  /**
   * Completes the authorization: checks the state, exchanges the code and stores the user's token.
   *
   * @param request the request Slack redirected the browser with
   * @return a plain-text confirmation, or a problem if the authorization is rejected or failed
   */
  public ServerResponse callback(final ServerRequest request) {
    try {
      return processCallback(request);
    } catch (final ProblemException e) {
      return problem(e, request);
    }
  }

  private ServerResponse processCallback(final ServerRequest request) {
    final SlackUserTokenStore tokenStore = requireConfigured();
    if (request.param("error").isPresent()) {
      throw rejected("the user cancelled the authorization");
    }
    final Cookie cookie = request.cookies().getFirst(STATE_COOKIE);
    if (!stateMatches(request.param("state").orElse(null), cookie == null ? null : cookie.getValue())) {
      throw rejected("state missing or not matching the cookie");
    }
    final String code = request.param("code").orElseThrow(() -> rejected("code missing"));
    final OAuthV2AccessResponse response = exchange(code);
    final OAuthV2AccessResponse.AuthedUser user = response.getAuthedUser();
    if (user == null || user.getId() == null || user.getAccessToken() == null) {
      throw failed("Slack issued no user token");
    }
    if (user.getRefreshToken() != null) {
      LOG.warn(
          "Rejected Slack user authorization: token rotation is on in the Slack app. Turn it off"
              + " under 'OAuth & Permissions', stored user tokens would expire after 12 hours.");
      throw new ProblemException(SlackUserAuthorizationProblemType.FAILED);
    }
    if (user.getScope() == null || !Arrays.asList(user.getScope().split(",")).contains(USER_SCOPE)) {
      throw failed("the user token lacks the scope " + USER_SCOPE);
    }
    final String authorizedTeam = response.getTeam() == null ? null : response.getTeam().getId();
    if (authorizedTeam == null) {
      throw failed("Slack named no workspace");
    }
    if (teamId != null && !teamId.equals(authorizedTeam)) {
      throw rejected("authorization for another workspace");
    }
    tokenStore.save(authorizedTeam, user.getId(), user.getAccessToken());
    LOG.info("Stored the Slack user token of user {} in workspace {}", user.getId(), authorizedTeam);
    return ServerResponse.ok()
        .contentType(MediaType.TEXT_PLAIN)
        .header(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO))
        .body(SUCCESS_MESSAGE);
  }

  private OAuthV2AccessResponse exchange(final String code) {
    final OAuthV2AccessResponse response;
    try {
      response =
          slack
              .methods()
              .oauthV2Access(
                  req ->
                      req.clientId(properties.clientId())
                          .clientSecret(properties.clientSecret())
                          .code(code)
                          .redirectUri(properties.redirectUri()));
    } catch (final SlackApiException | IOException e) {
      throw failed("code exchange failed (" + e.getClass().getSimpleName() + ")");
    }
    if (!response.isOk()) {
      throw failed("code exchange failed (" + response.getError() + ")");
    }
    return response;
  }

  private SlackUserTokenStore requireConfigured() {
    if (store == null || !properties.missingProperties().isEmpty()) {
      LOG.warn("Rejected Slack user authorization request: the feature is not configured");
      throw new ProblemException(SlackUserAuthorizationProblemType.NOT_CONFIGURED);
    }
    return store;
  }

  private String newState() {
    final byte[] bytes = new byte[STATE_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String stateCookie(final String value, final Duration maxAge) {
    return ResponseCookie.from(STATE_COOKIE, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path(properties.path())
        .maxAge(maxAge)
        .build()
        .toString();
  }

  /** The cookie is cleared with every problem, so a rejected state cannot be tried again. */
  private ServerResponse problem(final ProblemException exception, final ServerRequest request) {
    final ServerResponse.BodyBuilder response =
        ServerResponse.status(exception.type().status())
            .header(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO));
    final ProblemFactory factory = problemFactory.getIfAvailable();
    if (factory == null) {
      return response.build();
    }
    return response
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(factory.create(exception, request.servletRequest()));
  }

  private static boolean stateMatches(final @Nullable String state, final @Nullable String cookie) {
    return state != null
        && cookie != null
        && !state.isEmpty()
        && MessageDigest.isEqual(
            state.getBytes(StandardCharsets.UTF_8), cookie.getBytes(StandardCharsets.UTF_8));
  }

  private static ProblemException rejected(final String reason) {
    LOG.warn("Rejected Slack user authorization: {}", reason);
    return new ProblemException(SlackUserAuthorizationProblemType.REJECTED);
  }

  private static ProblemException failed(final String reason) {
    LOG.warn("Slack user authorization failed: {}", reason);
    return new ProblemException(SlackUserAuthorizationProblemType.FAILED);
  }
}
