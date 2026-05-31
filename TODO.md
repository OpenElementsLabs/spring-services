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

Garantiert Authentik-Konfiguration das name-Claim für jeden User, der sich anmeldet?
Konkret: hat jeder User in Authentik ein gepflegtes name-Attribut, und ist das profile-Scope-Mapping im
Authentik-Provider
so eingestellt, dass name immer im JWT landet — oder kann es User geben (z.B. SSO-importierte, frisch angelegte ohne
Profil), bei denen name fehlt? Wenn unsicher: brauchen wir einen serverseitigen Fallback (name ← preferred_username ←
sub),
bevor das JWT spring-services erreicht?
