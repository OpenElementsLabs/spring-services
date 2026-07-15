package com.openelements.spring.base.services.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * SCIM 2.0 {@code User} resource representation (RFC 7643 §4.1), in the dialect observed from the
 * Authentik capture (issue #21). Only the attributes this slice maps to {@code UserEntity} are
 * modelled; unknown attributes on input are ignored by the controller's lenient object mapper.
 *
 * @param schemas the resource schema URNs; defaults to the core User schema when serialised
 * @param id the server-assigned resource id (the {@code UserEntity} UUID); {@code null} on create
 *     requests
 * @param externalId the client-assigned stable identifier (Authentik's id); the correlation key
 * @param userName the unique business-key handle (required by RFC and by the DB constraint)
 * @param name the complex name attribute; its {@code formatted} value maps to the display name
 * @param displayName the display name; an alternative source for the persisted name
 * @param emails the email addresses; only the primary entry's value is persisted
 * @param active whether the user is active; maps to the spec-012 activation gate
 * @param meta the resource metadata (resource type + location), or {@code null} on requests
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimUser(
    List<String> schemas,
    String id,
    String externalId,
    String userName,
    ScimName name,
    String displayName,
    List<ScimEmail> emails,
    Boolean active,
    ScimMeta meta) {}
