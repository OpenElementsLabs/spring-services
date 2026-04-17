# TODOs

## Admin role

Kann ich aus @PreAuthorize("hasRole('ADMIN')") sowas machen wie:

@PreAuthorize("hasRole(Roles.ROLE_ADMIN)")
public @interface NeedsAdminRole {
}


