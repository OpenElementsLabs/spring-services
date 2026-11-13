package com.openelements.spring.base.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
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

  @Test
  @DisplayName("Registers no server, transport or security chain while openelements.mcp.enabled is unset")
  void inertWhenNotEnabled() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .run(
        context ->
            assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(McpSyncServer.class)
                .doesNotHaveBean(WebMvcStreamableServerTransportProvider.class)
                .doesNotHaveBean(McpServerConfig.class)
                .doesNotHaveBean(McpSecurityConfig.class)
                .doesNotHaveBean("mcpApiKeyFilterChain"));
  }

  @Test
  @DisplayName("McpToolSupport serialises tool payloads with Boot's customised JSON mapper")
  void toolSupportUsesBootsMapper() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withPropertyValues("spring.jackson.serialization.indent-output=true")
        .run(
            context -> {
              final McpToolSupport support = context.getBean(McpToolSupport.class);
              final McpSchema.Tool tool = McpTools.tool("demo", "Demo.", Map.of(), List.of());
              final McpSchema.CallToolResult result =
                  support
                      .spec(tool, args -> Map.of("a", 1))
                      .callHandler()
                      .apply(mock(McpSyncServerExchange.class), new McpSchema.CallToolRequest("demo", Map.of()));
              assertThat(((McpSchema.TextContent) result.content().get(0)).text())
                  .as("indent-output is set on Boot's mapper only")
                  .contains("\n");
            });
  }
}
