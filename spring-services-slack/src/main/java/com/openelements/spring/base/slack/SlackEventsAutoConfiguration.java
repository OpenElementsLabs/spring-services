package com.openelements.spring.base.slack;

import com.openelements.spring.base.SpringServicesCoreAutoConfiguration;
import com.openelements.spring.base.problem.ProblemFactory;
import com.openelements.spring.base.services.slack.events.SlackEventDeduplicator;
import com.openelements.spring.base.services.slack.events.SlackEventsEndpoint;
import com.openelements.spring.base.services.slack.events.SlackEventsProperties;
import com.openelements.spring.base.services.slack.events.SlackSignatureVerifier;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Auto-configuration for receiving Slack messages through the Slack Events API.
 *
 * <p>Gated on {@code open-elements.slack.events.enabled=true} rather than on classpath presence:
 * the feature opens a public path, which must be a deliberate decision of the application. Without
 * a signing secret the application still starts, a warning is logged, and the endpoint rejects
 * every request.
 *
 * <p>The beans live here rather than in {@code SlackConfig}, whose component scan covers the {@code
 * services.slack} packages and would bypass the property gate. Each bean is guarded by {@link
 * ConditionalOnMissingBean}: an application replaces the clock or the executor by declaring a bean
 * of the same name, the endpoint or the deduplicator by declaring a bean of the same type.
 *
 * <p>The clock and the executor are no default candidates for autowiring. They are injected here by
 * qualifier only, so an application that injects a {@link Clock} or an {@link Executor} by type keeps
 * getting its own bean and does not fail with two candidates once this feature is enabled.
 *
 * <p>Runs after {@link TaskExecutionAutoConfiguration}, because that one backs off as soon as any
 * {@link Executor} bean exists; the executor of this feature must not take the place of the
 * application's task executor. Runs after the security auto-configurations, so that the filter
 * chain can see whether there is an {@link HttpSecurity} to build it with.
 */
@AutoConfiguration(
    after = {
      SlackAutoConfiguration.class,
      SpringServicesCoreAutoConfiguration.class,
      ServletWebSecurityAutoConfiguration.class,
      TaskExecutionAutoConfiguration.class
    })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "open-elements.slack.events", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SlackEventsProperties.class)
public class SlackEventsAutoConfiguration {

  /** Bean name of the clock that signatures and de-duplication are checked against. */
  public static final String CLOCK_BEAN_NAME = "slackEventsClock";

  /** Bean name of the executor that runs the listeners. */
  public static final String EXECUTOR_BEAN_NAME = "slackEventsTaskExecutor";

  private static final Logger LOG = LoggerFactory.getLogger(SlackEventsAutoConfiguration.class);

  private static final Duration DEDUPLICATION_RETENTION = Duration.ofHours(1);

  /** Creates the auto-configuration. */
  public SlackEventsAutoConfiguration() {}

  /**
   * The clock for signature timestamps and de-duplication. A bean of its own name and no default
   * candidate, so that it never competes with a {@link Clock} of the application.
   *
   * @return the system clock in UTC
   */
  @Bean(name = CLOCK_BEAN_NAME, defaultCandidate = false)
  @ConditionalOnMissingBean(name = CLOCK_BEAN_NAME)
  public Clock slackEventsClock() {
    return Clock.systemUTC();
  }

  /**
   * Runs the listeners, one virtual thread per event.
   *
   * @return the executor
   */
  @Bean(name = EXECUTOR_BEAN_NAME, defaultCandidate = false)
  @ConditionalOnMissingBean(name = EXECUTOR_BEAN_NAME)
  public SimpleAsyncTaskExecutor slackEventsTaskExecutor() {
    final SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("slack-events-");
    executor.setVirtualThreads(true);
    return executor;
  }

  /**
   * Remembers processed event ids for one hour; correct for a single instance only.
   *
   * @param clock the clock of this feature
   * @return the deduplicator
   */
  @Bean
  @ConditionalOnMissingBean
  public SlackEventDeduplicator slackEventDeduplicator(
      @Qualifier(CLOCK_BEAN_NAME) final Clock clock) {
    return new SlackEventDeduplicator(clock, DEDUPLICATION_RETENTION);
  }

  /**
   * The endpoint. Without a signing secret it is still registered, but rejects every request.
   *
   * @param properties the configuration
   * @param clock the clock of this feature
   * @param deduplicator drops Slack's retries
   * @param publisher publishes the events
   * @param executor runs the listeners
   * @param problemFactory builds the problem for a rejection; absent while problem details are
   *     disabled
   * @return the endpoint
   */
  @Bean
  @ConditionalOnMissingBean
  public SlackEventsEndpoint slackEventsEndpoint(
      final SlackEventsProperties properties,
      @Qualifier(CLOCK_BEAN_NAME) final Clock clock,
      final SlackEventDeduplicator deduplicator,
      final ApplicationEventPublisher publisher,
      @Qualifier(EXECUTOR_BEAN_NAME) final Executor executor,
      final ObjectProvider<ProblemFactory> problemFactory) {
    final String secret = properties.signingSecret();
    final SlackSignatureVerifier verifier;
    if (secret == null || secret.isBlank()) {
      LOG.warn(
          "Slack events are enabled, but no signing secret is configured. The endpoint rejects "
              + "every request. Set property 'open-elements.slack.events.signing-secret'.");
      verifier = null;
    } else {
      verifier = new SlackSignatureVerifier(secret, clock);
    }
    return new SlackEventsEndpoint(
        verifier, properties, deduplicator, publisher, executor, problemFactory);
  }

  /**
   * Routes {@code POST} on the configured path to the endpoint.
   *
   * @param properties the configuration
   * @param endpoint the endpoint
   * @return the route
   */
  @Bean
  @ConditionalOnMissingBean(name = "slackEventsRouterFunction")
  public RouterFunction<ServerResponse> slackEventsRouterFunction(
      final SlackEventsProperties properties, final SlackEventsEndpoint endpoint) {
    return RouterFunctions.route().POST(properties.path(), endpoint::handle).build();
  }

  /**
   * Permits anonymous {@code POST} on the events path and nothing else on it. Ordered ahead of the
   * default JWT chain, which has no matcher and would otherwise demand a token from Slack. The
   * request is authenticated by its signature in the endpoint. The chain matches only this one path,
   * so it cannot open anything else.
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
  @ConditionalOnMissingBean(name = "slackEventsSecurityFilterChain")
  public SecurityFilterChain slackEventsSecurityFilterChain(
      final HttpSecurity http, final SlackEventsProperties properties) throws Exception {
    http.securityMatcher(properties.path())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, properties.path())
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable());
    return http.build();
  }
}
