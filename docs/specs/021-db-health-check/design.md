# Design: Database reachability check (`DbHealthService`)

## GitHub Issue

— (PR [#40](https://github.com/OpenElementsLabs/spring-services/pull/40))

> **Written retroactively.** The implementation landed in commit `f0066b7` before a spec existed;
> this document describes what was built, and the decisions are reconstructed from the code, its
> Javadoc and its tests rather than made here. It exists so the repository's spec-driven convention
> holds for this feature too, and so the next change to it has a design to argue against.

## Summary

An application that booted successfully can still have a dead database: a network partition, an
exhausted connection pool, a restarted server, rotated credentials. Inspecting the `DataSource` bean
reports "healthy" in every one of those cases. `spring-services-core` therefore ships one read-only
bean, `DbHealthService`, whose single method borrows a pooled connection and executes `SELECT 1` —
a real round-trip — and answers `true` or `false`. The library supplies the fact and nothing else:
no endpoint, no status vocabulary.

## Goals

- Answer *"is the database reachable right now?"* with an observation, not with cached boot state.
- Be safe to call from a health or readiness endpoint: **never throw**, so no caller needs
  `try`/`catch` to reach the same answer.
- Cost the consuming application nothing to adopt — no configuration, no new dependency.
- Leave the HTTP surface entirely to the application.

## Non-goals

- **No health endpoint.** Path, authorization, response shape and the words used for "up" and "down"
  are application decisions.
- **No status enum.** The library returns a `boolean`; the application owns its own vocabulary.
- **No Spring Boot Actuator integration.** No `HealthIndicator` is registered and Actuator is not a
  dependency — that belongs to the planned `spring-services-actuator` module (`docs/TODO.md`).
- **No caching, no rate limiting.** Every call is a fresh round-trip; throttling is the caller's
  business.
- **No diagnosis.** The result is binary. *Why* the database is unreachable is in the log, not in the
  return value.
- **No validation of anything but reachability** — no schema check, no migration state, no replica
  lag.

## Technical approach

### The service

`com.openelements.spring.base.data.DbHealthService`, one constructor and one method:

```java
public DbHealthService(DataSource dataSource)          // rejects null
public boolean isDatabaseReachable()                    // never throws
```

The check is:

```java
try (Connection connection = dataSource.getConnection();
     Statement statement = connection.createStatement()) {
  statement.execute("SELECT 1");
  return true;
} catch (final SQLException | RuntimeException e) {
  LOG.warn("Database is not reachable: {}", e.getMessage());
  LOG.debug("Database health check failed", e);
  return false;
}
```

Both resources are closed by try-with-resources, so the pooled connection is returned even when the
query fails.

### Wiring

A new `DataConfig` (`@Configuration(proxyBeanMethods = false)`) declares the bean explicitly, and
`FullSpringServiceConfig` imports it so the everything-configuration contributes it as well.

```mermaid
flowchart LR
    A["Application health endpoint"] --> B["DbHealthService.isDatabaseReachable()"]
    B --> C["DataSource.getConnection()"]
    C --> D["Statement.execute(\"SELECT 1\")"]
    D -- ok --> T["true"]
    C -- "SQLException / RuntimeException" --> F["WARN + DEBUG, false"]
    D -- "SQLException" --> F
```

## Design decisions and rationale

### D1: A real round-trip, not a bean-level check

The failure modes that matter all leave the `DataSource` bean intact — partition, pool exhaustion,
restarted database, rotated credentials. Only borrowing a connection *and* executing a statement
proves the path works end to end.

### D2: `SELECT 1` as the validation query

The cheapest statement that proves a borrowed connection can execute anything: it touches no table,
takes no lock, and is accepted by both databases this library runs against — PostgreSQL in
production, H2 in tests. `Connection.isValid(int)` was not used: it is driver-defined and may be
answered from pool state rather than by reaching the server.

### D3: Never throws — `SQLException` **and** `RuntimeException` are caught

A health check that throws forces every caller into a `try`/`catch` that arrives at the same answer.
`RuntimeException` is caught deliberately alongside `SQLException`, because a connection pool signals
acquisition timeouts as an unchecked exception; catching only `SQLException` would let a saturated
pool blow up the health endpoint. The message goes to `WARN`, the stack trace to `DEBUG`, so an
unreachable database is visible in production logs without one stack trace per probe interval.

### D4: No caching — and the cost is documented, not hidden

Each call is a point-in-time observation. The consequence is stated in the Javadoc rather than
smoothed over: frequent pollers (Kubernetes probes, uptime monitors) occupy one pooled connection per
call for the duration of the query, and against a saturated pool the call blocks for up to the pool's
connection timeout. A cache would make the answer stale, which is exactly the defect this service
exists to fix.

### D5: No endpoint and no status enum

An endpoint would impose a path, an authorization rule and a response shape on every consumer, and
those differ per application. A `HealthStatus` enum in the library would compete with the one the
application already has. The library supplies the fact; the application decides what to do with it.

### D6: An explicit `@Bean`, not `@ComponentScan` + `@Service`

A single bean does not justify a package scan, and an explicit `@Bean` method cannot be registered
twice when a consumer both imports `DataConfig` and scans the library package. This follows the same
reasoning spec 016 applied to `TenantConfig`, where a `@ComponentScan` was dropped in favour of an
explicit bean for exactly that double-registration hazard.

### D7: `data` is the right package

The service is about the `DataSource`, which the `data` package already owns conceptually
(`AbstractEntity`, `AbstractDbBackedDataService`, `DataService`). It needs no entity, no repository
and no transaction, so it introduces no new dependency in either direction.

## Security considerations

- **No credential exposure.** Only `e.getMessage()` reaches `WARN`; JDBC drivers do not put
  credentials there, and the full stack trace is `DEBUG`-only.
- **The result is a fact, not an authorization decision.** The application decides who may see it —
  which is one reason no endpoint ships with the library. An unauthenticated health endpoint that
  distinguishes "database down" from "application up" leaks infrastructure state to anyone who asks;
  that trade-off belongs to the application.
- **Denial-of-service surface.** Because every call borrows a connection, an unauthenticated,
  unthrottled health endpoint calling this service is a way to occupy the pool. Documented in the
  Javadoc; mitigating it (auth, caching, rate limiting) is the application's job.

## Data protection (GDPR / DSGVO)

No personal data is read, stored, logged or transmitted. `SELECT 1` touches no table.

## Testing strategy — as implemented

**Unit tests** (`DbHealthServiceTest`, 7 tests in three nested groups):

| Group | Cases |
|---|---|
| *when the database answers* | reports reachable; executes `SELECT 1` and closes both connection and statement |
| *when the database does not answer* | no connection can be borrowed; statement cannot be created; the validation query fails; the pool throws a `RuntimeException` |
| *construction* | a `null` data source is rejected |

**Integration tests** (`DbHealthServiceIntegrationTest`, 2 tests, real PostgreSQL via Testcontainers):
a running database reports reachable, and repeated checks stay reachable — which is the regression
guard for a leaked pooled connection.

**Known coverage gap, deliberately accepted:** no integration test stops the container to observe a
database that dies *after* startup — the motivating scenario. Stopping it would make the test slow and
order-dependent for every other test in the module. That path is covered by the unit tests through a
failing `DataSource`, so the behaviour is pinned; only the end-to-end proof against a real server is
missing.

## Dependencies

None. `javax.sql.DataSource` and `java.sql.*` are JDK; SLF4J and Spring come with
`spring-services-core` already.

## Migration / impact on existing applications

Purely additive: one new bean in a new configuration class, one import added to
`FullSpringServiceConfig`, no schema change, no property, no behaviour change to anything that
existed. Applications that do not inject `DbHealthService` are unaffected — except that the bean now
requires a `DataSource` in the context wherever `DataConfig` is active, which every application using
the data layer already has.

## Open questions

- **Actuator.** Once `spring-services-actuator` exists (`docs/TODO.md`), a `HealthIndicator` wrapping
  this service is the obvious contribution. It must reuse the service rather than re-implement the
  probe, so the two never disagree.
- **Timeout.** The call inherits the pool's connection-acquisition timeout and the driver's socket
  timeout; there is no timeout of its own. For a readiness probe with a tight deadline that may be too
  slow. Whether the service should accept a timeout is unresolved — and a `Statement.setQueryTimeout`
  would cover only the query, not the acquisition.
