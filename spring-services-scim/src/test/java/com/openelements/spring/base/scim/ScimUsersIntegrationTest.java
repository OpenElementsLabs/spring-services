package com.openelements.spring.base.scim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openelements.spring.base.services.scim.ScimServicePrincipal;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * HTTP integration tests for the SCIM Users provider, driving the real {@code /scim/v2/**} surface
 * through {@link MockMvc} against a Postgres Testcontainer with the SCIM module activated.
 */
@SpringBootTest(classes = ScimTestApp.class)
@Import(ScimTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
class ScimUsersIntegrationTest {

  private static final String SCIM = "application/scim+json";
  private static final String AUTH = "Authorization";
  private static final String TOKEN = "Bearer secret-123";

  private MockMvc mvc;

  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired
  void configureMockMvc(final WebApplicationContext context) {
    this.mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                .springSecurity())
            .build();
  }

  @BeforeEach
  void cleanUsers() {
    jdbcTemplate.update("delete from oe_spring_services.audit_log");
    jdbcTemplate.update(
        "delete from oe_spring_services.users where id not in (?, ?)",
        SystemUser.ID,
        ScimServicePrincipal.ID);
  }

  // ---- Authentication -------------------------------------------------------------------------

  @Test
  @DisplayName("A valid SCIM token is accepted")
  void validTokenAccepted() throws Exception {
    mvc.perform(get("/scim/v2/Users").header(AUTH, TOKEN)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("A missing bearer token is rejected with a SCIM 401")
  void missingTokenRejected() throws Exception {
    mvc.perform(get("/scim/v2/Users"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.schemas[0]", is("urn:ietf:params:scim:api:messages:2.0:Error")))
        .andExpect(jsonPath("$.status", is("401")));
  }

  @Test
  @DisplayName("A wrong bearer token is rejected")
  void wrongTokenRejected() throws Exception {
    mvc.perform(get("/scim/v2/Users").header(AUTH, "Bearer wrong"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("The SCIM token does not authenticate against the JWT chain")
  void scimTokenDoesNotLeakToJwtChain() throws Exception {
    mvc.perform(get("/api/some-protected-path").header(AUTH, TOKEN))
        .andExpect(status().isUnauthorized());
  }

  // ---- Discovery ------------------------------------------------------------------------------

  @Test
  @DisplayName("ServiceProviderConfig advertises honest capabilities")
  void serviceProviderConfig() throws Exception {
    mvc.perform(get("/scim/v2/ServiceProviderConfig").header(AUTH, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.patch.supported", is(false)))
        .andExpect(jsonPath("$.filter.supported", is(true)))
        .andExpect(jsonPath("$.bulk.supported", is(false)))
        .andExpect(jsonPath("$.etag.supported", is(false)))
        .andExpect(jsonPath("$.authenticationSchemes[0].type", is("oauthbearertoken")));
  }

  @Test
  @DisplayName("ResourceTypes and Schemas are served")
  void resourceTypesAndSchemas() throws Exception {
    mvc.perform(get("/scim/v2/ResourceTypes").header(AUTH, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id", is("User")));
    mvc.perform(get("/scim/v2/Schemas").header(AUTH, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id", is("urn:ietf:params:scim:schemas:core:2.0:User")));
  }

  // ---- Create ---------------------------------------------------------------------------------

  @Test
  @DisplayName("POST creates a brand-new user with a server-assigned id and sub = NULL")
  void createUser() throws Exception {
    final String location =
        mvc.perform(
                post("/scim/v2/Users").header(AUTH, TOKEN).contentType(SCIM).content(alice()))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.userName", is("alice")))
            .andReturn()
            .getResponse()
            .getHeader("Location");

    final UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    final UserEntity row = userRepository.findById(id).orElseThrow();
    assertThat(row.getExternalId()).isEqualTo("ext-1");
    assertThat(row.getName()).isEqualTo("Alice");
    assertThat(row.getEmail()).isEqualTo("alice@example.com");
    assertThat(row.isActive()).isTrue();
    assertThat(row.isDeleted()).isFalse();
    assertThat(row.getSub()).isNull();
  }

  @Test
  @DisplayName("POST with an existing externalId returns 409 uniqueness")
  void createDuplicateExternalId() throws Exception {
    seed("alice", "ext-1", true, false);
    mvc.perform(
            post("/scim/v2/Users")
                .header(AUTH, TOKEN)
                .contentType(SCIM)
                .content(user("different", "ext-1")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.scimType", is("uniqueness")));
  }

  @Test
  @DisplayName("POST with an existing userName returns 409 uniqueness")
  void createDuplicateUserName() throws Exception {
    seed("alice", "ext-1", true, false);
    mvc.perform(
            post("/scim/v2/Users")
                .header(AUTH, TOKEN)
                .contentType(SCIM)
                .content(user("alice", "ext-2")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.scimType", is("uniqueness")));
  }

  @Test
  @DisplayName("POST without userName returns 400 invalidValue")
  void createMissingUserName() throws Exception {
    mvc.perform(
            post("/scim/v2/Users").header(AUTH, TOKEN).contentType(SCIM).content("{\"active\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.scimType", is("invalidValue")));
  }

  // ---- Read -----------------------------------------------------------------------------------

  @Test
  @DisplayName("GET by id returns an existing user; deleted and unknown ids return 404")
  void getById() throws Exception {
    final UUID id = seed("alice", "ext-1", true, false);
    mvc.perform(get("/scim/v2/Users/" + id).header(AUTH, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userName", is("alice")));

    final UUID deleted = seed("bob", "ext-2", false, true);
    mvc.perform(get("/scim/v2/Users/" + deleted).header(AUTH, TOKEN))
        .andExpect(status().isNotFound());

    mvc.perform(get("/scim/v2/Users/" + UUID.randomUUID()).header(AUTH, TOKEN))
        .andExpect(status().isNotFound());
  }

  // ---- List & filter --------------------------------------------------------------------------

  @Test
  @DisplayName("List excludes soft-deleted rows and the reserved principals")
  void listExcludesDeletedAndReserved() throws Exception {
    seed("alice", "ext-1", true, false);
    seed("bob", "ext-2", true, false);
    seed("gone", "ext-3", false, true);
    mvc.perform(get("/scim/v2/Users").header(AUTH, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemas[0]", is("urn:ietf:params:scim:api:messages:2.0:ListResponse")))
        .andExpect(jsonPath("$.totalResults", is(2)))
        .andExpect(jsonPath("$.Resources", hasSize(2)));
  }

  @Test
  @DisplayName("Filter by userName eq and externalId eq resolve exactly one user; no match is empty")
  void filter() throws Exception {
    seed("alice", "ext-1", true, false);
    seed("bob", "ext-2", true, false);
    mvc.perform(get("/scim/v2/Users").param("filter", "userName eq \"alice\"").header(AUTH, TOKEN))
        .andExpect(jsonPath("$.totalResults", is(1)))
        .andExpect(jsonPath("$.Resources[0].userName", is("alice")));
    mvc.perform(get("/scim/v2/Users").param("filter", "externalId eq \"ext-2\"").header(AUTH, TOKEN))
        .andExpect(jsonPath("$.totalResults", is(1)))
        .andExpect(jsonPath("$.Resources[0].userName", is("bob")));
    mvc.perform(get("/scim/v2/Users").param("filter", "userName eq \"ghost\"").header(AUTH, TOKEN))
        .andExpect(jsonPath("$.totalResults", is(0)))
        .andExpect(jsonPath("$.Resources", hasSize(0)));
  }

  @Test
  @DisplayName("Pagination honours startIndex and count")
  void pagination() throws Exception {
    for (int i = 1; i <= 5; i++) {
      seed("user" + i, "ext-" + i, true, false);
    }
    mvc.perform(get("/scim/v2/Users").param("startIndex", "3").param("count", "2").header(AUTH, TOKEN))
        .andExpect(jsonPath("$.totalResults", is(5)))
        .andExpect(jsonPath("$.startIndex", is(3)))
        .andExpect(jsonPath("$.itemsPerPage", is(2)))
        .andExpect(jsonPath("$.Resources", hasSize(2)));
  }

  // ---- Replace (PUT) --------------------------------------------------------------------------

  @Test
  @DisplayName("PUT replaces mutable fields, does not write sub, and 404s on unknown id")
  void putReplace() throws Exception {
    final UUID id = seed("alice", "ext-1", true, false);
    mvc.perform(
            put("/scim/v2/Users/" + id)
                .header(AUTH, TOKEN)
                .contentType(SCIM)
                .content(
                    "{\"userName\":\"alice\",\"externalId\":\"ext-1\","
                        + "\"displayName\":\"Alice Changed\","
                        + "\"emails\":[{\"value\":\"changed@example.com\",\"primary\":true}],"
                        + "\"active\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName", is("Alice Changed")));
    final UserEntity row = userRepository.findById(id).orElseThrow();
    assertThat(row.getName()).isEqualTo("Alice Changed");
    assertThat(row.getEmail()).isEqualTo("changed@example.com");
    assertThat(row.getSub()).isNull();

    mvc.perform(
            put("/scim/v2/Users/" + UUID.randomUUID())
                .header(AUTH, TOKEN)
                .contentType(SCIM)
                .content(user("ghost", "ext-9")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PUT active:false deactivates; PUT active:true on a soft-deleted user undeletes it")
  void putDeactivateAndUndelete() throws Exception {
    final UUID id = seed("alice", "ext-1", true, false);
    mvc.perform(
            put("/scim/v2/Users/" + id).header(AUTH, TOKEN).contentType(SCIM)
                .content("{\"userName\":\"alice\",\"active\":false}"))
        .andExpect(status().isOk());
    assertThat(userRepository.findById(id).orElseThrow().isActive()).isFalse();

    final UUID deleted = seed("bob", "ext-2", false, true);
    mvc.perform(
            put("/scim/v2/Users/" + deleted).header(AUTH, TOKEN).contentType(SCIM)
                .content("{\"userName\":\"bob\",\"active\":true}"))
        .andExpect(status().isOk());
    final UserEntity revived = userRepository.findById(deleted).orElseThrow();
    assertThat(revived.isDeleted()).isFalse();
    assertThat(revived.getDeletedAt()).isNull();
    assertThat(revived.isActive()).isTrue();
  }

  // ---- Delete (soft) --------------------------------------------------------------------------

  @Test
  @DisplayName("DELETE soft-deletes, distinguishes from deactivation, and 404s on unknown id")
  void softDelete() throws Exception {
    final UUID deactivated = seed("a", "ext-a", false, false); // PUT active:false earlier (deactivated)
    final UUID id = seed("b", "ext-b", true, false);
    mvc.perform(delete("/scim/v2/Users/" + id).header(AUTH, TOKEN))
        .andExpect(status().isNoContent());
    final UserEntity row = userRepository.findById(id).orElseThrow();
    assertThat(row.isActive()).isFalse();
    assertThat(row.isDeleted()).isTrue();
    assertThat(row.getDeletedAt()).isNotNull();
    // deactivated (not deleted) is distinguishable
    final UserEntity other = userRepository.findById(deactivated).orElseThrow();
    assertThat(other.isDeleted()).isFalse();
    assertThat(other.getDeletedAt()).isNull();

    mvc.perform(delete("/scim/v2/Users/" + UUID.randomUUID()).header(AUTH, TOKEN))
        .andExpect(status().isNotFound());
  }

  // ---- Audit ----------------------------------------------------------------------------------

  @Test
  @DisplayName("SCIM writes are audited against the SCIM service principal with the right action")
  void auditAttribution() throws Exception {
    final UUID id =
        UUID.fromString(
            lastPathSegment(
                mvc.perform(
                        post("/scim/v2/Users").header(AUTH, TOKEN).contentType(SCIM).content(alice()))
                    .andReturn()
                    .getResponse()
                    .getHeader("Location")));
    mvc.perform(delete("/scim/v2/Users/" + id).header(AUTH, TOKEN)).andExpect(status().isNoContent());

    final List<String> actions =
        jdbcTemplate.queryForList(
            "select action from oe_spring_services.audit_log where user_id = ? order by created_at",
            String.class,
            ScimServicePrincipal.ID);
    assertThat(actions).containsExactly("INSERT", "DELETE");
  }

  // ---- Group stub -----------------------------------------------------------------------------

  @Test
  @DisplayName("Group stub: GET returns an empty ListResponse; writes return 501")
  void groupStub() throws Exception {
    mvc.perform(get("/scim/v2/Groups").header(AUTH, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalResults", is(0)));
    mvc.perform(post("/scim/v2/Groups").header(AUTH, TOKEN).contentType(SCIM).content("{}"))
        .andExpect(status().isNotImplemented());
  }

  // ---- helpers --------------------------------------------------------------------------------

  private UUID seed(
      final String userName, final String externalId, final boolean active, final boolean deleted) {
    final UserEntity e = new UserEntity();
    e.setUserName(userName);
    e.setExternalId(externalId);
    e.setName(userName);
    e.setActive(active);
    e.setDeleted(deleted);
    if (deleted) {
      e.setDeletedAt(java.time.Instant.now());
    }
    return userRepository.save(e).id();
  }

  private static String alice() {
    return "{\"userName\":\"alice\",\"externalId\":\"ext-1\",\"displayName\":\"Alice\","
        + "\"emails\":[{\"value\":\"alice@example.com\",\"primary\":true}],\"active\":true}";
  }

  private static String user(final String userName, final String externalId) {
    return "{\"userName\":\"" + userName + "\",\"externalId\":\"" + externalId + "\",\"active\":true}";
  }

  private static String lastPathSegment(final String path) {
    return path.substring(path.lastIndexOf('/') + 1);
  }
}
