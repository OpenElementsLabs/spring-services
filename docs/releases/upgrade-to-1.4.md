# Upgrade prompt: enabling the SCIM 2.0 Users provider (spec 015)

`spring-services` 1.4.0 adds an **optional** SCIM 2.0 Users service-provider module,
`spring-services-scim`. It lets an external identity provider (Authentik, or any RFC 7644 client)
push-provision users into the library's existing `UserEntity` over an isolated, bearer-token-secured
`/scim/v2/**` surface.

The feature is **off by default**: unless `openelements.scim.token` is configured, the module ships
nothing (no filter chain, no endpoints, no beans) and there is nothing to migrate. This guide applies
only to consumers who want to turn SCIM on.

> This is not a Java-API break. It adds two additive columns to the `users` table and a reserved
> service-principal row; both are backward-compatible. As with every prior spec the library ships the
> migration SQL below — you apply it through your own Flyway/Liquibase timeline (spec 013 contract).

This file is a self-contained prompt for an agent (Claude Code, etc.) to run inside a consumer repo.

---

## Prompt

You are working inside a Spring Boot service that depends on `spring-services` and wants to enable
SCIM user provisioning.

### 1. Depend on the SCIM module

If you use `spring-services-all`, the SCIM module is already on the classpath — skip to step 2.
Otherwise add it (version managed by `spring-services-bom`):

```xml
<dependency>
    <groupId>com.open-elements</groupId>
    <artifactId>spring-services-scim</artifactId>
</dependency>
```

### 2. Apply the database migration

Add a new versioned migration to your own Flyway/Liquibase timeline:

```sql
-- Vx__scim_users_provider.sql
ALTER TABLE oe_spring_services.users ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE oe_spring_services.users ADD COLUMN deleted_at TIMESTAMPTZ;

-- Reserved SCIM service principal (fixed UUID, inactive) — the audit actor for SCIM writes.
INSERT INTO oe_spring_services.users (id, user_name, name, active, deleted, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-0000000000cf', 'scim', 'SCIM Provisioning', FALSE, FALSE,
        now(), now())
ON CONFLICT (id) DO NOTHING;
```

(The library also creates the service-principal row at startup if it is missing, mirroring the System
user; the `INSERT` above makes it explicit for environments that run with `ddl-auto=validate/none`.)

### 3. Configure the SCIM token

Supply the static shared-secret bearer token your IdP will send on every SCIM request. It is a
high-value secret — provide it from a secret manager, never in plaintext config, and never log it:

```properties
openelements.scim.token=${SCIM_TOKEN}
```

Point your IdP's SCIM provider at `https://<your-host>/scim/v2` with the same token as its bearer
credential. Rotation = change this property and the IdP token together; there is no server-side state.

### What you get

- `POST/GET/PUT/DELETE /scim/v2/Users` mapped onto `UserEntity` (correlated with OIDC JIT-login by
  `externalId`; SCIM never writes `sub`). `POST` of an existing `externalId`/`userName` returns
  `409 uniqueness`; the IdP recovers via filter + `PUT`.
- Discovery endpoints (`ServiceProviderConfig`, `ResourceTypes`, `Schemas`).
- A dedicated `/scim/v2/**` security chain, fully isolated from the JWT and API-key chains — the SCIM
  token cannot be used against `/api/**` and vice versa.

### Guard rails / Don't do this

- `DELETE` is a **soft delete** (`active=false`, `deleted=true`, `deleted_at=now`) — it does **not**
  scrub PII. Foreign keys from audit-log and comments stay intact; GDPR erasure is a separate,
  deferred module. Do not assume SCIM `DELETE` satisfies a data-subject erasure request on its own.
- Groups are **not** implemented in this slice: `GET /scim/v2/Groups` returns an empty list and every
  group write returns `501 Not Implemented`. Do not enable group provisioning in your IdP yet.
- User `PATCH` is not implemented (`501`); Authentik updates via `PUT` full-replace.
- Do not point your **application's own** entities at the reserved SCIM principal UUID.
