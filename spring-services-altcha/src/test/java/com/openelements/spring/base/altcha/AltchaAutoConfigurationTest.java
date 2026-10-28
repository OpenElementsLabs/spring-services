package com.openelements.spring.base.altcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.altcha.AltchaChallengeController;
import com.openelements.spring.base.services.altcha.AltchaExceptionHandler;
import com.openelements.spring.base.services.altcha.AltchaProperties;
import com.openelements.spring.base.services.altcha.AltchaService;
import com.openelements.spring.base.services.altcha.AltchaVerifier;
import com.openelements.spring.base.services.altcha.InMemoryReplayStore;
import com.openelements.spring.base.services.altcha.ReplayStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * Activation tests for {@link AltchaAutoConfiguration}.
 *
 * <p>The module is opt-in, so the interesting behaviour is that nothing happens without
 * {@code openelements.altcha.enabled=true} — in particular no start-up failure for a missing secret —
 * and that an enabled module without a secret refuses to start.
 */
@DisplayName("AltchaAutoConfiguration")
class AltchaAutoConfigurationTest {

    private static final String KEY = "auto-configuration-test-key-with-32-characters";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AltchaAutoConfiguration.class));

    private final WebApplicationContextRunner enabled = runner.withPropertyValues(
            "openelements.altcha.enabled=true", "openelements.altcha.hmac-key=" + KEY);

    @Test
    @DisplayName("Registers nothing and needs no secret while openelements.altcha.enabled is unset")
    void inertByDefault() {
        runner.run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(AltchaService.class)
                .doesNotHaveBean(AltchaChallengeController.class)
                .doesNotHaveBean(AltchaExceptionHandler.class));
    }

    @Test
    @DisplayName("Registers service, endpoint and error handler with defaults when enabled")
    void activatesWhenEnabled() {
        enabled.run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(AltchaVerifier.class)
                    .hasSingleBean(AltchaService.class)
                    .hasSingleBean(AltchaChallengeController.class)
                    .hasSingleBean(AltchaExceptionHandler.class);
            assertThat(context.getBean(ReplayStore.class)).isInstanceOf(InMemoryReplayStore.class);
            final AltchaProperties properties = context.getBean(AltchaProperties.class);
            assertThat(properties.challengePath()).isEqualTo(AltchaProperties.DEFAULT_CHALLENGE_PATH);
            assertThat(properties.expires()).hasMinutes(10);
        });
    }

    @Test
    @DisplayName("Fails start-up when enabled without openelements.altcha.hmac-key")
    void doesNotStartWithoutHmacKey() {
        runner.withPropertyValues("openelements.altcha.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("openelements.altcha.hmac-key"));
    }

    @Test
    @DisplayName("Backs off for a ReplayStore of the application")
    void usesAReplayStoreOfTheApplication() {
        final ReplayStore shared = (signature, expiresAt) -> true;
        enabled.withBean(ReplayStore.class, () -> shared).run(context ->
                assertThat(context.getBean(ReplayStore.class)).isSameAs(shared));
    }

    @Test
    @DisplayName("Registers only the service outside a servlet web application")
    void noWebPartOutsideAWebApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AltchaAutoConfiguration.class))
                .withPropertyValues("openelements.altcha.enabled=true", "openelements.altcha.hmac-key=" + KEY)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(AltchaService.class)
                        .doesNotHaveBean(AltchaChallengeController.class)
                        .doesNotHaveBean(AltchaExceptionHandler.class));
    }
}
