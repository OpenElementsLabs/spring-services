# Upgrade prompt: spring-services 1.5.0 optional-module changes

> **Draft — 1.5.0 is unreleased.** This document is written as the release takes shape; sections are
> added as features land. Everything described here is on `main` today.

`spring-services` 1.5.0 brings one change so far. Every section below stands on its own — apply only
the ones you need:

| Change | Spec | Nature |
| --- | --- | --- |
| Optional **object store** `ObjectStore` | — | new optional module, off by default, no schema change |

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
openelements.storage.s3.region=eu-central-1
openelements.storage.s3.bucket=my-objects
openelements.storage.s3.access-key=${S3_ACCESS_KEY}
openelements.storage.s3.secret-key=${S3_SECRET_KEY}
```

All five are required for `type=s3`. Credentials are high-value secrets — provide them from
environment variables or a secret manager, never in plaintext config. Path-style addressing is forced
and chunked encoding is disabled, because several S3-compatible providers support neither
virtual-host addressing nor trailing checksums.

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
