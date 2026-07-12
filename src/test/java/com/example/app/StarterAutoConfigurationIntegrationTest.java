package com.example.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserRepository;
import com.openelements.spring.base.services.user.UserService;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Binding acceptance gate for the Spring Boot starter (spec 013).
 *
 * <p>Boots {@link StarterTestApp} — a consumer application in {@code com.example.app} that declares
 * <strong>no</strong> {@code @Import}, {@code @EntityScan}, or {@code @EnableJpaRepositories} — with
 * only the library on the classpath. It then asserts that <em>both</em> the application's own
 * {@link OrderRepository}/{@link Order} <em>and</em> the library's {@link UserRepository} and feature
 * beans resolve under one persistence unit.
 *
 * <p>This is the proof that {@code AutoConfigurationPackages.register} plus
 * {@code @AutoConfigureBefore(HibernateJpaAutoConfiguration, JpaRepositoriesAutoConfiguration)}
 * actually win the auto-configuration ordering — the additive scan is not assumed to work, it is
 * demonstrated. The test fails if <em>either</em> side is missing.
 */
@SpringBootTest(classes = StarterTestApp.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
class StarterAutoConfigurationIntegrationTest {

  @Autowired private OrderRepository orderRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private UserService userService;

  @Test
  @DisplayName("The application's own entity and repository are discovered and usable (default scan intact)")
  void applicationOwnPersistenceResolves() {
    final Order order = new Order();
    order.setDescription("first order");

    final Order saved = orderRepository.save(order);

    assertThat(saved.getId()).isNotNull();
    assertThat(orderRepository.findById(saved.getId()))
        .isPresent()
        .get()
        .extracting(Order::getDescription)
        .isEqualTo("first order");
  }

  @Test
  @DisplayName("The library's repositories and feature beans are wired by the starter alone (no @Import)")
  void libraryPersistenceAndBeansResolve() {
    // The library repository is injectable...
    assertThat(userRepository).isNotNull();
    // ...a library feature bean is present...
    assertThat(userService).isNotNull();
    // ...and the schema-qualified library table is live: SystemUserInitializer ran on startup and
    // inserted the System User row into oe_spring_services.users.
    assertThat(userRepository.findById(SystemUser.ID))
        .as("System User row must exist in the dedicated schema after startup")
        .isPresent();
  }

  @Test
  @DisplayName("Application and library entities coexist under one persistence unit")
  void bothEntitiesCoexist() {
    final UUID orderId = orderRepository.save(new Order()).getId();

    assertThat(orderRepository.existsById(orderId)).isTrue();
    assertThat(userRepository.existsById(SystemUser.ID)).isTrue();
  }
}
