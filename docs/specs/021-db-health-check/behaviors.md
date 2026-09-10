# Behaviors: Database reachability check (`DbHealthService`)

Written retroactively alongside `design.md`: the scenarios below describe the behaviour the shipped
implementation already has, and each maps onto an existing test. Scenarios marked **[postgres]** run
against a real PostgreSQL container via Testcontainers; the rest are unit-level with a mocked
`DataSource`. One scenario is marked **[uncovered]** — it states behaviour that is only pinned
indirectly, see *Known coverage gap* in the design.

## When the database answers

### A reachable database is reported as reachable

- **Given** a `DataSource` that hands out a working connection
- **When** `dbHealthService.isDatabaseReachable()` is called
- **Then** the result is `true`

### The check executes the validation query and releases both resources

- **Given** a `DataSource` that hands out a working connection
- **When** `dbHealthService.isDatabaseReachable()` is called
- **Then** `SELECT 1` is executed on a statement created from that connection
- **And** the statement is closed
- **And** the connection is closed, so it returns to the pool

## When the database does not answer

### No connection can be borrowed

- **Given** a `DataSource` whose `getConnection()` throws an `SQLException`
- **When** `dbHealthService.isDatabaseReachable()` is called
- **Then** the result is `false`
- **And** no exception escapes the method
- **And** the failure is logged at `WARN` with the exception message, and at `DEBUG` with the stack
  trace

### The statement cannot be created

- **Given** a connection whose `createStatement()` throws an `SQLException`
- **When** `dbHealthService.isDatabaseReachable()` is called
- **Then** the result is `false`
- **And** the connection is still closed

### The validation query fails

- **Given** a statement whose `execute(...)` throws an `SQLException`
- **When** `dbHealthService.isDatabaseReachable()` is called
- **Then** the result is `false`
- **And** both statement and connection are closed

### The connection pool throws an unchecked exception

- **Given** a `DataSource` whose `getConnection()` throws a `RuntimeException` — the shape a pool uses
  to signal a connection-acquisition timeout
- **When** `dbHealthService.isDatabaseReachable()` is called
- **Then** the result is `false`
- **And** no exception escapes — catching only `SQLException` would let a saturated pool break the
  caller's health endpoint

## Construction

### A `null` data source is rejected

- **Given** no data source
- **When** `new DbHealthService(null)` is constructed
- **Then** a `NullPointerException` is thrown with the message `dataSource must not be null`
- **And** the failure happens at construction time, not on the first check

## Against a real database

### A running PostgreSQL is reported as reachable **[postgres]**

- **Given** a running PostgreSQL container and the `DataSource` pointing at it
- **When** `dbHealthService.isDatabaseReachable()` is called
- **Then** the result is `true`

### Repeated checks stay reachable, so no pooled connection leaks **[postgres]**

- **Given** the same running database
- **When** `isDatabaseReachable()` is called repeatedly, more often than the pool has connections
- **Then** every call returns `true`
- **And** the pool is never exhausted — which is the regression guard for a connection or statement
  that is not closed

### A database that dies after startup is reported as unreachable **[uncovered]**

- **Given** an application that started successfully against a reachable database
- **And** the database becomes unreachable afterwards (stopped, partitioned, credentials rotated)
- **When** `dbHealthService.isDatabaseReachable()` is called
- **Then** the result is `false` — not the cached "up" a bean-level check would report
- **And** this is the motivating scenario of the whole service, yet no integration test stops the
  container to prove it end to end: doing so would make the module's test run slow and
  order-dependent. The behaviour is pinned by the failing-`DataSource` unit tests above

## Contract

### The result is never cached

- **Given** two consecutive calls to `isDatabaseReachable()`
- **When** the database state changes between them
- **Then** the second call reflects the new state
- **And** each call performs its own round-trip

### The library contributes no endpoint and no status vocabulary

- **Given** an application on `spring-services-core`
- **When** its context starts
- **Then** the only bean added by this feature is `DbHealthService`
- **And** no controller, no request mapping and no health-status enum ships with it — path,
  authorization and response shape stay with the application

### The bean is registered exactly once

- **Given** a consumer that both imports `DataConfig` and component-scans the library package
- **When** the context starts
- **Then** exactly one `DbHealthService` bean exists — the explicit `@Bean` method cannot be
  registered twice, unlike a `@Service`-annotated class picked up by a scan
