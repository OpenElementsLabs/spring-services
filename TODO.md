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
- SCIM 2.0 Provider support: Endpoints für User/Group-Sync von Authentik bereitstellen, damit User- und
  Gruppenänderungen aus Authentik aktiv in den lokalen Store gepusht werden. Hintergrund: user-gebundene API-Tokens
  (PATs) brauchen aktuelle Permission-Daten. Erster Schritt ist JIT-Sync beim OIDC-Login; SCIM ist der saubere
  zentrale Kanal, sobald mehrere Services denselben User-State brauchen.

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
 