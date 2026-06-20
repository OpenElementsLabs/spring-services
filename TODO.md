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
- SCIM 2.0 Foundation (Schritt 1, vorgelagert): User- und Group-Modell auf SCIM vorbereiten, ohne SCIM-Endpoints zu
  bauen. Konkret: `UserEntity.sub` nullable machen, neue Felder `externalId` (unique, indexed), `userName` (unique)
  und `active` (boolean) ergänzen; `GroupEntity` mit `externalId`, `displayName` und Membership einführen;
  Group→Role-Mapping definieren; JIT-Login so erweitern, dass `externalId`/`userName` mitgepflegt werden und ein
  evtl. vorab angelegter User per `externalId`/`userName` korreliert wird; Deactivation-Pfad (`active=false`) blockt
  JIT-Provisioning, JWT-Auth und PAT-Auth. Hintergrund: ohne diese Änderungen kann SCIM keine User vor dem ersten
  Interactive-Login korrelieren (heute ist `sub` der einzige Schlüssel und ist im SCIM-Provisioning-Zeitpunkt
  unbekannt). Die heutige `UserEntity.roles`-Spalte (aus Spec 010) bleibt physisch, wird aber zukünftig aus
  Group-Membership gespeist; JWT-`roles`-Claim bleibt als Fallback bestehen.

- SCIM 2.0 Provider (Schritt 2, nach Foundation): Server-Endpoints `/scim/v2/Users`, `/scim/v2/Groups` und Discovery
  (ServiceProviderConfig, Schemas, ResourceTypes) für Push-Provisioning aus Authentik/IdP. Library-Basis:
  UnboundID/Ping SCIM 2 SDK + eigene Spring-MVC-Controller. Auth: JWT in dediziertem dritten Filter-Chain
  (analog `/api/external/**`), isoliert von default und external. Soft-Deactivation via `active=false`; DELETE soft.
  Audit-Log-Eintrag pro SCIM-Write. Offene Designfragen für Schritt 2 (Liste aus Grill-Session): PATCH-Mechanik,
  Filter-Grammatik-Scope (eq/sw/co/AND/OR), ETag/Optimistic-Concurrency, Pagination, Tenant-Interaktion (eine
  SCIM-Instance pro Tenant?), Vendor-Extensions von Authentik, Discovery-Ehrlichkeit (ServiceProviderConfig muss
  exakt das deklarieren, was wir tatsächlich können), Group-Rename/Delete und Auswirkungen auf abgeleitete Rollen.

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
 