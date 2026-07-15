package com.openelements.spring.base.services.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SCIM {@code name} complex attribute (RFC 7643 §4.1.1). Only {@code formatted} is persisted by this
 * slice (mapped to {@code UserEntity.name}); {@code givenName}/{@code familyName} are accepted on
 * input but not stored separately.
 *
 * @param formatted the full display name
 * @param givenName the given (first) name, accepted but not persisted separately
 * @param familyName the family (last) name, accepted but not persisted separately
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimName(String formatted, String givenName, String familyName) {}
