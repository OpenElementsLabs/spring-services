# TODOs

- services/system repo implementation to enable the config of external services
- Logging should be added at several places
- Basics for Hibernate search (full text search)
- Migrate ApiKeyDataService to our data pattern
- Migrate SettingsDataService to our data pattern
- Metrics
- Eigenes DB Schema mit Flyway für jedes Modul.
  Siehe https://stackoverflow.com/questions/49303184/how-to-handle-a-modular-spring-project-with-flyway-and-single-db
- ApiKeyDataService.KEY_PREFIX soll konfigurierbar sein (je app)
- ~~SCIM 2.0 Foundation (Schritt 1)~~ — **implementiert via Spec 012** (`UserEntity` erweitert, JIT-Korrelation,
  Active-Gate, `UserEntityPrincipalDirectory`). Folge-Arbeiten siehe TODO-Punkte zum SCIM-Provider unten.

- SCIM 2.0 Provider (Schritt 2, nach Foundation): Server-Endpoints `/scim/v2/Users`, `/scim/v2/Groups` und Discovery
  (ServiceProviderConfig, Schemas, ResourceTypes) für Push-Provisioning aus Authentik/IdP. Library-Basis:
  UnboundID/Ping SCIM 2 SDK + eigene Spring-MVC-Controller. Auth: JWT in dediziertem dritten Filter-Chain,
  isoliert vom default-Chain. Soft-Deactivation via `active=false`; DELETE soft. Audit-Log-Eintrag pro SCIM-Write.
  **Erweitert `UserEntityPrincipalDirectory`** aus Spec 012: GroupEntity wird hinzugefügt, Group-Membership wird
  in `ResolvedPrincipal.groups()` ausgeliefert, optional auch in `ResolvedPrincipal.roles()` per
  Group→Role-Mapping (konfigurierbar). Damit werden USER-Tokens role-aware. Offene Designfragen für Schritt 2
  (Liste aus Grill-Session): PATCH-Mechanik, Filter-Grammatik-Scope (eq/sw/co/AND/OR), ETag/Optimistic-Concurrency,
  Pagination, Tenant-Interaktion (eine SCIM-Instance pro Tenant?), Vendor-Extensions von Authentik,
  Discovery-Ehrlichkeit, Group-Rename/Delete und Auswirkungen auf abgeleitete Rollen, ob `revokeAllForSubject`
  bei SCIM-Deactivation als hard-revoke-Hook zusätzlich zur live-Resolution gewünscht ist.

- Spec 011 (Security Configuration Hygiene) — Follow-ups aus dem `/spec-review`:
  - **MockMvc-Integration-Test für JWT-Chain-`JsonAuthenticationEntryPoint`** (Bearer-Scheme über echten HTTP-Roundtrip):
    aktuell nur Unit-Level abgedeckt (`JsonAuthenticationEntryPointTest`). Voraussetzung: ein JWT-issuing
    Test-Fixture (eigener `JwtDecoder`, der hand-gecrafted Tokens akzeptiert, plus ein kleiner Test-Controller).
    Rundet Behavior-Szenario "Missing JWT on default chain produces same error shape" ab.
  - **`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`** anlegen?
    Trade-off: Zero-Config-Integration für Konsumenten (kein `@Import(FullSpringServiceConfig.class)` mehr nötig)
    gegen weniger expliziten Kontrolle. Verändert den Integration-Contract — eigener Spec wert.
  - **Per-Feature `@ConditionalOnMissingBean` / `@ConditionalOnProperty`** für alle Library-Beans:
    erlaubt Konsumenten, eigene `JwtAuthenticationConverter`s zu registrieren oder einzelne Features per
    Property zu deaktivieren (`openelements.security.api-key.enabled=false`). Braucht zuerst eine
    Property-Namens-Konvention und einen kohärenten Toggle-Plan.
  - **INDEX.md-Status** von Spec 011 nach Merge in `main` auf `done` flippen.

- Test-Pool-Sizing-Konvention dokumentieren: integration tests setzen
  `spring.datasource.hikari.maximum-pool-size=30` weil `UserProvisioner` mit `REQUIRES_NEW` zwei Connections
  pro Provisioning-Thread belegt. Production-Apps brauchen das in ihrer eigenen `application.yml`
  (`peak_concurrent_first_logins × 2 + steady-state`). Aktuell nur im README Upgrade-Notes erwähnt — sollte
  ggf. auch in einer "Production Deployment Notes"-Sektion stehen, sobald die existiert.

Fragen zur Nutzung des Moduls die wir uns genau anschauen müssen:

Garantiert Authentik-Konfiguration das name-Claim für jeden User, der sich anmeldet?
Konkret: hat jeder User in Authentik ein gepflegtes name-Attribut, und ist das profile-Scope-Mapping im
Authentik-Provider
so eingestellt, dass name immer im JWT landet — oder kann es User geben (z.B. SSO-importierte, frisch angelegte ohne
Profil), bei denen name fehlt? Wenn unsicher: brauchen wir einen serverseitigen Fallback (name ← preferred_username ←
sub),
bevor das JWT spring-services erreicht?

Q4 (Branch F — OidcHealthIndicator): Der Indicator liest heute issuer-uri und pingt
${issuer-uri}/.well-known/openid-configuration. Wir stellen auf jwk-set-uri um. Zwei Optionen:

(a) Beide Keys behalten — issuer-uri nur für den Indicator, jwk-set-uri für die JWT-Validierung. Indicator pingt
Discovery
wie bisher. Zwei OIDC-URLs pro Umgebung zu pflegen.

(b) Indicator auf jwk-set-uri umstellen — pingt direkt den JWKS-Endpoint. Eine Config-URL. Verliert den Check, ob das
Discovery-Dokument valide ist (für uns als Resource-Server fachlich aber irrelevant — wir brauchen nur JWKS).
 