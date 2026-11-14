# Upgrade prompt: spring-services 1.4.0 → 2.0.0

`spring-services` 2.0.0 is the release after 1.4.0. There was **no 1.5.0 release**: the work planned
for 1.5.0 is part of 2.0.0. The upgrade therefore has two parts:

- **Part 1 — Spring Boot 4** (spec 025). The whole library moves from Spring Boot 3.5 to Spring Boot
  4.1. This part is **required** for every consumer: 2.0.0 does not run on Boot 3.5.
- **Part 2 — library changes since 1.4.0** (spec 022, 023, 024 and the object store). Each section
  stands on its own; two of them affect every consumer, the other two are optional modules.

| Change | Part | Spec | Nature |
| --- | --- | --- | --- |
| Java 25, Spring Boot 4.1, `java-parent` 2.0.0 | 1 | 025 | **required** platform baseline |
| Boot 4 starter renames | 1 | 025 | build change in your POM |
| Jackson 3 (`tools.jackson.*`) in four public constructors / methods | 1 | 025 | **source break** if you call them |
| `JsonProcessingException` → `JacksonException` | 1 | 025 | source change if you catch it around library calls |
| MCP SDK 2.x, WebMVC transport from Spring AI | 1 | 025 | source break if you build MCP tools without `McpToolSupport` |
| **Webhook payload `timestamp` is an ISO-8601 string, no longer a number** | 1 | 025 | **breaking wire change for webhook receivers** |
| **RFC 9457 problem details** for every error response | 2 | 024 | **breaking wire change, on by default** |
| **Previous state on `OnObjectUpdate`**, no-op updates suppressed, typed listeners fixed | 2 | 022 | source break for `postSave` overrides, behaviour change |
| Optional **object store** `ObjectStore` (`spring-services-storage`) | 2 | — | new optional module, off by default |
| Optional **ALTCHA bot protection** (`spring-services-altcha`) | 2 | 023 | new optional module, off by default |
| No database migration | — | — | — |

> **Two changes alter what third parties receive even if your code compiles unchanged:** the webhook
> `timestamp` format (Part 1, section 5) and problem details for every error response (Part 2). Read
> both before you deploy. Webhook receivers and API clients do not upgrade with you.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.
Do Part 1 first — the application has to build on Boot 4 before anything else can be verified.

---

# Part 1 — Spring Boot 4 (spec 025)

## Prompt

You are working inside a Spring Boot service that depends on `spring-services` 1.4.0 and moves to
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

### 6. Everything else is unchanged by the platform move

This section is about the Boot 4 move alone. Error responses **do** change compared to 1.4.0, but
because of problem details (Part 2), not because of the platform.

Captured on Boot 3.5 and on Boot 4.1 with the same library code and compared: RFC 9457 problem details (401, 400 with
`errors[]`, 404, 409 with an extension member, 500), SCIM `User` / `ListResponse` / `Error`, and the
library's REST DTOs (users, API keys, webhooks, audit log, application info) are byte-for-byte
identical. As before, the order of entries in a problem's `errors[]` is not specified.

No test expectation of the library had to change because of a platform default.

### 7. Verify

- `./mvnw verify` — the application builds and its tests pass on Java 25.
- If you send webhooks: a receiver test with an ISO-8601 `timestamp`.
- If you have custom MCP tools: `tools/list` and `tools/call` against `/mcp`.

---

# Part 2 — library changes since 1.4.0

These changes were developed for a 1.5.0 release that never shipped; they reach consumers with 2.0.0.
Problem details and the update-event change affect every consumer. The object store and ALTCHA are
optional modules that do nothing until you configure them.

## Problem details for error responses (spec 024)

**This is a breaking wire change and it is on by default.** With no configuration at all, every error
response from an application built on `spring-services` changes its content type from
`application/json` to `application/problem+json`, and its body changes shape.

Nothing in your database changes, and no Java code of yours stops compiling. What changes is what your
clients receive.

### What changed

