package com.example.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.mcp.McpProperties;
import com.openelements.spring.base.services.email.EmailService;
import com.openelements.spring.base.services.slack.SlackService;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Aggregate / zero-config starter integration test (spec 013 gate 1, relocated here per spec 014).
 *
 * <p>Boots {@link AggregateApp} — a zero-config consumer with the full reactor
 * ({@code spring-services-all}) on the classpath — and asserts that:
 *
 * <ul>
 *   <li>the application's own {@link Order}/{@link OrderRepository} resolve (default entity scan
 *       intact);
 *   <li>the core library persistence resolves ({@link UserRepository}, System User bootstrapped);
 *   <li>representative beans from multiple optional feature modules are present — {@link SlackService}
 *       (slack), {@link EmailService} (email), and {@link McpProperties} (mcp) — proving every module
 *       self-activated by classpath presence without any {@code @Import}.
 * </ul>
 */
@SpringBootTest(classes = AggregateApp.class)
@Import(AggregateTestConfig.class)
@Testcontainers
@ActiveProfiles("testcontainers")
class AggregateStarterIntegrationTest {

  @Autowired private ApplicationContext context;

  @Autowired private OrderRepository orderRepository;

  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("Application and core-library persistence both resolve under the full classpath")
  void applicationAndCorePersistenceResolve() {
    final Order order = new Order();
    order.setDescription("aggregate order");
    final Order saved = orderRepository.save(order);

    assertThat(orderRepository.findById(saved.getId())).isPresent();
    assertThat(userRepository.findById(SystemUser.ID))
        .as("System User must be bootstrapped in the dedicated schema")
        .isPresent();
  }

  @Test
  @DisplayName("Representative beans from the slack, email and mcp feature modules are all present")
  void optionalFeatureModulesSelfActivate() {
    assertThat(context.getBeanNamesForType(SlackService.class))
        .as("slack module must self-activate")
        .isNotEmpty();
    assertThat(context.getBeanNamesForType(EmailService.class))
        .as("email module must self-activate")
        .isNotEmpty();
    assertThat(context.getBeanNamesForType(McpProperties.class))
        .as("mcp module must self-activate (properties bound unconditionally)")
        .isNotEmpty();
  }
}
