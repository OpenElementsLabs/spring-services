package com.openelements.spring.base.services.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SCIM {@code meta} complex attribute (RFC 7643 §3.1) describing a resource's metadata.
 *
 * @param resourceType the resource type name (e.g. {@code User})
 * @param location the canonical URI of the resource
 * @param version the resource version / ETag, or {@code null} (not supported by this slice)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimMeta(String resourceType, String location, String version) {}
