package com.openelements.spring.base.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.server.McpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Activation-guard test for {@link McpAutoConfiguration}: proves the {@code @ConditionalOnClass}
 * guard keeps the feature inert when the MCP SDK is not on the classpath, so a bundle that ships the
 * auto-configuration class without the SDK causes no failure.
 */
@DisplayName("McpAutoConfiguration — activation guard")
class McpAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class));

  @Test
  @DisplayName("Stays inert (no MCP beans) when the MCP SDK type McpServer is absent")
  void inertWhenSdkAbsent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(McpServer.class))
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(McpAutoConfiguration.class)
                    .doesNotHaveBean(McpProperties.class));
  }
}
