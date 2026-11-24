package com.openelements.spring.base.services.slack.events;

import com.openelements.spring.base.problem.ProblemException;
import com.openelements.spring.base.problem.ProblemFactory;
import java.io.IOException;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Handles requests of the Slack Events API and publishes each user message as a {@link
 * SlackMessageReceivedEvent}.
 *
 * <p>A request is processed only after its signature was verified; the body is parsed from the same
 * bytes that were verified. Without a signing secret every request is rejected. Rejections are
 * logged without body or headers and answered with the status of their {@link
 * SlackEventsProblemType}: as an RFC 9457 problem while problem details are enabled, with an empty
 * body otherwise.
 *
 * <p>Events are published on a separate executor, so Slack gets its acknowledgement within its
 * three-second limit regardless of how long listeners take, and a failing listener never affects
 * the response.
 */
public final class SlackEventsEndpoint {

  /** The largest request body that is read, in bytes. */
  public static final int MAX_BODY_BYTES = 1024 * 1024;

  static final String TIMESTAMP_HEADER = "X-Slack-Request-Timestamp";

  static final String SIGNATURE_HEADER = "X-Slack-Signature";

  private static final Logger LOG = LoggerFactory.getLogger(SlackEventsEndpoint.class);

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final @Nullable SlackSignatureVerifier verifier;

  private final @Nullable String teamId;

  private final @Nullable String appId;

  private final SlackEventDeduplicator deduplicator;

  private final ApplicationEventPublisher publisher;

  private final Executor executor;

  private final ObjectProvider<ProblemFactory> problemFactory;

  /**
   * Creates the endpoint.
   *
   * @param verifier the signature verifier, or {@code null} if no signing secret is configured, in
   *     which case every request is rejected
   * @param properties the configuration; {@code teamId} and {@code appId} restrict the accepted
   *     events
   * @param deduplicator drops Slack's retries of an already processed event
   * @param publisher publishes the events
   * @param executor runs the listeners
   * @param problemFactory builds the problem for a rejection; absent while problem details are
   *     disabled
   */
  public SlackEventsEndpoint(
      final @Nullable SlackSignatureVerifier verifier,
      final SlackEventsProperties properties,
      final SlackEventDeduplicator deduplicator,
      final ApplicationEventPublisher publisher,
      final Executor executor,
      final ObjectProvider<ProblemFactory> problemFactory) {
    Objects.requireNonNull(properties, "properties must not be null");
    this.verifier = verifier;
    this.teamId = properties.teamId();
    this.appId = properties.appId();
    this.deduplicator = Objects.requireNonNull(deduplicator, "deduplicator must not be null");
    this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    this.problemFactory = Objects.requireNonNull(problemFactory, "problemFactory must not be null");
  }

  /**
   * Handles one request of the Slack Events API.
   *
   * @param request the request
   * @return the challenge for {@code url_verification}; an error status if the feature is not
   *     configured, the body is too large or the request is not verifiably from Slack; otherwise an
   *     empty {@code 200}
   * @throws IOException if the body cannot be read
   */
  public ServerResponse handle(final ServerRequest request) throws IOException {
    try {
      return process(request);
    } catch (final ProblemException e) {
      return problem(e, request);
    }
  }

  private ServerResponse process(final ServerRequest request) throws IOException {
    if (verifier == null) {
      LOG.warn("Rejected Slack event request: no signing secret configured");
      throw new SlackEventsNotConfiguredException();
    }
    final byte[] body = readBody(request);
    if (!verifier.verify(
        request.headers().firstHeader(TIMESTAMP_HEADER),
        request.headers().firstHeader(SIGNATURE_HEADER),
        body)) {
      throw rejected("missing or invalid signature, or timestamp out of range");
    }
    final JsonNode envelope;
    try {
      envelope = JSON.readTree(body);
    } catch (final JacksonException e) {
      LOG.warn("Ignored Slack event request: signed body is no valid JSON");
      return ServerResponse.badRequest().build();
    }
    return switch (SlackEventMapper.text(envelope, "type")) {
      case "url_verification" ->
          ServerResponse.ok()
              .contentType(MediaType.TEXT_PLAIN)
              .body(Objects.requireNonNullElse(SlackEventMapper.text(envelope, "challenge"), ""));
      case "event_callback" -> handleEventCallback(envelope);
      case null, default -> ServerResponse.ok().build();
    };
  }

  private ServerResponse handleEventCallback(final JsonNode envelope) {
    if (!matches(teamId, SlackEventMapper.text(envelope, "team_id"))
        || !matches(appId, SlackEventMapper.text(envelope, "api_app_id"))) {
      throw rejected("event for another workspace or app");
    }
    SlackEventMapper.map(envelope)
        .filter(event -> deduplicator.firstSeen(event.eventId()))
        .ifPresent(this::publish);
    return ServerResponse.ok().build();
  }

  private ServerResponse problem(final ProblemException exception, final ServerRequest request) {
    final ProblemFactory factory = problemFactory.getIfAvailable();
    if (factory == null) {
      return ServerResponse.status(exception.type().status()).build();
    }
    return ServerResponse.status(exception.type().status())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(factory.create(exception, request.servletRequest()));
  }

  private void publish(final SlackMessageReceivedEvent event) {
    executor.execute(
        () -> {
          try {
            publisher.publishEvent(event);
          } catch (final RuntimeException e) {
            LOG.error("A listener failed for Slack event {}", event.eventId(), e);
          }
        });
  }

  private static byte[] readBody(final ServerRequest request) throws IOException {
    final OptionalLong contentLength = request.headers().contentLength();
    if (contentLength.isPresent() && contentLength.getAsLong() > MAX_BODY_BYTES) {
      throw tooLarge();
    }
    final byte[] body = request.servletRequest().getInputStream().readNBytes(MAX_BODY_BYTES + 1);
    if (body.length > MAX_BODY_BYTES) {
      throw tooLarge();
    }
    return body;
  }

  private static boolean matches(final @Nullable String expected, final @Nullable String actual) {
    return expected == null || expected.equals(actual);
  }

  private static ProblemException rejected(final String reason) {
    LOG.warn("Rejected Slack event request: {}", reason);
    return new ProblemException(SlackEventsProblemType.SIGNATURE_INVALID);
  }

  private static ProblemException tooLarge() {
    LOG.warn("Rejected Slack event request: body larger than {} bytes", MAX_BODY_BYTES);
    return new ProblemException(SlackEventsProblemType.TOO_LARGE);
  }
}
