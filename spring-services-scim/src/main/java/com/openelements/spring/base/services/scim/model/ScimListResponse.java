package com.openelements.spring.base.services.scim.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * SCIM {@code ListResponse} message (RFC 7644 §3.4.2) wrapping a page of resources.
 *
 * @param <T> the resource type carried in {@code Resources}
 * @param schemas the message schema URNs (the ListResponse URN)
 * @param totalResults the total number of matching resources across all pages
 * @param startIndex the 1-based index of the first returned resource
 * @param itemsPerPage the number of resources in this page
 * @param resources the resources in this page (serialised as the capitalised {@code Resources})
 */
public record ScimListResponse<T>(
    List<String> schemas,
    int totalResults,
    int startIndex,
    int itemsPerPage,
    @JsonProperty("Resources") List<T> resources) {

  /**
   * Builds a {@code ListResponse} with the correct schema URN and derived {@code itemsPerPage}.
   *
   * @param resources the resources in this page
   * @param totalResults the total number of matching resources across all pages
   * @param startIndex the 1-based index of the first returned resource
   * @param <T> the resource type
   * @return a populated {@code ListResponse}
   */
  public static <T> ScimListResponse<T> of(
      final List<T> resources, final int totalResults, final int startIndex) {
    return new ScimListResponse<>(
        List.of(ScimSchemas.LIST_RESPONSE), totalResults, startIndex, resources.size(), resources);
  }
}
