# Upgrade prompt: spring-services 2.0.0 — Spring Boot 4

> **Draft — 2.0.0 is unreleased.** `main` builds against `java-parent` 2.0.0-SNAPSHOT. The release
> switches to the released `java-parent` 2.0.0; nothing else in this document depends on that.

`spring-services` 2.0.0 is a **platform upgrade only** (spec 025): the whole library moves from Spring
Boot 3.5 to Spring Boot 4.1. There are no new library features and no library-motivated breaking
changes — every break below is one the platform forces.

| Change | Nature |
| --- | --- |
| Java 25, Spring Boot 4.1, `java-parent` 2.0.0 | **required** platform baseline |
| Boot 4 starter renames | build change in your POM |
| Jackson 3 (`tools.jackson.*`) in four public constructors / methods | **source break** if you call them |
| `JsonProcessingException` → `JacksonException` | source change if you catch it around library calls |
| MCP SDK 2.x, WebMVC transport from Spring AI | source break if you build MCP tools without `McpToolSupport` |
| **Webhook payload `timestamp` is an ISO-8601 string, no longer a number** | **breaking wire change for webhook receivers** |
| No database migration, no `openelements.*` property change | — |

> **Read the webhook section even if your code compiles unchanged.** Webhook receivers are third
> parties; they do not upgrade with you.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.

---

## Prompt

You are working inside a Spring Boot service that depends on `spring-services` 1.x and moves to
2.0.0.

### 1. Platform baseline

| | 1.x | 2.x |
| --- | --- | --- |
| Java | 21 | **25** |
| Spring Boot | 3.5 | **4.1** (Spring Framework 7.0, Spring Security 7.1, Hibernate ORM 7.4, Jackson 3.1, Jakarta EE 11) |
| `com.open-elements:java-parent` (if you use it) | 1.x | **2.0.0** |

