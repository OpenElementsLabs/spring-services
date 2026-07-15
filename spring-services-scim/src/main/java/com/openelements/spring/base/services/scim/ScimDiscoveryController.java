package com.openelements.spring.base.services.scim;

import com.openelements.spring.base.services.scim.model.ScimSchemas;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCIM 2.0 discovery endpoints (RFC 7644 §4): {@code ServiceProviderConfig}, {@code ResourceTypes}
 * and {@code Schemas}. The documents are static and <em>capability-honest</em> — they advertise
 * {@code patch:false}, no group writes, and no ETag/bulk/sort, so a well-behaved client never
 * attempts an operation this slice does not support.
 */
@RestController
@RequestMapping(path = "/scim/v2", produces = ScimMediaType.SCIM_JSON)
public class ScimDiscoveryController {

  /** Advertised maximum number of results returned from a filtered list. */
  private static final int MAX_RESULTS = 200;

  /** Creates the discovery controller. */
  public ScimDiscoveryController() {}

  /**
   * Serves {@code GET /scim/v2/ServiceProviderConfig}.
   *
   * @return the service-provider capability document
   */
  @GetMapping("/ServiceProviderConfig")
  public Map<String, Object> serviceProviderConfig() {
    return Map.of(
        "schemas", List.of(ScimSchemas.SERVICE_PROVIDER_CONFIG),
        "documentationUri", "https://github.com/OpenElementsLabs/spring-services",
        "patch", Map.of("supported", false),
        "bulk", Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0),
        "filter", Map.of("supported", true, "maxResults", MAX_RESULTS),
        "changePassword", Map.of("supported", false),
        "sort", Map.of("supported", false),
        "etag", Map.of("supported", false),
        "authenticationSchemes",
            List.of(
                Map.of(
                    "name", "OAuth Bearer Token",
                    "description", "Authentication via a static OAuth bearer token",
                    "type", "oauthbearertoken",
                    "primary", true)));
  }

  /**
   * Serves {@code GET /scim/v2/ResourceTypes}.
   *
   * @return the list of supported resource types (only {@code User} is functional in this slice)
   */
  @GetMapping("/ResourceTypes")
  public List<Map<String, Object>> resourceTypes() {
    return List.of(
        Map.of(
            "schemas", List.of(ScimSchemas.RESOURCE_TYPE),
            "id", "User",
            "name", "User",
            "endpoint", "/Users",
            "description", "SCIM core User",
            "schema", ScimSchemas.USER));
  }

  /**
   * Serves {@code GET /scim/v2/Schemas}.
   *
   * @return the list of supported schemas (the core {@code User} schema)
   */
  @GetMapping("/Schemas")
  public List<Map<String, Object>> schemas() {
    return List.of(
        Map.of(
            "id", ScimSchemas.USER,
            "name", "User",
            "description", "SCIM core User",
            "attributes",
                List.of(
                    attribute("userName", "string", true),
                    attribute("externalId", "string", false),
                    attribute("displayName", "string", false),
                    attribute("active", "boolean", false))));
  }

  private static Map<String, Object> attribute(
      final String name, final String type, final boolean required) {
    return Map.of(
        "name", name,
        "type", type,
        "multiValued", false,
        "required", required,
        "caseExact", false,
        "mutability", "readWrite",
        "returned", "default",
        "uniqueness", "userName".equals(name) ? "server" : "none");
  }
}
