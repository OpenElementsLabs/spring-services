package com.openelements.spring.base.services.scim.model;

/** SCIM 2.0 schema URN constants (RFC 7643 / RFC 7644). */
public final class ScimSchemas {

  /** Core {@code User} resource schema URN. */
  public static final String USER = "urn:ietf:params:scim:schemas:core:2.0:User";

  /** {@code ListResponse} message schema URN. */
  public static final String LIST_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:ListResponse";

  /** {@code Error} message schema URN. */
  public static final String ERROR = "urn:ietf:params:scim:api:messages:2.0:Error";

  /** {@code ServiceProviderConfig} resource schema URN. */
  public static final String SERVICE_PROVIDER_CONFIG =
      "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";

  /** {@code ResourceType} resource schema URN. */
  public static final String RESOURCE_TYPE = "urn:ietf:params:scim:schemas:core:2.0:ResourceType";

  private ScimSchemas() {}
}
