# TODOs

## Admin role

Kann ich aus @PreAuthorize("hasRole('ADMIN')") sowas machen wie:

@PreAuthorize("hasRole(Roles.ROLE_ADMIN)")
public @interface NeedsAdminRole {
}

## Next steps:

- services/system repo implementation to enable the config of external services
- Logging should be added at several places
- Basics for Hibernate search (full text search)
- Migrate ApiKeyDataService to our data pattern
- Migrate SettingsDataService to our data pattern
- Metrics
- Eigenes DB Schema mit Flyway für jedes Modul.
  Siehe https://stackoverflow.com/questions/49303184/how-to-handle-a-modular-spring-project-with-flyway-and-single-db
- ApiKeyDataService.KEY_PREFIX soll konfigurierbar sein (je app)
- Associate user information with API keys to replace the `UNKNOWN` user returned by
  `AuthService.getUserInformation()` in API-key contexts (audit log entries currently record
  `"UNKNOWN"` for actions performed via API key)