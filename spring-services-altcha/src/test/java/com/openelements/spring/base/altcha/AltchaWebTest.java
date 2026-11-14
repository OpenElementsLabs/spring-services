package com.openelements.spring.base.altcha;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openelements.spring.base.problem.ProblemDetailsAutoConfiguration;
import com.openelements.spring.base.services.altcha.AltchaService;
import com.openelements.spring.base.services.altcha.AltchaSolver;
import com.openelements.spring.base.trace.TraceAutoConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * The module through HTTP, behind Spring Security.
 *
 * <p>The application side is a stand-in for spring-services' default chain — a chain without a
 * matcher that demands authentication for everything — plus one public form endpoint. That is the
 * situation the module's own chain exists for: without it the visitor of the form could not fetch a
 * challenge.
 *
 * <p>RFC 9457 problem details are active, as they are by default in a real application, so the failure
 * responses are problems. The slug a client branches on is unchanged from the {@code code} this module
 * published before — it is now the last segment of the {@code type} URI. The opposite configuration is
 * covered by {@link AltchaProblemDetailsDisabledWebTest}.
 */
@DisplayName("ALTCHA over HTTP")
class AltchaWebTest {

    private static final String CHALLENGE = "/api/altcha/challenge";

    /** The default {@code openelements.problem-details.type-base}. */
    static final String TYPE_BASE = "https://open-elements.com/problems/spring-services/";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AltchaAutoConfiguration.class, SecurityAutoConfiguration.class,
                    ServletWebSecurityAutoConfiguration.class,
                    WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                    JacksonAutoConfiguration.class, ProblemDetailsAutoConfiguration.class,
                    TraceAutoConfiguration.class))
            .withUserConfiguration(Application.class)
            .withPropertyValues("openelements.altcha.enabled=true",
                    "openelements.altcha.hmac-key=web-test-altcha-key-with-at-least-32-characters",
                    "openelements.altcha.cost=1", "openelements.altcha.key-prefix-length=1");

    @Test
    @DisplayName("Hands out challenges without authentication and never lets them be cached")
    void challengeIsPublic() {
        run(mvc -> mvc.perform(get(CHALLENGE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.signature").isNotEmpty()));
    }

    @Test
    @DisplayName("Opens only GET on the challenge path and nothing else")
    void opensNothingElse() {
        run(mvc -> {
            mvc.perform(post(CHALLENGE)).andExpect(status().isForbidden());
            mvc.perform(get("/api/anything")).andExpect(status().isForbidden());
        });
    }

    @Test
    @DisplayName("Accepts a form with a solved challenge once")
    void acceptsASolvedChallengeOnce() {
        run(mvc -> {
            final String challenge = mvc.perform(get(CHALLENGE)).andReturn().getResponse().getContentAsString();
            final String payload = AltchaSolver.solve(challenge);
            mvc.perform(post("/api/form").param("altcha", payload)).andExpect(status().isOk());
            mvc.perform(post("/api/form").param("altcha", payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType("application/problem+json"))
                    .andExpect(jsonPath("$.type").value(TYPE_BASE + "altcha-reused"))
                    .andExpect(jsonPath("$.title").value("Bot check reused"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").doesNotExist());
        });
    }

    /**
     * The title stays English because it describes the <em>type</em> and must not change from occurrence
     * to occurrence (RFC 9457 §3.1.2); the visitor-facing sentence is the {@code detail}, and that one
     * follows the request's language.
     */
    @Test
    @DisplayName("Answers a form without a solution with a 400 problem whose detail is in the request language")
    void rejectsAFormWithoutSolution() {
        run(mvc -> {
            mvc.perform(post("/api/form").header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType("application/problem+json"))
                    .andExpect(jsonPath("$.type").value(TYPE_BASE + "altcha-missing"))
                    .andExpect(jsonPath("$.title").value("Bot check missing"))
                    .andExpect(jsonPath("$.traceId").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{32}")))
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.startsWith("Die Prüfung gegen automatisierte Anfragen fehlt.")));
            mvc.perform(post("/api/form").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                    .andExpect(jsonPath("$.title").value("Bot check missing"))
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.startsWith("The check against automated requests is missing.")));
        });
    }

    @Test
    @DisplayName("Answers an unparseable solution with the altcha-invalid problem")
    void rejectsAnInvalidSolution() {
        run(mvc -> mvc.perform(post("/api/form").param("altcha", "not-a-payload"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + "altcha-invalid"))
                .andExpect(jsonPath("$.title").value("Bot check invalid")));
    }

    private void run(final MvcAssertion assertion) {
        runner.run(context -> {
            final MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                    .apply(springSecurity()).build();
            assertion.accept(mvc);
        });
    }

    @FunctionalInterface
    private interface MvcAssertion {
        void accept(MockMvc mvc) throws Exception;
    }

    @Configuration(proxyBeanMethods = false)
    static class Application {

        @Bean
        @Order(2)
        SecurityFilterChain defaultChain(final HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/form").permitAll()
                            .anyRequest().authenticated())
                    .csrf(csrf -> csrf.disable());
            return http.build();
        }

        @Bean
        FormController formController(final AltchaService altcha) {
            return new FormController(altcha);
        }
    }

    @RestController
    static class FormController {

        private final AltchaService altcha;

        FormController(final AltchaService altcha) {
            this.altcha = altcha;
        }

        @PostMapping("/api/form")
        String submit(@RequestParam(name = "altcha", required = false) final @Nullable String payload) {
            altcha.requireVerified(payload);
            return "ok";
        }
    }
}
