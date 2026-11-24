package com.openelements.spring.base.services.slack.authorization;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for editing Slack messages of users, bound from the {@code
 * open-elements.slack.user-authorization.*} namespace.
 *
 * <p>{@link #enabled()} defaults to {@code false}: the feature opens public paths and stores
 * credentials, which must be a deliberate decision of the application. Without the remaining values
 * the application still starts, but the feature refuses to work; {@link #missingProperties()} names
 * what is missing.
 *
 * <p>{@link #toString()} masks the client secret and the encryption key, so that logging the
 * properties never leaks them.
 *
 * @param enabled whether the feature is active
 * @param clientId the client id of the Slack app
 * @param clientSecret the client secret of the Slack app; supply it through the environment or
 *     secret management, never commit it
 * @param publicBaseUrl the URL under which the application is reachable from a browser, for example
 *     {@code https://app.example.com}; a trailing slash is removed
 * @param path the path below which the authorization endpoints are served
 * @param tokenEncryptionKey the Base64 encoded 32-byte AES key that encrypts stored user tokens;
 *     supply it through the environment or secret management, never commit it
 */
@ConfigurationProperties(prefix = SlackUserAuthorizationProperties.PREFIX)
public record SlackUserAuthorizationProperties(
    @DefaultValue("false") boolean enabled,
    @Nullable String clientId,
    @Nullable String clientSecret,
    @Nullable String publicBaseUrl,
    @DefaultValue(SlackUserAuthorizationProperties.DEFAULT_PATH) String path,
    @Nullable String tokenEncryptionKey) {

  /** The prefix of every property of this feature. */
  public static final String PREFIX = "open-elements.slack.user-authorization";

  /** The path of the endpoints when {@code open-elements.slack.user-authorization.path} is unset. */
  public static final String DEFAULT_PATH = "/slack/oauth";

  /** The length of the encryption key in bytes. */
  public static final int KEY_LENGTH = 32;

  private static final String MASK = "****";

  /** Treats blank values as unset and removes a trailing slash from the public base URL. */
  public SlackUserAuthorizationProperties {
    clientId = blankToNull(clientId);
    clientSecret = blankToNull(clientSecret);
    publicBaseUrl = blankToNull(publicBaseUrl);
    if (publicBaseUrl != null && publicBaseUrl.endsWith("/")) {
      publicBaseUrl = publicBaseUrl.substring(0, publicBaseUrl.length() - 1);
    }
    tokenEncryptionKey = blankToNull(tokenEncryptionKey);
  }

  /**
   * Returns the names of the properties that are missing or invalid. Empty means the feature is
   * fully configured.
   *
   * @return the property names, with a hint for an invalid encryption key
   */
  public List<String> missingProperties() {
    final List<String> missing = new ArrayList<>();
    if (clientId == null) {
      missing.add(PREFIX + ".client-id");
    }
    if (clientSecret == null) {
      missing.add(PREFIX + ".client-secret");
    }
    if (publicBaseUrl == null) {
      missing.add(PREFIX + ".public-base-url");
    }
    if (tokenEncryptionKey == null) {
      missing.add(PREFIX + ".token-encryption-key");
    } else if (decodedKey().isEmpty()) {
      missing.add(
          PREFIX + ".token-encryption-key (invalid: must be Base64 of " + KEY_LENGTH + " bytes)");
    }
    return List.copyOf(missing);
  }

  /**
   * Returns the decoded encryption key.
   *
   * @return the key, or empty if it is unset, not Base64 or not {@value #KEY_LENGTH} bytes long
   */
  public Optional<byte[]> decodedKey() {
    if (tokenEncryptionKey == null) {
      return Optional.empty();
    }
    try {
      final byte[] key = Base64.getDecoder().decode(tokenEncryptionKey);
      return key.length == KEY_LENGTH ? Optional.of(key) : Optional.empty();
    } catch (final IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * Returns the path of the endpoint that starts the authorization.
   *
   * @return the path
   */
  public String authorizePath() {
    return path + "/authorize";
  }

  /**
   * Returns the path of the endpoint Slack redirects back to.
   *
   * @return the path
   */
  public String callbackPath() {
    return path + "/callback";
  }

  /**
   * Returns the link a user opens to authorize the application.
   *
   * @return the absolute URL, or {@code null} without a public base URL
   */
  public @Nullable String authorizationUrl() {
    return publicBaseUrl == null ? null : publicBaseUrl + authorizePath();
  }

  /**
   * Returns the redirect URI that must be registered in the Slack app.
   *
   * @return the absolute URL, or {@code null} without a public base URL
   */
  public @Nullable String redirectUri() {
    return publicBaseUrl == null ? null : publicBaseUrl + callbackPath();
  }

  @Override
  public String toString() {
    return "SlackUserAuthorizationProperties[enabled=%s, clientId=%s, clientSecret=%s, publicBaseUrl=%s, path=%s, tokenEncryptionKey=%s]"
        .formatted(
            enabled,
            clientId,
            clientSecret == null ? null : MASK,
            publicBaseUrl,
            path,
            tokenEncryptionKey == null ? null : MASK);
  }

  private static @Nullable String blankToNull(final @Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