| Source | Before | After |
| --- | --- | --- |
| `401` on the JWT, external API-key and MCP chains | `{"status":401,"error":"Unauthorized","message":"Invalid API key"}` | problem `unauthorized` |
| `403` (deactivated user, method security, `denyAll`) | Spring Security default, empty body | problem `forbidden` |
| ALTCHA verification failure (`400`) | `{"code":"altcha-expired","message":"…"}` | problem `altcha-expired` |
| Bean Validation on a request body (`400`) | Spring Boot default | problem `validation-failed` with `errors[]` |
| Unhandled exception (`500`) | `BasicErrorController` JSON | problem `internal-error` |
| Container error / `sendError(...)` from a filter | `BasicErrorController` JSON | problem for that status |
| SCIM (`/scim/v2/**`) | RFC 7644 `Error` | **unchanged** |

There are no compatibility fields. The old `error` and `message` members, Boot's `timestamp` and
ALTCHA's `code` are gone.

Before — an invalid API key:

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json
WWW-Authenticate: ApiKey realm="external"

{"status":401,"error":"Unauthorized","message":"Invalid API key"}
```

After:

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/problem+json
WWW-Authenticate: ApiKey realm="external"

{
  "type": "https://open-elements.com/problems/spring-services/unauthorized",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Invalid API key",
  "instance": "/api/external/contacts",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

Before — a body-validation failure:

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{"timestamp":"2026-11-12T10:00:00.000+00:00","status":400,"error":"Bad Request","path":"/api/contacts"}
```

After:

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "https://open-elements.com/problems/spring-services/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "instance": "/api/contacts",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [
    { "pointer": "/address/postal_code", "message": "must match \"[0-9]{5}\"" }
  ]
}
```

Before — a `403`:

```http
HTTP/1.1 403 Forbidden
Content-Length: 0
```

After:

```http
HTTP/1.1 403 Forbidden
Content-Type: application/problem+json

