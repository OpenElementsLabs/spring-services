package com.openelements.spring.base.services.scim;

import com.openelements.spring.base.services.scim.model.ScimListResponse;
import com.openelements.spring.base.services.scim.model.ScimUser;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCIM 2.0 Users resource endpoints (RFC 7644 §3.2–§3.6) under {@code /scim/v2/Users}, all speaking
 * {@code application/scim+json}. Delegates business rules to {@link ScimUserService}; SCIM errors are
 * rendered by {@link ScimExceptionHandler}.
 */
@RestController
@RequestMapping(path = "/scim/v2/Users", produces = ScimMediaType.SCIM_JSON)
public class ScimUserController {

  /** Default page size when the client does not supply {@code count} (matches the advertised max). */
  private static final int DEFAULT_COUNT = 200;

  private final ScimUserService scimUserService;

  /**
   * Creates the controller.
   *
   * @param scimUserService the SCIM user service
   */
  public ScimUserController(final ScimUserService scimUserService) {
    this.scimUserService = scimUserService;
  }

  /**
   * Creates a user ({@code POST /scim/v2/Users}).
   *
   * @param request the SCIM user payload
   * @return {@code 201 Created} with the created user and a {@code Location} header
   */
  @PostMapping(consumes = ScimMediaType.SCIM_JSON)
  public ResponseEntity<ScimUser> create(@RequestBody final ScimUser request) {
    final ScimUser created = scimUserService.create(request);
    return ResponseEntity.created(URI.create("/scim/v2/Users/" + created.id())).body(created);
  }

  /**
   * Returns a user by id ({@code GET /scim/v2/Users/{id}}).
   *
   * @param id the user id
   * @return {@code 200 OK} with the SCIM user
   */
  @GetMapping("/{id}")
  public ScimUser getById(@PathVariable final UUID id) {
    return scimUserService.getById(id);
  }

  /**
   * Lists / filters users ({@code GET /scim/v2/Users}).
   *
   * @param filter an optional {@code userName eq}/{@code externalId eq} filter
   * @param startIndex the 1-based index of the first result
   * @param count the maximum number of results to return
   * @return a SCIM {@code ListResponse}
   */
  @GetMapping
  public ScimListResponse<ScimUser> list(
      @RequestParam(name = "filter", required = false) final String filter,
      @RequestParam(name = "startIndex", defaultValue = "1") final int startIndex,
      @RequestParam(name = "count", defaultValue = "" + DEFAULT_COUNT) final int count) {
    return scimUserService.list(filter, startIndex, count);
  }

  /**
   * Full-replace update of a user ({@code PUT /scim/v2/Users/{id}}).
   *
   * @param id the user id
   * @param request the replacement SCIM payload
   * @return {@code 200 OK} with the updated user
   */
  @PutMapping(path = "/{id}", consumes = ScimMediaType.SCIM_JSON)
  public ScimUser replace(@PathVariable final UUID id, @RequestBody final ScimUser request) {
    return scimUserService.replace(id, request);
  }

  /**
   * Soft-deletes a user ({@code DELETE /scim/v2/Users/{id}}).
   *
   * @param id the user id
   * @return {@code 204 No Content}
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable final UUID id) {
    scimUserService.softDelete(id);
    return ResponseEntity.noContent().build();
  }
}
