package com.openelements.spring.base.services.webhook.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link WebhookDto} record's {@code fromEntity(...)} mapper.
 *
 * <h2>What is tested</h2>
 *
 * <p>Two scenarios for the entity-to-DTO conversion:
 *
 * <ol>
 *   <li>A fully populated {@link WebhookEntity} (with {@code lastStatus} and {@code lastCalledAt}
 *       set) is copied 1:1 — pins the API payload shape.
 *   <li>A never-called inactive webhook produces a DTO with {@code lastStatus = null} and
 *       {@code lastCalledAt = null} — confirms the nullable HTTP-tracking fields survive the
 *       mapping without being defaulted to {@code 0} or {@code Instant.EPOCH}.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 — instantiate a {@link WebhookEntity}, run the static mapper, assert each
 * accessor.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. The entity is a POJO with public setters and the mapper has
 * no collaborators; nothing to stub.
 */
@DisplayName("WebhookDto")
class WebhookDtoTest {

  @Test
  @DisplayName("WebhookDto.fromEntity(...) copies id, url, active, lastStatus and lastCalledAt 1:1 from the WebhookEntity.")
  void shouldConvertFromEntity() {
    // GIVEN
    final UUID id = UUID.randomUUID();
    final Instant lastCalledAt = Instant.now();
    final WebhookEntity entity = new WebhookEntity();
    entity.setId(id);
    entity.setUrl("https://example.com/hook");
    entity.setActive(true);
    entity.setLastStatus(200);
    entity.setLastCalledAt(lastCalledAt);

    // WHEN
    final WebhookDto dto = WebhookDto.fromEntity(entity);

    // THEN
    assertThat(dto.id()).isEqualTo(id);
    assertThat(dto.url()).isEqualTo("https://example.com/hook");
    assertThat(dto.active()).isTrue();
    assertThat(dto.lastStatus()).isEqualTo(200);
    assertThat(dto.lastCalledAt()).isEqualTo(lastCalledAt);
  }

  @Test
  @DisplayName("An inactive webhook with unset lastStatus/lastCalledAt maps to null fields — nullable tracking values are preserved.")
  void shouldConvertInactiveWebhookWithNullStatus() {
    // GIVEN
    final WebhookEntity entity = new WebhookEntity();
    entity.setId(UUID.randomUUID());
    entity.setUrl("https://example.com/inactive");
    entity.setActive(false);

    // WHEN
    final WebhookDto dto = WebhookDto.fromEntity(entity);

    // THEN
    assertThat(dto.active()).isFalse();
    assertThat(dto.lastStatus()).isNull();
    assertThat(dto.lastCalledAt()).isNull();
  }
}
