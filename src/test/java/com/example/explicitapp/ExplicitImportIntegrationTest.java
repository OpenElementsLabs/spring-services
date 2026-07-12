package com.example.explicitapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserRepository;
import com.openelements.spring.base.services.user.UserService;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the "explicit import still works" behavior: with the starter auto-configuration excluded
 * and the library wired purely via {@code @Import(FullSpringServiceConfig.class)} +
 * {@code @EntityScan}/{@code @EnableJpaRepositories} (see {@link ExplicitImportTestApp}), the
 * context starts, the library's beans and repositories resolve against the dedicated schema, and no
 * duplicate-bean error occurs.
 */
@SpringBootTest(classes = ExplicitImportTestApp.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
class ExplicitImportIntegrationTest {

  @Autowired private UserRepository userRepository;

  @Autowired private UserService userService;

  @Test
  @DisplayName("Explicit @Import(FullSpringServiceConfig) wires the library with no duplicate beans")
  void explicitImportWiresLibrary() {
    assertThat(userService).isNotNull();
    assertThat(userRepository).isNotNull();
    // The library's schema-qualified table is live and the System User bootstrap ran on startup.
    assertThat(userRepository.findById(SystemUser.ID)).isPresent();
  }
}
