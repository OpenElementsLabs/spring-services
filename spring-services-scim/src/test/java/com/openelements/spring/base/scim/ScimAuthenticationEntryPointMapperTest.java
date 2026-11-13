package com.openelements.spring.base.scim;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.scim.ScimAuthenticationEntryPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * {@link ScimAuthenticationEntryPoint} writes its {@code 401} body with the application's JSON
 * mapper.
 *
 * <p>Since Spring Boot 4 that mapper is a Jackson 3 {@code JsonMapper}; the entry point must
 * receive Boot's bean — including the application's customisations — rather than a mapper of its own. A
 * customisation that is visible in every body ({@code indent-output}) proves which mapper wrote it.
 *
 * <p><b>Mock-Audit.</b> No mocks; Spring's servlet test doubles stand in for the container.
 */
@DisplayName("ScimAuthenticationEntryPoint — JSON mapper")
class ScimAuthenticationEntryPointMapperTest {

  @Test
  @DisplayName("Serialises the SCIM error with Boot's customised JSON mapper")
  void usesBootsMapper() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withBean(ScimAuthenticationEntryPoint.class)
        .withPropertyValues("spring.jackson.serialization.indent-output=true")
        .run(
            context -> {
              final MockHttpServletResponse response = new MockHttpServletResponse();
              context
                  .getBean(ScimAuthenticationEntryPoint.class)
                  .commence(
                      new MockHttpServletRequest(),
                      response,
                      new BadCredentialsException("no token"));
              assertThat(response.getContentAsString())
                  .as("indent-output is set on Boot's mapper only")
                  .contains("\n")
                  .contains("Authentication required");
            });
  }
}