{
  "type": "https://open-elements.com/problems/spring-services/forbidden",
  "title": "Forbidden",
  "status": 403,
  "detail": "User account is disabled",
  "instance": "/api/contacts",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

---

### Prompt

You are working inside a Spring Boot service that depends on `spring-services` and is upgrading to
2.0.0.

### 1. Decide whether to adopt or defer

Search the repository and any client code you own for places that read the old error members:

```bash
grep -rn '"error"\|\.error\b\|"message"\|altcha-missing\|altcha-invalid\|altcha-expired\|altcha-reused' \
  --include='*.ts' --include='*.tsx' --include='*.java' --include='*.kt' .
```

- **Nothing matches, or you can update the matches now** → adopt. Continue with step 2.
- **A client you do not control parses the old body** → defer: set
  `openelements.problem-details.enabled=false`, note it as technical debt, and stop here. This
  restores the previous behaviour completely, including the empty `403` and ALTCHA's
  `{code, message}`. The trace ID keeps working in your logs either way.

### 2. Set your own type base

```properties
openelements.problem-details.type-base=https://example.org/problems/my-service/
```

Must be an absolute URI whose path ends with `/`; anything else fails start-up with a message naming
the property. It applies to **all** types, the library's included, so your `type` URIs end up under a
domain you can actually publish documentation on. Leaving the default points them at
`https://open-elements.com/problems/spring-services/`, where your application's own types will never
be documented.

### 3. Update clients to compare `type`

Clients must branch on the **full `type` URI**, not on `status` and not on `title`.

```ts
// before
if (body.code === "altcha-expired") { … }

// after
if (body.type === `${PROBLEM_BASE}altcha-expired`) { … }
```

The slug is unchanged in every case: whatever word the client matched before is now the last segment
of `type`.

### 4. Surface the trace ID

Add it to your log pattern so a client's bug report can be looked up:

```properties
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-}]
```

If your clients already send a W3C `traceparent` header, the `traceId` in the response is the
trace-id from that header.

### 5. Replace your own error handling, if you have any

If you wrote an `@ExceptionHandler` or an `ErrorController` only to get a uniform error body, delete
it — that is now the library's job. If you keep one:

- an advice for an exception the library does not map needs no change;
- an advice for a **standard MVC exception** (`MethodArgumentNotValidException`, for example) needs an
  explicit `@Order`, because the library's advice sits at `LOWEST_PRECEDENCE` and so does yours by
  default;
- your own `ResponseEntityExceptionHandler` subclass takes over the standard mappings entirely.

Inject `ProblemFactory` to build an RFC 9457 body from your own handler.

### 6. Replace ad-hoc error throwing with typed problems

```java
// before
throw new ResponseStatusException(HttpStatus.CONFLICT, "A contact with this e-mail already exists");

// after
throw new ProblemException(
    CrmProblemType.DUPLICATE_CONTACT,
    "A contact with this e-mail address already exists",
    Map.of("existingContactId", existing.id()));
```

A `ResponseStatusException` keeps working and becomes the problem for its status; it just gives the
client no identity beyond the status code.

### 7. Verify

```bash
./mvnw -B verify
curl -i https://localhost:8080/api/external/contacts          # 401 application/problem+json
curl -i https://localhost:8080/api/does-not-exist             # 404 application/problem+json
```

### Guard rails / Don't do this

- **Do not let clients branch on `status` or `title`.** `status` is too coarse and `title` is prose.
  `type` is the contract.
- **Do not put personal data in a `detail`.** It is written to the client verbatim and into your
  logs. The library never adds text of its own to an application problem.
- **Do not use `${validatedValue}` in a Bean Validation message template** unless you mean it. The
  interpolated message is passed through, so the rejected value would be echoed — which is exactly
  what the library otherwise never does.
- **Do not expect HTML error pages.** Responses are `application/problem+json` even for
  `Accept: text/html`. If you need HTML, supply your own `ErrorController`.
- **Do not change a slug once a client has seen it.** A slug is as binding as a REST path.
- **Do not expect SCIM to change.** `/scim/v2/**` keeps its RFC 7644 envelope on purpose.

---

## Previous state on update events (spec 022)

`OnObjectUpdate` now carries the state **before** the update when the publisher knows it.
`AbstractDbBackedDataService` and `AbstractMultitenantDbBackedDataService` always supply it.
Publishers that cannot know it (for example data from an external system) keep using
`new OnObjectUpdate<>(current)`:

```java
@EventListener
public void onTaskUpdated(OnObjectUpdate<TaskDto> event) {
  if (!event.isPreviousKnown()) {
    return; // or: treat as "something changed"
  }
  final TaskDto previous = event.getPrevious().orElseThrow();
  final TaskDto current = event.getData();
  if (!previous.columnId().equals(current.columnId())) { /* moved */ }
}
```

The event computes no diff and carries no actor. Compare the fields you care about yourself, and
resolve the actor through `AuthService`, as `AuditLogEventListener` does.

This file is a self-contained prompt for an agent to run inside a consumer repo.

### Prompt

You are working inside a Spring Boot service that depends on `spring-services` 2.0.0.

#### 1. Replace `postSave` overrides

`postSave(boolean insert, D data)` no longer exists on either data service base class. Any
`@Override` of it now fails to compile. Split it:

| Before | After |
| --- | --- |
| `postSave(true, data)` branch | `protected void postInsert(D data)` |
| `postSave(false, data)` branch | `protected void postUpdate(D previous, D current)` |

Call `super.postInsert(...)` / `super.postUpdate(...)` wherever the old override called
`super.postSave(...)`, otherwise the lifecycle events are no longer published.

#### 2. Make `toData` / `mapToData` return a snapshot

On update, the base class calls `toData(entity)` **before** `updateEntity` mutates the entity, to
capture the previous state. A DTO that holds the entity's own collection changes along with the
entity, so the previous state becomes equal to the new one and the update event is **silently
dropped** (see step 3). Search every `toData` / `mapToData` for collections passed through
unchanged and copy them:

```java
// wrong: shares the entity's mutable set
return new TaskDto(entity.getId(), entity.getAssignees());
// right
return new TaskDto(entity.getId(), Set.copyOf(entity.getAssignees()));
```

Mapping to a new collection (`stream().map(...).toList()`) is already a copy.

#### 3. Expect no event for no-op updates

An update where `previous.equals(current)` no longer publishes `OnObjectUpdate`. As a result, no
audit-log row is written and no webhook fires for it. Remove any listener code that only filtered
these events out. DTOs that are not records and do not implement `equals` are never equal and
behave as before.

#### 4. Check typed listeners

Before 2.0.0, a listener declared for `OnObjectUpdate<BookDto>` (or `OnObjectCreate<…>` /
`OnObjectDelete<…>`) was invoked for **every** DTO type, because Spring could not resolve the
type parameter. The events now implement `ResolvableTypeProvider`, so such listeners receive only
their own type. Search for listeners that relied on the old behaviour: a typed parameter combined
with an `event.getType()` check for a *different* type. Move them to `GenericDataEvent<?>`.
Listeners on `GenericDataEvent<?>` or raw types are unaffected.

#### 5. Optional: replace hand-written snapshots

If the application snapshots an entity before `save(...)` to diff it afterwards (like open-tasks'
`TaskSnapshot`), it can listen to `OnObjectUpdate` and compare `getPrevious()` with `getData()`
instead.

### Guard rails / Don't do this

- Do not treat `isPreviousKnown() == false` as "nothing changed". It means the publisher did not
  know the previous state.
- Do not persist `getPrevious()` without checking it for personal data that the update just
  removed.


---

## Object store (`spring-services-storage`)

`spring-services` 2.0.0 adds an **optional** object-store module, `spring-services-storage`. It gives
an application one `ObjectStore` interface for binary payloads — files, images, exports, recordings —
with three implementations behind it: an S3-compatible endpoint, a local directory, and the heap.

The feature is **off by default** and there is no database migration. Unless
`openelements.storage.type` is configured, the module registers no bean at all and there is nothing
to migrate. This guide applies only to consumers who want to turn it on.

> Not a Java-API break: nothing existing changes. The module is purely additive.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.

---

### Prompt

You are working inside a Spring Boot service that depends on `spring-services` and wants to store
binary objects.

### 1. Depend on the storage module

If you use `spring-services-all`, the module is already on the classpath — skip to step 2. Otherwise
add it (version managed by `spring-services-bom`):

```xml
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services-storage</artifactId>
</dependency>
```

### 2. Choose an implementation

Unlike every other feature module, this one does **not** self-activate on classpath presence. It
ships three implementations and all three are always on the classpath, so nothing about the classpath
distinguishes them — and classpath order must not be what decides where your objects land.
`openelements.storage.type` makes the choice explicit. With it unset, no `ObjectStore` bean exists.

**S3, or any S3-compatible endpoint** (AWS S3, Hetzner Object Storage, MinIO, …):

```properties
openelements.storage.type=s3
openelements.storage.s3.endpoint=https://s3.eu-central-1.amazonaws.com
openelements.storage.s3.bucket=my-objects
openelements.storage.s3.access-key=${S3_ACCESS_KEY}
openelements.storage.s3.secret-key=${S3_SECRET_KEY}

# Optional. Defaults to us-east-1, which providers that ignore the region accept.
openelements.storage.s3.region=eu-central-1
```

Endpoint, bucket and credentials are required for `type=s3`. The **region is optional**: it belongs
to the request *signature*, not to the address, so SigV4 needs some string there but only AWS derives
meaning from it — on AWS it must match the bucket's region, while Hetzner Object Storage and similar
providers accept whatever is sent. Credentials are high-value secrets — provide them from environment
variables or a secret manager, never in plaintext config. Path-style addressing is forced and chunked
encoding is disabled, because several S3-compatible providers support neither virtual-host addressing
nor trailing checksums.

**A local directory:**

```properties
openelements.storage.type=file
openelements.storage.file.root=/var/lib/my-app/objects
```

The directory is created if missing. The store owns it: it keeps objects under `root/objects/` and
in-flight writes under `root/uploads/`.

**The heap**, for tests and local development only:

```properties
openelements.storage.type=memory
```

### 3. Use it

```java
@Service
class ExportService {

    private final ObjectStore store;

    ExportService(final ObjectStore store) {
        this.store = store;
    }

    void write(final String key, final InputStream data) {
        store.put(key, data, "application/pdf");   // consumes and closes data
    }

    InputStream read(final String key) {
        return store.get(key);                     // you close it
    }
}
```

The full surface:

| Method | Notes |
| --- | --- |
| `put(key, data, contentType)` | streams in bounded parts; the payload is never held whole in memory |
| `get(key)` | the object's content stream; **the caller closes it** |
| `get(key, offset, length)` | a byte range, for seeking into a large object without reading it |
| `size(key)` | `OptionalLong` — empty means *no such object*, distinct from a zero-length one |
| `delete(key)` | idempotent; `true` if something was removed |
| `list(prefix)` | `Stream<StoredObject>` of key, size and last-modified |
| `abortIncompleteUploadsOlderThan(instant)` | reclaims interrupted uploads; see the guard rails |

Failures are `ObjectStoreException`; a read of a key that does not exist is `ObjectNotFoundException`.
Both are unchecked.

### What you get

- One interface over three backends, so the same code runs against S3 in production and a directory
  or the heap in a test, decided entirely by configuration.
- Streaming in both directions. An upload of any length moves through in bounded 8 MiB parts —
  single `PutObject` below that, multipart above — so payload size does not drive heap use. Note the
  flip side: the S3 store allocates one 8 MiB buffer per in-flight `put`, whatever the payload's
  size, so it is concurrency and not object size that sets the ceiling.
- A write that never becomes visible half-finished. The S3 store aborts its multipart upload on
  failure; the file store streams into a scratch file and moves it into place, so a reader sees
  either the previous object or the complete new one, never a truncated one.
- The same answers from every backend. A shared contract suite runs the interface's promises against
  all three implementations, so a ranged read past the end, a zero-length read of a missing key or a
  negative offset behaves identically whether you configured `s3`, `file` or `memory`.
- Declaring your own `ObjectStore` bean makes the library back off, whatever `type` says.

### Guard rails / Don't do this

- **`memory` loses every object when the process ends**, and each object sits on the heap for the
  lifetime of the store. It exists so a test or a laptop needs no infrastructure. Never point a
  deployed environment at it.
- **Nothing calls `abortIncompleteUploadsOlderThan` for you.** An upload interrupted mid-stream
  leaves an incomplete multipart upload on S3 (or a scratch file on disk) that `list(prefix)` cannot
  see — because S3 does not list them either — and that S3 **still bills you for**. If you accept
  uploads, schedule this sweep yourself with a threshold comfortably past your longest legitimate
  upload. The library ships no scheduler.
- **`put` consumes and closes the stream you hand it; `get` hands you one you must close.** A `get`
  whose stream is never closed leaks a pooled HTTP connection on S3 and a file handle on disk. Use
  try-with-resources.
- **`list(prefix)` returns a `Stream`, and on S3 it is lazy and paginated.** Consume it inside the
  call rather than storing it; a prefix matching a large number of objects paginates as you iterate.
- **`delete` returning `false` is not a guarantee that nothing was there.** The S3 store asks for the
  size first and then deletes, so the boolean reflects two round trips with a gap between them. Treat
  it as information, not as a lock.
- **Keys are not equally portable across the implementations.** On S3 a key is an opaque string, so
  `a/../b` is simply a key. In the file store a key is a path, so it is validated: an empty segment,
  `.`, `..`, a backslash, or a leading/trailing `/` is rejected with `IllegalArgumentException`. Keys
  that work against S3 can therefore fail against the file store. Generate keys from values you
  control (UUIDs, hashes), never from unvalidated user input.
- **The file store ignores `contentType`.** A file system has nowhere to keep it and the interface
  offers no way to read it back — your application stores an object's content type in its own
  database. Do not expect the store to remember it.
- **There is no transaction integration.** A write that succeeded is not rolled back when the
  surrounding database transaction fails. If you need the two to agree, write the object first and
  record it second, then reconcile orphans with `list` — which is what
  `abortIncompleteUploadsOlderThan` complements rather than replaces.
- **Maturity: the module is new in 2.0.0.** The implementations arrived from an application that ran
  them in production, and they are covered here by a shared contract suite — the S3 store against a
  real S3 server, including its multipart path. Two edges are still untested: aborting an actually
  interrupted multipart upload, and the S3 store's own abort-on-failure path during `put`.
- **The AWS SDK comes along even if you only use `type=file`.** `software.amazon.awssdk:s3` is a
  hard dependency of the module today. Making it optional is planned; until then, budget for it in
  your dependency footprint.


---

## ALTCHA bot protection (`spring-services-altcha`, spec 023)

`spring-services` 2.0.0 adds an **optional** module that protects endpoints without authentication —
contact, sign-up or lead forms — against automated submissions with [ALTCHA](https://altcha.org): a
self-hosted proof of work, without an external service and without cookies.

The feature is **off by default** and there is no database migration. Unless
`openelements.altcha.enabled=true` is configured, the module registers no bean and needs no secret.

> Not a Java-API break: nothing existing changes. The module is purely additive.

This file is a self-contained prompt for an agent to run inside a consumer repo.

### Prompt

You are working inside a Spring Boot service that depends on `spring-services` and has a public form
that bots should not be able to submit.

#### 1. Depend on the module

If you use `spring-services-all`, the module is already on the classpath — skip to step 2. Otherwise
add it (version managed by `spring-services-bom`):

```xml
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services-altcha</artifactId>
</dependency>
```

#### 2. Enable it

```properties
openelements.altcha.enabled=true
openelements.altcha.hmac-key=${ALTCHA_HMAC_KEY}   # at least 32 characters, from a secret manager
```

Optional: `challenge-path` (default `/api/altcha/challenge`), `expires` (default `PT10M`), `cost`
(default `5000`) and `key-prefix-length` (default `2`). Start-up fails if the module is enabled
without a key of at least 32 characters.

#### 3. Add the widget to the form

Load the `altcha` widget **3.x** (protocol v2) and point it at the challenge path:

```html
<script async defer src="https://cdn.jsdelivr.net/npm/altcha@3.3.0/dist/altcha.min.js" type="module"></script>
<form method="post" action="/api/my-form">
  …
  <altcha-widget challengeurl="https://my-service.example/api/altcha/challenge"></altcha-widget>
</form>
```

The widget puts its solution into the form field `altcha`.

#### 4. Verify in the endpoint

```java
@PostMapping("/api/my-form")
ResponseEntity<Void> submit(@RequestParam(name = "altcha", required = false) String altcha, …) {
    altchaService.requireVerified(altcha);   // throws AltchaVerificationException
    …
}
```

Call it first, before validation, rate limiting or persistence. A failed check is answered by the
module with `400` and an RFC 9457 problem whose `type` ends in `altcha-missing`, `altcha-invalid`,
`altcha-expired` or `altcha-reused`; the `detail` is English or German depending on
`Accept-Language`. Match on the full `type` URI in the frontend, show `detail` to the visitor.

> With `openelements.problem-details.enabled=false` the module answers with its previous body,
> `{"code": "altcha-…", "message": "…"}`. The stable word is the same in both: it is the `code` in the
> old body and the last segment of `type` in the new one. See the problem-details section above.

### Guard rails / Don't do this

- **The module opens only the challenge path, not your form.** Your form endpoint is behind the
  default JWT chain until you permit it yourself (for example with your own `SecurityFilterChain` for
  its path).
- **`InMemoryReplayStore` is correct for one instance only.** With several instances a solution can be
  replayed once per instance. Declare your own `ReplayStore` bean (database, Redis) — the library
  backs off.
- **Do not raise `cost` or `key-prefix-length` without measuring on a slow phone.** Each step of
  `key-prefix-length` multiplies the average work by 16.
- **Another error format?** Declare your own `AltchaExceptionHandler` bean, or catch
  `AltchaVerificationException` in your own advice; `code()` and `getMessage()` are public.
- **Never put the `hmac-key` in committed configuration.** Anyone with the key can sign their own
  challenges.

