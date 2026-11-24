package com.openelements.spring.base.services.slack.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.problem.ProblemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

@DisplayName("SlackEventsProblemType")
class SlackEventsProblemTypeTest {

  @ParameterizedTest(name = "[{index}] {0} → {1}")
  @CsvSource({
    "NOT_CONFIGURED, slack-events-not-configured, SERVICE_UNAVAILABLE",
    "TOO_LARGE, slack-event-too-large, CONTENT_TOO_LARGE",
    "SIGNATURE_INVALID, slack-signature-invalid, UNAUTHORIZED",
  })
  @DisplayName("Slug and status of each constant are frozen")
  void constantsAreFrozen(
      final SlackEventsProblemType type, final String slug, final HttpStatus status) {
    assertThat(type.slug()).isEqualTo(slug);
    assertThat(type.status()).isEqualTo(status);
  }

  @ParameterizedTest
  @EnumSource(SlackEventsProblemType.class)
  @DisplayName("Every constant satisfies the ProblemType contract")
  void everyConstantIsValid(final SlackEventsProblemType type) {
    ProblemType.validate(type);
  }

  @Test
  @DisplayName("The not-configured exception carries the not-configured problem type")
  void notConfiguredExceptionHasItsType() {
    assertThat(new SlackEventsNotConfiguredException().type())
        .isEqualTo(SlackEventsProblemType.NOT_CONFIGURED);
  }
}
