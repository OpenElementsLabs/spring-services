package com.example.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.mcp.McpProperties;
import com.openelements.spring.base.services.altcha.AltchaService;
import com.openelements.spring.base.services.dbbackup.DbBackupClient;
import com.openelements.spring.base.services.email.EmailService;
import com.openelements.spring.base.services.scim.ScimUserService;
import com.openelements.spring.base.services.search.MeilisearchClient;
import com.openelements.spring.base.services.slack.SlackService;
import com.openelements.spring.base.services.storage.ObjectStore;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserRepository;
import io.modelcontextprotocol.server.McpSyncServer;
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
 *       self-activated by classpath presence without any {@code @Import};
 *   <li>the storage module, which deliberately does <em>not</em> self-activate by classpath presence,
 *       registers no {@link ObjectStore}.
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

  @Test
  @DisplayName("The storage module registers no ObjectStore until a type is configured")
  void storageStaysInertWithoutAType() {
    assertThat(context.getBeanNamesForType(ObjectStore.class))
        .as("three implementations are on the classpath; none may be picked by classpath presence")
        .isEmpty();
  }

  @Test
  @DisplayName("Search, DB backup, MCP, ALTCHA and SCIM stay inactive without their enabling properties")
  void optInFeaturesStayInactiveByDefault() {
    assertThat(context.getBeanNamesForType(MeilisearchClient.class))
        .as("search requires openelements.meilisearch.enabled=true")
        .isEmpty();
    assertThat(context.getBeanNamesForType(DbBackupClient.class))
        .as("DB backup requires its enabling property")
        .isEmpty();
    assertThat(context.getBeanNamesForType(McpSyncServer.class))
        .as("MCP requires openelements.mcp.enabled=true")
        .isEmpty();
    assertThat(context.getBeanNamesForType(AltchaService.class))
        .as("ALTCHA requires openelements.altcha.enabled=true")
        .isEmpty();
    assertThat(context.getBeanNamesForType(ScimUserService.class))
        .as("SCIM requires openelements.scim.token")
        .isEmpty();
  }
}
