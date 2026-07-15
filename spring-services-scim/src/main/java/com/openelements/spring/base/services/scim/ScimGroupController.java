package com.openelements.spring.base.services.scim;

import com.openelements.spring.base.services.scim.model.ScimListResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Graceful Groups stub for the Users-only slice. {@code GET /scim/v2/Groups} returns an empty
 * {@code ListResponse} so a Users-only reconcile (e.g. Authentik) does not error, while every group
 * write returns {@code 501 Not Implemented}. Full {@code GroupEntity} support is a follow-up issue.
 */
@RestController
@RequestMapping(path = "/scim/v2/Groups", produces = ScimMediaType.SCIM_JSON)
public class ScimGroupController {

  /** Creates the Groups stub controller. */
  public ScimGroupController() {}

  /**
   * Serves {@code GET /scim/v2/Groups} as an empty list.
   *
   * @return an empty SCIM {@code ListResponse}
   */
  @GetMapping
  public ScimListResponse<Object> list() {
    return ScimListResponse.of(List.of(), 0, 1);
  }

  /**
   * Rejects group creation.
   *
   * @return never returns normally
   * @throws ScimNotImplementedException always
   */
  @PostMapping
  public ScimListResponse<Object> create() {
    throw new ScimNotImplementedException("Group provisioning is not implemented");
  }

  /**
   * Rejects group replacement.
   *
   * @return never returns normally
   * @throws ScimNotImplementedException always
   */
  @PutMapping("/**")
  public ScimListResponse<Object> replace() {
    throw new ScimNotImplementedException("Group provisioning is not implemented");
  }

  /**
   * Rejects group patching.
   *
   * @return never returns normally
   * @throws ScimNotImplementedException always
   */
  @PatchMapping("/**")
  public ScimListResponse<Object> patch() {
    throw new ScimNotImplementedException("Group provisioning is not implemented");
  }

  /**
   * Rejects group deletion.
   *
   * @return never returns normally
   * @throws ScimNotImplementedException always
   */
  @DeleteMapping("/**")
  public ScimListResponse<Object> delete() {
    throw new ScimNotImplementedException("Group provisioning is not implemented");
  }
}
