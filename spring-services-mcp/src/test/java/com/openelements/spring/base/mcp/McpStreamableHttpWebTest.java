package com.openelements.spring.base.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.openelements.spring.base.services.apikey.ApiKeyDataService;
import com.openelements.spring.base.services.apikey.ApiKeyEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The MCP endpoint over Streamable HTTP.
 *
 * <h2>What is tested</h2>
 *
 * <p>That an MCP client can talk to {@code /mcp}: {@code initialize} opens a session, {@code
 * tools/list} returns the contributed tools with their input schemas, and {@code tools/call} runs a
 * tool and returns its serialized payload. This is the contract the MCP SDK 2.x / Spring AI
 * transport migration (spec 025) must keep; the unit tests of {@link McpToolSupport} cannot see the
 * transport.
 *
 * <h2>How it is tested</h2>
 *
 * <p>A {@link WebApplicationContextRunner} with the real {@link McpServerConfig}, {@link
 * McpToolSupport} and Jackson / WebMVC auto-configurations, driven through {@link MockMvc} as a
 * client would — JSON-RPC bodies, the {@code Mcp-Session-Id} header, async dispatch for the SSE
 * answers. The security chain is not part of this context; it is covered by {@code
 * McpSecurityChainWebTest}.
 *
 * <p><b>Mock-Audit.</b> One mock: {@link ApiKeyDataService}, which would otherwise pull in a JPA
 * repository and a datasource. The transport only asks it to resolve the actor label for the
 * transport context. Everything else is production code.
 */
@DisplayName("MCP over Streamable HTTP")
class McpStreamableHttpWebTest {

  private static final String SESSION_HEADER = "Mcp-Session-Id";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  WebMvcAutoConfiguration.class))
          .withUserConfiguration(McpServerUnderTest.class)
          .withPropertyValues(
              "openelements.mcp.enabled=true", "openelements.mcp.server-name=Test MCP");

  @Test
  @DisplayName("initialize and tools/list return the contributed tools with their input schemas")
  void listsTools() {
    runner.run(
        context -> {
          final MockMvc mvc = mvc((WebApplicationContext) context);
          final String session = initialize(mvc);

          final JsonNode result = rpc(mvc, session, 2, "tools/list", Map.of()).path("result");

          final JsonNode tools = result.path("tools");
          assertThat(tools).hasSize(1);
          final JsonNode tool = tools.get(0);
          assertThat(tool.path("name").asString()).isEqualTo("echo");
          assertThat(tool.path("description").asString()).isEqualTo("Echoes its text.");
          final JsonNode schema = tool.path("inputSchema");
          assertThat(schema.path("type").asString()).isEqualTo("object");
          assertThat(schema.path("properties").path("text").path("type").asString())
              .isEqualTo("string");
          assertThat(schema.path("required").get(0).asString()).isEqualTo("text");
          assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        });
  }

  @Test
  @DisplayName("tools/call returns the tool's serialized payload as text content")
  void callsTool() {
    runner.run(
        context -> {
          final MockMvc mvc = mvc((WebApplicationContext) context);
          final String session = initialize(mvc);

          final JsonNode result =
              rpc(
                      mvc,
                      session,
                      3,
                      "tools/call",
                      Map.of("name", "echo", "arguments", Map.of("text", "hello")))
                  .path("result");

          assertThat(result.path("isError").asBoolean(true)).isFalse();
          assertThat(result.path("content").get(0).path("type").asString()).isEqualTo("text");
          assertThat(result.path("content").get(0).path("text").asString())
              .isEqualTo("{\"echo\":\"hello\"}");
        });
  }

  private static MockMvc mvc(final WebApplicationContext context) {
    return MockMvcBuilders.webAppContextSetup(context).build();
  }

  /** Runs the MCP handshake and returns the session id the server assigned. */
  private static String initialize(final MockMvc mvc) throws Exception {
    final MvcResult result =
        complete(
            mvc,
            mcpPost(null)
                .content(
                    request(
                        1,
                        "initialize",
                        Map.of(
                            "protocolVersion", "2025-06-18",
                            "capabilities", Map.of(),
                            "clientInfo", Map.of("name", "test-client", "version", "1.0")))));
    final String session = result.getResponse().getHeader(SESSION_HEADER);
    assertThat(session).as("initialize must open a session").isNotBlank();
    assertThat(parse(result).path("result").path("serverInfo").path("name").asString())
        .isEqualTo("Test MCP");

    mvc.perform(
            mcpPost(session)
                .content(
                    JSON.writeValueAsString(
                        Map.of("jsonrpc", "2.0", "method", "notifications/initialized"))))
        .andReturn();
    return session;
  }

  private static JsonNode rpc(
      final MockMvc mvc,
      final String session,
      final int id,
      final String method,
      final Map<String, Object> params)
      throws Exception {
    return parse(complete(mvc, mcpPost(session).content(request(id, method, params))));
  }

  private static MockHttpServletRequestBuilder mcpPost(final String session) {
    final MockHttpServletRequestBuilder builder =
        post(McpServerConfig.MCP_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM);
    return session == null ? builder : builder.header(SESSION_HEADER, session);
  }

  private static String request(
      final int id, final String method, final Map<String, Object> params) {
    return JSON.writeValueAsString(
        Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params));
  }

  /** Performs the request and follows an async (SSE) answer to its end. */
  private static MvcResult complete(final MockMvc mvc, final MockHttpServletRequestBuilder request)
      throws Exception {
    final MvcResult result = mvc.perform(request).andReturn();
    return result.getRequest().isAsyncStarted()
        ? mvc.perform(asyncDispatch(result)).andReturn()
        : result;
  }

  /** Reads the JSON-RPC message from a plain JSON or an SSE response body. */
  private static JsonNode parse(final MvcResult result) throws Exception {
    final String body = result.getResponse().getContentAsString();
    final String json =
        body.lines()
            .filter(line -> line.startsWith("data:"))
            .map(line -> line.substring("data:".length()).trim())
            .reduce((first, last) -> last)
            .orElse(body);
    return JSON.readTree(json);
  }

  /** The MCP server configuration plus one tool and the key store the transport consults. */
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(McpProperties.class)
  @Import({McpPaging.class, McpToolSupport.class, McpServerConfig.class})
  static class McpServerUnderTest {

    @Bean
    ApiKeyDataService apiKeyDataService() {
      final ApiKeyEntity key = new ApiKeyEntity();
      key.setName("ci");
      final ApiKeyDataService service = mock(ApiKeyDataService.class);
      when(service.authenticate(anyString())).thenReturn(Optional.of(key));
      return service;
    }

    @Bean
    McpToolProvider echoTool(final McpToolSupport support) {
      return () ->
          List.of(
              support.spec(
                  McpTools.tool(
                      "echo",
                      "Echoes its text.",
                      Map.of("text", McpTools.prop("string", "the text")),
                      List.of("text")),
                  args -> Map.of("echo", McpTools.requiredString(args, "text"))));
    }
  }
}
