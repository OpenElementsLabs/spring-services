package com.openelements.spring.base.altcha;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openelements.spring.base.services.altcha.AltchaService;
import com.openelements.spring.base.services.altcha.AltchaSolver;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
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
 */
@DisplayName("ALTCHA over HTTP")
class AltchaWebTest {

    private static final String CHALLENGE = "/api/altcha/challenge";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AltchaAutoConfiguration.class, SecurityAutoConfiguration.class,
                    WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                    JacksonAutoConfiguration.class))
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
                    .andExpect(jsonPath("$.code").value("altcha-reused"));
        });
    }

    @Test
    @DisplayName("Answers a form without a solution with 400, a stable code and a message in the request language")
    void rejectsAFormWithoutSolution() {
        run(mvc -> {
            mvc.perform(post("/api/form").header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("altcha-missing"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.startsWith("Die Prüfung gegen automatisierte Anfragen fehlt.")));
            mvc.perform(post("/api/form").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.startsWith("The check against automated requests is missing.")));
        });
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
