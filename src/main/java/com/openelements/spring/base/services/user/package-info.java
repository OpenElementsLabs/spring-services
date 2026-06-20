/**
 * Local user-profile mirror for the authenticated identities supplied by the OAuth2 identity
 * provider.
 *
 * <p>The platform deliberately does not own credentials — authentication is handled entirely by an
 * external OAuth2 provider via JWT. This package only persists the subset of information about a
 * user that the application itself needs to attach to domain data (display name, email, avatar
 * image), keyed by the JWT's {@code sub} claim.
 *
 * <h2>Lifecycle</h2>
 *
 * <ul>
 *   <li>The {@link com.openelements.spring.base.services.user.UserEntity} for a given JWT subject
 *       is created lazily on first access by {@link
 *       com.openelements.spring.base.services.user.UserService#getCurrentUser()}.
 *   <li>If the JWT's {@code name}, {@code email} or {@code avatar} claim has changed since the last
 *       call, the local entity is updated transparently inside the same request transaction.
 *   <li>The avatar URL is stored as-is and points directly at the identity provider's image
 *       endpoint — the library never proxies, caches or downloads it.
 * </ul>
 *
 * <h2 id="concurrency">Concurrency and first-login race recovery</h2>
 *
 * <p>Two requests for the same previously-unknown JWT {@code sub} can arrive at almost the same
 * moment — typical in modern frontends that fire several authenticated API calls in parallel on
 * page load, and unavoidable in horizontally-scaled deployments where two instances receive the
 * same user's first request from a load balancer. The design must produce <em>exactly one</em>
 * {@code UserEntity} row and let every concurrent caller receive a managed reference to it,
 * without serialising the hot path of repeat logins.
 *
 * <h3>The mechanism in three layers</h3>
 *
 * <ol>
 *   <li><b>The unique constraint on {@code users.sub} is the actual coordinator.</b> Postgres's
 *       unique B-Tree index acquires a row-level lock on the index entry the moment an {@code
 *       INSERT} arrives, and holds it until that transaction commits or rolls back. A second
 *       concurrent {@code INSERT} for the same {@code sub} blocks on this lock and then either
 *       succeeds (if the first transaction rolled back) or fails with {@code SQLSTATE 23505}
 *       (unique violation) — Postgres serialises us automatically, across threads <em>and</em>
 *       across application instances. We do not need a JVM-level lock to add a second layer of
 *       protection.
 *   <li><b>The {@link com.openelements.spring.base.services.user.UserProvisioner} runs the {@code
 *       INSERT} in its own transaction.</b> Annotated {@link
 *       org.springframework.transaction.annotation.Transactional}{@code (REQUIRES_NEW)}, it
 *       suspends the caller's transaction, opens a fresh inner one, and calls {@link
 *       org.springframework.data.jpa.repository.JpaRepository#saveAndFlush(Object)} so that any
 *       unique-constraint violation surfaces <em>here</em>, inside the inner transaction. If the
 *       inner transaction loses the race, only it is rolled back. The caller's outer transaction
 *       — held by {@code UserService.getCurrentUserEntity()} — is untouched, still alive, still
 *       commit-eligible.
 *   <li><b>{@code UserService} catches the {@code DataIntegrityViolationException} and retries
 *       {@code findBySub}.</b> Because the failure was scoped to the inner transaction, Spring
 *       does <em>not</em> mark the outer transaction as rollback-only, and Postgres does not put
 *       the outer connection into the "current transaction is aborted" state. The retry runs
 *       normally and finds the row that the winning concurrent {@code INSERT} just committed.
 * </ol>
 *
 * <p>The result: with N concurrent first-login requests for the same subject, exactly one {@code
 * INSERT} commits, the other N−1 each cost one extra round-trip to the DB to read the winner's
 * row. Repeat-login traffic does not pay anything for this safety — only the very first request
 * for a previously-unknown subject takes the {@code REQUIRES_NEW} hop.
 *
 * <h3>Why {@code UserProvisioner} must be a separate Spring bean</h3>
 *
 * <p>Spring applies {@code @Transactional} via AOP proxies: when one bean calls another bean's
 * {@code @Transactional} method, the call goes through the dynamic proxy, which is what starts
 * the new transaction. A bean calling its <em>own</em> {@code @Transactional} method (e.g. {@code
 * this.provision(...)}) bypasses the proxy entirely — the call resolves directly against the
 * class instance, so Spring never gets a chance to start an inner transaction. {@code
 * REQUIRES_NEW} would silently degrade into "reuse the existing transaction", defeating the
 * whole isolation argument above.
 *
 * <p>That is why the insert lives in {@code UserProvisioner}, not as a private method on {@code
 * UserService}. The split is not a stylistic choice — it is what makes the {@code REQUIRES_NEW}
 * semantics actually work.
 *
 * <h3>Operational note for consuming applications</h3>
 *
 * <p>The {@code REQUIRES_NEW} hop temporarily holds two connections per provisioning thread (one
 * for the suspended outer transaction, one for the inner). Under sustained first-login bursts —
 * unusual in steady state, possible during a coordinated bulk-onboarding — the HikariCP pool
 * needs enough headroom. The library's integration tests run with {@code
 * spring.datasource.hikari.maximum-pool-size=30}; production deployments should size the pool
 * with the same arithmetic in mind: {@code peak_concurrent_first_logins * 2 + steady-state
 * traffic} should fit.
 */
package com.openelements.spring.base.services.user;
