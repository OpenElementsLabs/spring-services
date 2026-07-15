package com.openelements.spring.base.services.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SCIM {@code emails} multi-valued attribute entry (RFC 7643 §4.1.2). This slice persists only the
 * primary entry's {@code value} (mapped to {@code UserEntity.email}).
 *
 * @param value the email address
 * @param primary whether this is the primary email, or {@code null} if unspecified
 * @param type the email type (e.g. {@code work}), accepted but not persisted
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimEmail(String value, Boolean primary, String type) {}
