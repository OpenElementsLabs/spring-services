# Upgrade prompt: spring-services 1.5.0 optional-module changes

> **Draft — 1.5.0 is unreleased.** This document is written as the release takes shape; sections are
> added as features land. Everything described here is on `main` today.

`spring-services` 1.5.0 brings four changes so far. Every section below stands on its own — apply only
the ones you need:

| Change | Spec | Nature |
| --- | --- | --- |
| Optional **object store** `ObjectStore` | — | new optional module, off by default, no schema change |
| **Previous state on `OnObjectUpdate`**, no-op updates suppressed, typed listeners fixed | 022 | source break for `postSave` overrides, behaviour change, no schema change |
| Optional **ALTCHA bot protection** for public forms | 023 | new optional module, off by default, no schema change |
| **RFC 9457 problem details** for every error response | 024 | **breaking wire change, on by default**, no schema change |

> **Read the problem-details section even if you change nothing else.** It is the one change in 1.5.0
> that alters responses your clients already consume, and it is active without any configuration.

## Object store (`spring-services-storage`)

`spring-services` 1.5.0 adds an **optional** object-store module, `spring-services-storage`. It gives
an application one `ObjectStore` interface for binary payloads — files, images, exports, recordings —
with three implementations behind it: an S3-compatible endpoint, a local directory, and the heap.

The feature is **off by default** and there is no database migration. Unless
`openelements.storage.type` is configured, the module registers no bean at all and there is nothing
to migrate. This guide applies only to consumers who want to turn it on.

> Not a Java-API break: nothing existing changes. The module is purely additive.

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.

---

## Prompt

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
- **Maturity: the module is new in 1.5.0.** The implementations arrived from an application that ran
  them in production, and they are covered here by a shared contract suite — the S3 store against a
  real S3 server, including its multipart path. Two edges are still untested: aborting an actually
  interrupted multipart upload, and the S3 store's own abort-on-failure path during `put`.
- **The AWS SDK comes along even if you only use `type=file`.** `software.amazon.awssdk:s3` is a
  hard dependency of the module today. Making it optional is planned; until then, budget for it in
  your dependency footprint.

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

You are working inside a Spring Boot service that depends on `spring-services` 1.5.0.

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

Before 1.5.0, a listener declared for `OnObjectUpdate<BookDto>` (or `OnObjectCreate<…>` /
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

## ALTCHA bot protection (`spring-services-altcha`, spec 023)

`spring-services` 1.5.0 adds an **optional** module that protects endpoints without authentication —
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
> old body and the last segment of `type` in the new one. See the problem-details section below.

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

---

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
1.5.0.

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