Move the application itself to Spring Boot 4.1 and Java 25 first, following the
[Spring Boot 4.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
spring-services 2.x does not run on Boot 3.5, and 1.x does not run on Boot 4.

Then bump `spring-services-bom` / `spring-services-all` to `2.0.0`.

### 2. Starter renames

Boot 4 renamed or split starters. The library itself made these changes; your POM needs the same
ones wherever you declare them directly:

| Boot 3.5 | Boot 4.1 |
| --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |
| `org.springframework.security:spring-security-test` | `spring-boot-starter-security-test` |
| MockMvc / `@AutoConfigureMockMvc` (was in `spring-boot-starter-test`) | add `spring-boot-starter-webmvc-test` |
| `TestRestTemplate` (was in `spring-boot-starter-test`) | add `spring-boot-starter-restclient-test` |

Auto-configuration classes moved packages as well (for example
`org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration` →
`org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration`, `ErrorController` →
`org.springframework.boot.webmvc.error.ErrorController`). OpenRewrite's
`org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` handles most of them.

### 3. Jackson 3

Spring Boot 4 provides a Jackson 3 `tools.jackson.databind.json.JsonMapper`. Annotations stay in
`com.fasterxml.jackson.annotation` — `@JsonProperty`, `@JsonInclude` and friends need no change.

These public signatures changed 1:1 from `com.fasterxml.jackson.databind` to
`tools.jackson.databind`:

| Class | Signature in 2.0 |
| --- | --- |
| `MeilisearchClient` | `MeilisearchClient(MeilisearchProperties, tools.jackson.databind.ObjectMapper)`, `tools.jackson.databind.JsonNode multiSearch(Map<String, Object>)` |
| `McpToolSupport` | `McpToolSupport(McpPaging, tools.jackson.databind.ObjectMapper)` |
| `ProblemResponseWriter` | `ProblemResponseWriter(tools.jackson.databind.ObjectMapper)` |
| `ScimAuthenticationEntryPoint` | `ScimAuthenticationEntryPoint(tools.jackson.databind.ObjectMapper)` |

Only code that **constructs** these classes itself, or reads the `JsonNode` from `multiSearch`, is
affected. Beans created by auto-configuration keep receiving Boot's mapper, including your
`spring.jackson.*` customisations.

`JsonProcessingException` (checked) is `JacksonException` (unchecked) in Jackson 3. Library behaviour
on a serialisation failure is unchanged — the same log line, the same response. Adjust only your own
`catch` blocks.

On `JsonNode`, Jackson 3 renamed the text accessors: `asText()` → `asString()`, `isTextual()` →
`isString()`, `findValuesAsText()` → `findValuesAsString()`.

#### Jackson 2 in your application

spring-services 2.x has **no Jackson 2 at runtime**; the build enforces it for every module. Your
application may still see `com.fasterxml.jackson.core:jackson-databind` on its classpath if it uses
springdoc: springdoc 3.2.0 / swagger-core 2.2.55 (managed by `java-parent` 2.0.0) still depend on
Jackson 2. That is expected, not a spring-services leak, and harmless — Jackson 2 and 3 live in
different packages. A Jackson 2 ban like the library's own is therefore not possible in an
application that uses springdoc.

### 4. MCP (`spring-services-mcp`)

The module moved from MCP SDK 0.18 to **MCP SDK 2.1** (`io.modelcontextprotocol.sdk:mcp-core`,
`mcp-json-jackson3`). The Streamable HTTP transport for WebMVC moved out of the SDK into Spring AI:
`org.springframework.ai:mcp-spring-webmvc` 2.0.x, class
`org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider`. Both come
transitively with `spring-services-mcp`; remove any direct dependency on
`io.modelcontextprotocol.sdk:mcp-spring-webmvc` or `mcp-json-jackson2`.

For MCP clients nothing changes: same path (`/mcp`), same tools, names, input schemas and results,
same API-key security chain, same `openelements.mcp.*` properties.

For your code:

- **`McpToolProvider` implementations that use `McpToolSupport.spec(...)` and `McpTools`** need no
  change.
- Code that builds `SyncToolSpecification`s directly must use the 2.x API: the call handler receives
  a `McpSchema.CallToolRequest` (`request.arguments()`) instead of a raw argument map, and results are
  built with `McpSchema.CallToolResult.builder().addTextContent(...).isError(...).build()`.
- `McpSchema.Tool#inputSchema()` returns the schema as a `Map<String, Object>` (the JSON object it is
  sent as), no longer a `McpSchema.JsonSchema` record.

### 5. Webhook receivers — `timestamp` format

`WebhookSender` serialises payloads with Spring's default JSON converter, not with the application's
mapper. Under Jackson 2 that converter wrote `java.time.Instant` as a decimal number of epoch
seconds; under Jackson 3 it writes an ISO-8601 string. This affects **every `Instant` in a webhook
payload**: the `timestamp` member of data events and pings, and any `Instant` field of the DTO in
`data` (for example `createdAt` / `updatedAt` of a `UserDto`):

```jsonc
// 1.x
{"eventId":"…","eventType":"CREATED","dataClass":"…","data":{…},"timestamp":1767323045.123456000}
// 2.0
{"eventId":"…","eventType":"CREATED","dataClass":"…","data":{…},"timestamp":"2026-01-02T03:04:05.123456Z"}
```

No other member changed. The new format is the one the REST API has always used. Tell every webhook
receiver to accept ISO-8601 strings before you deploy 2.0.

### 6. Everything else is unchanged on the wire

Captured before and after the upgrade and compared: RFC 9457 problem details (401, 400 with
`errors[]`, 404, 409 with an extension member, 500), SCIM `User` / `ListResponse` / `Error`, and the
library's REST DTOs (users, API keys, webhooks, audit log, application info) are byte-for-byte
identical. As before, the order of entries in a problem's `errors[]` is not specified.

No test expectation of the library had to change because of a platform default.

### 7. Verify

- `./mvnw verify` — the application builds and its tests pass on Java 25.
- If you send webhooks: a receiver test with an ISO-8601 `timestamp`.
- If you have custom MCP tools: `tools/list` and `tools/call` against `/mcp`.
