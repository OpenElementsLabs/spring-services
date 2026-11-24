package com.openelements.spring.base.slack;

import com.openelements.spring.base.problem.ProblemFactory;
import com.openelements.spring.base.services.slack.authorization.JdbcSlackUserTokenStore;
import com.openelements.spring.base.services.slack.authorization.SlackTokenCipher;
import com.openelements.spring.base.services.slack.authorization.SlackUserAuthorizationEndpoint;
import com.openelements.spring.base.services.slack.authorization.SlackUserAuthorizationProperties;
import com.openelements.spring.base.services.slack.authorization.SlackUserTokenStore;
import com.slack.api.Slack;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Auto-configuration for editing Slack messages of users: the OAuth endpoints through which a user
 * allows it, and the store for the resulting user tokens.
 *
 * <p>Gated on {@code open-elements.slack.user-authorization.enabled=true} rather than on classpath
 * presence: the feature opens public paths and stores credentials, which must be a deliberate
 * decision of the application. Without the required properties the application still starts, a
 * warning names what is missing, and the feature refuses to work.
 *
 * <p>The beans live here rather than in {@code SlackConfig}, whose component scan covers the {@code
 * services.slack} packages and would bypass the property gate. An application replaces the token
 * store by declaring a {@link SlackUserTokenStore} bean; the default needs a {@link JdbcClient}.
 */
@AutoConfiguration(
    after = {SlackAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = SlackUserAuthorizationProperties.PREFIX,
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties(SlackUserAuthorizationProperties.class)
public class SlackUserAuthorizationAutoConfiguration {

  /** The property that restricts the feature to one Slack workspace, shared with spec 026. */
  static final String TEAM_ID_PROPERTY = "open-elements.slack.events.team-id";

  private static final Logger LOG =
      LoggerFactory.getLogger(SlackUserAuthorizationAutoConfiguration.class);

  /** Creates the auto-configuration. */
  public SlackUserAuthorizationAutoConfiguration() {}

  /**
   * Stores the user tokens encrypted in the library schema. Not registered while the encryption key
   * is missing or invalid.
   *
   * @param properties the configuration
   * @param jdbc the database access; absent without a {@code DataSource}
   * @return the store, or {@code null} without a valid encryption key
   * @throws IllegalStateException if the key is valid but there is no database
   */
  @Bean
  @ConditionalOnMissingBean(SlackUserTokenStore.class)
  public @Nullable SlackUserTokenStore slackUserTokenStore(
      final SlackUserAuthorizationProperties properties, final ObjectProvider<JdbcClient> jdbc) {
    final Optional<byte[]> key = properties.decodedKey();
    if (key.isEmpty()) {
      return null;
    }
    final JdbcClient client = jdbc.getIfAvailable();
    if (client == null) {
      throw new IllegalStateException(
          "Editing Slack messages of users needs a place to store user tokens: provide a"
              + " DataSource for the default JdbcSlackUserTokenStore, or declare a"
              + " SlackUserTokenStore bean");
    }
    return new JdbcSlackUserTokenStore(client, new SlackTokenCipher(key.get()), Clock.systemUTC());
  }

  /**
   * The endpoints. Without every required property they are still registered, but answer {@code
   * 503}.
   *
   * @param properties the configuration
   * @param store the token store; absent without a valid encryption key
   * @param slack the Slack SDK
   * @param environment provides the optional workspace restriction
   * @param problemFactory builds the problem for a rejection; absent while problem details are
   *     disabled
   * @return the endpoint
   */
  @Bean
  @ConditionalOnMissingBean
  public SlackUserAuthorizationEndpoint slackUserAuthorizationEndpoint(
      final SlackUserAuthorizationProperties properties,
      final ObjectProvider<SlackUserTokenStore> store,
      final Slack slack,
      final Environment environment,
      final ObjectProvider<ProblemFactory> problemFactory) {
    final List<String> missing = properties.missingProperties();
    if (!missing.isEmpty()) {
      LOG.warn(
          "Editing Slack messages of users is enabled, but not configured. Every authorization"
              + " request is rejected and SlackService.updateUserMessage fails. Missing: {}",
          String.join(", ", missing));
    }
    final String teamId = environment.getProperty(TEAM_ID_PROPERTY);
    return new SlackUserAuthorizationEndpoint(
        properties,
        store.getIfAvailable(),
        slack,
        teamId == null || teamId.isBlank() ? null : teamId,
        problemFactory);
  }

  /**
   * Routes {@code GET} on the two endpoint paths to the endpoint.
   *
   * @param properties the configuration
   * @param endpoint the endpoint
   * @return the routes
   */
  @Bean
  @ConditionalOnMissingBean(name = "slackUserAuthorizationRouterFunction")
  public RouterFunction<ServerResponse> slackUserAuthorizationRouterFunction(
      final SlackUserAuthorizationProperties properties,
      final SlackUserAuthorizationEndpoint endpoint) {
    return RouterFunctions.route()
        .GET(properties.authorizePath(), endpoint::authorize)
        .GET(properties.callbackPath(), endpoint::callback)
        .build();
  }

  /**
   * Permits anonymous {@code GET} on the two endpoint paths and nothing else on them. Ordered ahead
   * of the default JWT chain, which has no matcher and would otherwise demand a token from a user
   * who only has a Slack account. The callback protects itself with the state cookie. The chain
   * matches only these two paths, so it cannot open anything else.
   *
   * @param http the security builder
   * @param properties the configuration
   * @return the filter chain
   * @throws Exception if the chain cannot be built
   */
  @Bean
  @Order(0)
  @ConditionalOnClass(HttpSecurity.class)
  @ConditionalOnBean(HttpSecurity.class)
  @ConditionalOnMissingBean(name = "slackUserAuthorizationSecurityFilterChain")
  public SecurityFilterChain slackUserAuthorizationSecurityFilterChain(
      final HttpSecurity http, final SlackUserAuthorizationProperties properties)
      throws Exception {
    http.securityMatcher(properties.authorizePath(), properties.callbackPath())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.GET, properties.authorizePath(), properties.callbackPath())
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable());
    return http.build();
  }
}
