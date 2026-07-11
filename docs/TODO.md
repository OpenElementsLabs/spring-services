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

- SCIM 2.0 Provider (Schritt 2, nach Foundation) — **in Teil-Specs aufgeteilt** (Grill-Session 2026-07-11):
  - **Spec 015 — SCIM Users Provider** (in Arbeit): dedizierter dritter Filter-Chain für `/scim/v2/**` mit
    **statischem Bearer-Token** (`openelements.scim.token`, **nicht** JWT — Authentik sendet ein festes
    opakes Token), Discovery-Endpoints (ServiceProviderConfig/ResourceTypes/Schemas), Users-Resource
    (List+Filter, POST, GET, PUT-Replace, DELETE) gemappt auf `UserEntity` aus Spec 012. `POST`-Kollision →
    `409 uniqueness` (RFC), DELETE → soft (`active=false` + `deleted`/`deleted_at`). Audit-Einträge unter
    reserviertem **SCIM-Service-Principal**. Vor-Merge-Verifikation der Authentik-`409`-Recovery am
    #21-Harness (siehe Issue-Kommentar).
  - **Folge-Issue A — SCIM Groups + Membership**: `GroupEntity` + Membership-Tabelle, `/scim/v2/Groups`
    (POST/GET/PUT/**PATCH** mit PatchOp add/remove/replace auf `members`, DELETE). Liefert
    `ResolvedPrincipal.groups()` aus `UserEntityPrincipalDirectory` (heute leer).
  - **Folge-Issue B — Group→Role-Mapping**: konfigurierbare Ableitung von `ResolvedPrincipal.roles()` aus
    Gruppen-Mitgliedschaft; macht USER-Tokens role-aware.
  - Verbleibende offene Designfragen für die Folge-Issues (aus Grill-Sessions): PATCH-Mechanik,
    Filter-Grammatik jenseits `eq` (sw/co/AND/OR), ETag/Optimistic-Concurrency, Tenant-Interaktion
    (eine SCIM-Instance pro Tenant?), Authentik-Vendor-Extensions, Discovery-Ehrlichkeit, Group-Rename/Delete
    und Auswirkungen auf abgeleitete Rollen, ob `revokeAllForSubject` bei SCIM-Deactivation als
    zusätzlicher Hard-Revoke-Hook gewünscht ist.

- **DSGVO-/Anonymisierungs-Modul muss `UserEntity` abdecken.** Das (noch zu bauende) Anonymisierungs-Modul
  muss gezielt SCIM-**soft-gelöschte** Nutzer erfassen (`deleted=true` / `deleted_at` gesetzt, aus Spec 015)
  und deren PII (`name`, `email`, `avatarUrl`, `userName`) nach Retention-Policy scrubben. Spec 015 löscht
  bewusst *nicht* physisch (FK-Integrität zu Audit-Log/Kommentaren), sondern deaktiviert soft und markiert —
  das Scrubben ist Aufgabe dieses Moduls.
  **Context:** Grill-Session 2026-07-11 zu Spec 015 (SCIM Users), Branch D (DELETE-Semantik).
  **Prerequisite:** Spec 015 (liefert die `deleted`/`deleted_at`-Marker).

- **Feld-Ownership `name`/`email` beobachten (SCIM vs. JWT-JIT).** Spec 015 wählt bewusst *Last-Writer-Wins*
  für `name`/`email`: sowohl SCIM-`PUT` als auch der JIT-Drift-Sync (Spec 012) schreiben diese Felder. Bei
  Single-IdP (Authentik ist Quelle für OIDC *und* SCIM) stimmen die Werte überein → kein Konflikt. Falls in
  Zukunft divergierende Quellen auftreten (Ping-Pong-Churn im Audit-Log sichtbar), muss eine explizite
  Ownership-Regel („SCIM gewinnt für SCIM-managed Rows") nachgezogen werden.
  **Context:** Grill-Session 2026-07-11 zu Spec 015, Branch C (PUT-Full-Replace vs. Feld-Ownership).
  **Prerequisite:** Spec 015.

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

- **Spec-Kandidat "Test Hygiene & Bug Fixes"** (Grundlage für `/spec-create` + `/grill-me`).
  Im Rahmen der Test-Dokumentation aller 45 Test-Files (Class-Javadoc + `@DisplayName` + Mock-Audit,
  durchgeführt nach Spec 012) sind drei separate Befunde aufgefallen, die eine eigene Spec rechtfertigen.
  Die Spec wird über `/spec-create` mit vorgeschaltetem `/grill-me` ausgearbeitet — die folgenden Punkte
  sind die rohen Findings, **nicht** das fertige Spec-Design.

  **Befund 1 — Echter Bug: `WebhookEventListener.handle(...)` ignoriert `eventType`**
  - Datei: `src/main/java/com/openelements/spring/base/services/webhook/WebhookEventListener.java`
  - Symptom: die Methode signiert `eventType` als Parameter, ruft aber auf den Zeilen ~45-47 in beiden
    Branches (Payload-Bau + `WebhookSupport.supports(...)`) hardcoded `WebhookDataEventType.DELETED`
    statt den übergebenen Parameter zu nutzen. Dadurch werden CREATED- und UPDATED-Events als
    DELETED an Subscribers verschickt.
  - Test-Status: der bestehende Test `WebhookEventListenerTest.shouldHandleCreate` passt zufällig durch,
    weil er nur prüft "Sender wurde aufgerufen". Agent-6 (Test-Doku-Pass) hat den Bug im Test-Javadoc
    dokumentiert statt ihn stillschweigend zu fixen — siehe Klassen-Javadoc von `WebhookEventListenerTest`.
  - Grill-Fragen für die Spec: müssen die Subscriber-Signaturen geändert werden? Gibt es schon Apps, die
    sich auf das Bug-Verhalten verlassen (DELETED bei jedem Lifecycle-Event)? Wie testen wir die drei
    Varianten verlässlich?

  **Befund 2 — Über-Mocking in Integration-Tests**
  - `ApiKeyDataServiceIntegrationTest` — `@MockBean UserService` in einem Test, der ansonsten echte
    Postgres via Testcontainers nutzt. Sollte echten User per `userRepository.save(...)` seeden und
    `AuthService` minimal stubben. Macht das "Integration"-Label ehrlich.
  - `ApiKeyDataServiceIntegrationTest` — `@MockBean AuthService` wird nur wegen transitiver Wiring
    deklariert, von den Tests aber nie gelesen. Entweder enger scopen oder durch No-Op-`SecurityContext`-
    Setup ersetzen.
  - `CommentServiceIntegrationTest` — `@MockitoSpyBean UserService` wird nur für eine einzige
    `verify(never()).findById(...)`-Assertion verwendet. Die N+1-Behauptung lässt sich durch
    Hibernate-`Statistics`-Counter (bereits in derselben Klasse genutzt für andere Assertions) direkter
    und ehrlicher ausdrücken — der Spy wird obsolet.
  - Grill-Fragen für die Spec: gibt es weitere Integration-Tests mit verstecktem Mocking, das wir
    übersehen haben? Ist die Konvention "echte Beans in Integration-Tests, Mocks nur in Unit-Tests"
    bereits in `CLAUDE.md` festgehalten? Soll das ergänzt werden?

  **Befund 3 — Cosmetic: ObjectProvider-/FilterChain-Mocks ersetzbar durch echte Implementations**
  - `SlackServiceTest` + `EmailServiceTest` — `mock(ObjectProvider.class)` mit `@SuppressWarnings("unchecked")`.
    Spring's `ObjectProvider` ist ein Functional Interface — `(ObjectProvider<X>) () -> instance`
    (bzw. `() -> null`) ist lesbarer und vermeidet den Cast.
  - `ApiKeyAuthenticationFilterTest` — `FilterChain`-Mock ist konventionell, aber eine "RecordingFilterChain"
    (echte Impl, die Aufrufe in eine Liste schreibt) wäre robuster gegen Double-Invocation-Bugs.
  - `SecurityConfigRoleTest` — `ApiKeyDataService`-Mock ist No-Op-Konstruktor-Argument. Kann durch
    No-Op-Impl ersetzt werden oder via Factory-Extraction (`jwtAuthenticationConverter()` als statischer
    Helper ohne `ApiKeyDataService`-Dependency) komplett entfallen.
  - `AuditLogDataServiceTest` — `UserRepository` + `ApplicationEventPublisher` sind in den Reader-Tests
    toter Mock-Ballast. Wenn Reader/Writer-Split der Datenservices kommt (siehe TODO oben zu Migration
    auf `AbstractDbBackedDataService`-Pattern), können beide Mocks entfallen.
  - Grill-Fragen für die Spec: lohnt sich eine generelle "no `mock(...)` für functional interfaces"-Regel
    in der `CLAUDE.md`? Sollen wir SonarQube-/PMD-Rules dafür konfigurieren?

  **Scope-Frage für die Spec selbst:** Sollen alle drei Befunde in einer Spec landen oder werden sie
  in zwei getrennt (Bug-Fix #1 isoliert, Cleanup #2 + #3 zusammen)? Die Grill-Session entscheidet.

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

- **Spring Modulith für echte Modulgrenzen evaluieren.** Nach dem Multi-Modul-Umbau (Spec 014) sind die
  Modulgrenzen reine Konvention: plain Maven-Classpath-Module ohne JPMS (bewusst gewählt, da Spring sich schlecht
  mit JPMS verträgt), d.h. jeder `public` Typ in `spring-services-core` ist modulübergreifend erreichbar. Spring
  Modulith könnte die fachlichen Spring-Modulgrenzen explizit deklarieren und per `ApplicationModules`-Test
  verifizieren (erlaubte Abhängigkeiten, keine Zugriffe auf interne Pakete).
  **Context:** Surfaced in der `/grill-me`-Session zu Spec 014 (Multi-Modul-Umbau), Branch E (API-Oberfläche zwischen
  Modulen) — JPMS wurde verworfen, Modulith als Alternative geparkt.
  **Prerequisite:** Spec 014 (Multi-Modul-Umbau) muss zuerst landen.
 