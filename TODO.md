# TODOs

## Admin role

Kann ich aus @PreAuthorize("hasRole('ADMIN')") sowas machen wie:

@PreAuthorize("hasRole(Roles.ROLE_ADMIN)")
public @interface NeedsAdminRole {
}

## Next steps:

- services/system repo implementation to enable the config of external services
- Logging should be added at several places
- data crud for basic update data based on "onObjectCreated"... events (only store Type, ID and user)
- Basics for Hibernate search (full text search)
- Support for Avatar pictures coming from authentik
- Migrate ApiKeyDataService to our data pattern
- Migrate SettingsDataService to our data pattern
- Metrics
- Send mails