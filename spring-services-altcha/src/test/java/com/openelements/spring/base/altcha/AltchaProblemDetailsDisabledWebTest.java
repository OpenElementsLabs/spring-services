package com.openelements.spring.base.altcha;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openelements.spring.base.problem.ProblemDetailsAutoConfiguration;
import com.openelements.spring.base.trace.TraceAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The module's error response with {@code openelements.problem-details.enabled=false}.
 *
 * <h2>What is tested</h2>
 *
 * <p>That the escape hatch is a complete rollback for this module, not a partial one. An application
 * that cannot migrate its clients to {@code type} URIs sets the property and must get back exactly what
 * this module published before: status {@code 400}, {@code Content-Type: application/json}, and a body
 * of precisely {@code code} and {@code message} — no {@code type}, no {@code traceId}, nothing extra for
 * a strict client to trip over.
 *
 * <p>The localisation of {@code message} is asserted too, because that behaviour predates problem details
 * and must survive the switch untouched.
 *
 * <h2>How it is tested</h2>
 *
 * <p>The same {@link WebApplicationContextRunner} set-up as {@link AltchaWebTest}, including
 * {@link ProblemDetailsAutoConfiguration} — the property, not the absence of the auto-configuration, is
 * what has to disable the feature. The application fixture is reused from {@link AltchaWebTest} so the
 * two configurations are compared on identical ground.
 */
@DisplayName("ALTCHA over HTTP without problem details")
class AltchaProblemDetailsDisabledWebTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AltchaAutoConfiguration.class, SecurityAutoConfiguration.class,
                    WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                    JacksonAutoConfiguration.class, ProblemDetailsAutoConfiguration.class,
                    TraceAutoConfiguration.class))
            .withUserConfiguration(AltchaWebTest.Application.class)
            .withPropertyValues("openelements.altcha.enabled=true",
                    "openelements.altcha.hmac-key=web-test-altcha-key-with-at-least-32-characters",
                    "openelements.altcha.cost=1", "openelements.altcha.key-prefix-length=1",
                    "openelements.problem-details.enabled=false");

    @Test
    @DisplayName("Answers with the previous {code, message} body and nothing else")
    void answersWithThePreviousBody() {
        runner.run(context -> {
            final MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                    .apply(springSecurity()).build();

            mvc.perform(post("/api/form").header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(jsonPath("$.code").value("altcha-missing"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.startsWith("Die Prüfung gegen automatisierte Anfragen fehlt.")))
                    .andExpect(jsonPath("$.type").doesNotExist())
                    .andExpect(jsonPath("$.title").doesNotExist())
                    .andExpect(jsonPath("$.status").doesNotExist())
                    .andExpect(jsonPath("$.traceId").doesNotExist());
        });
    }

    @Test
    @DisplayName("Keeps the English message for a request in another language")
    void keepsTheEnglishFallback() {
        runner.run(context -> {
            final MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                    .apply(springSecurity()).build();

            mvc.perform(post("/api/form").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                    .andExpect(jsonPath("$.code").value("altcha-missing"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.startsWith("The check against automated requests is missing.")));
        });
    }
}
