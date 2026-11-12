package com.openelements.spring.base.mcp;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openelements.spring.base.problem.ProblemDetailsAutoConfiguration;
import com.openelements.spring.base.security.SecurityConfig;
import com.openelements.spring.base.services.apikey.ApiKeyDataService;
import com.openelements.spring.base.trace.TraceAutoConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The MCP filter chain's {@code 401}.
 *
 * <h2>What is tested</h2>
 *
 * <p>That {@code /mcp} answers an unauthenticated request with the same RFC 9457 problem as the rest
 * of the platform (spec 024). The MCP chain injects the shared {@code apiKeyAuthenticationEntryPoint}
 * bean by qualifier, so this is really a test that the wiring still resolves to the problem entry
 * point after spec 024 changed that bean's type from {@code JsonAuthenticationEntryPoint} to
 * {@code AuthenticationEntryPoint} — a change that compiles either way and would otherwise only show
 * up as an MCP client receiving a body it does not recognise.
 *
 * <h2>How it is tested</h2>
 *
 * <p>A {@link WebApplicationContextRunner} with the library's {@link SecurityConfig} beans that this
 * chain depends on, plus the problem-details and trace auto-configurations. The request never reaches
 * a handler, so no MCP server is needed.
 *
 * <p><b>Mock-Audit.</b> One mock: {@link ApiKeyDataService}, which would otherwise pull in a JPA
 * repository and a datasource. It is stubbed to find no key, which is exactly the path under test —
 * the entry point's. No other collaborator is mocked; the filter chain, the entry point and the
 * problem factory are the production objects.
 */
@DisplayName("MCP security chain over HTTP")
class McpSecurityChainWebTest {

  private static final String BASE = "https://open-elements.com/problems/spring-services/";

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ProblemDetailsAutoConfiguration.class,
                  TraceAutoConfiguration.class,
                  JacksonAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  SecurityAutoConfiguration.class,
                  WebMvcAutoConfiguration.class))
          .withUserConfiguration(EntryPoints.class, McpSecurityConfig.class)
          .withPropertyValues("openelements.mcp.enabled=true");

  @Test
  @DisplayName("An unauthenticated /mcp request is a 401 problem with the ApiKey challenge")
  void unauthenticatedMcpRequestIsAProblem() {
    runner.run(
        context -> {
          final MockMvc mvc =
              MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                  .apply(springSecurity())
                  .build();

          mvc.perform(post("/mcp"))
              .andExpect(status().isUnauthorized())
              .andExpect(content().contentType("application/problem+json"))
              .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "ApiKey realm=\"external\""))
              .andExpect(jsonPath("$.type").value(BASE + "unauthorized"))
              .andExpect(jsonPath("$.title").value("Unauthorized"))
              .andExpect(jsonPath("$.status").value(401))
              .andExpect(jsonPath("$.error").doesNotExist());
        });
  }

  @Test
  @DisplayName("An invalid API key on /mcp is a 401 problem, not an empty body")
  void invalidApiKeyOnMcpIsAProblem() {
    runner.run(
        context -> {
          final MockMvc mvc =
              MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                  .apply(springSecurity())
                  .build();

          mvc.perform(post("/mcp").header("X-API-Key", "not-a-valid-key"))
              .andExpect(status().isUnauthorized())
              .andExpect(content().contentType("application/problem+json"))
              .andExpect(jsonPath("$.type").value(BASE + "unauthorized"))
              .andExpect(jsonPath("$.detail").value("Invalid API key"));
        });
  }

  /**
   * Contributes the two entry-point beans {@link SecurityConfig} declares, plus the key store the
   * MCP chain needs. Importing {@code SecurityConfig} itself would drag in the whole JPA-backed
   * platform; the chain under test depends only on the qualified entry-point bean.
   */
  @Configuration(proxyBeanMethods = false)
  static class EntryPoints {

    @Bean
    ApiKeyDataService apiKeyDataService() {
      final ApiKeyDataService service = mock(ApiKeyDataService.class);
      when(service.authenticate(anyString())).thenReturn(Optional.empty());
      return service;
    }

    @Bean
    org.springframework.security.web.AuthenticationEntryPoint apiKeyAuthenticationEntryPoint(
        final com.openelements.spring.base.problem.ProblemFactory problemFactory,
        final com.openelements.spring.base.problem.ProblemResponseWriter responseWriter) {
      return new com.openelements.spring.base.problem.ProblemAuthenticationEntryPoint(
          problemFactory, responseWriter, "ApiKey realm=\"external\"");
    }
  }
}
