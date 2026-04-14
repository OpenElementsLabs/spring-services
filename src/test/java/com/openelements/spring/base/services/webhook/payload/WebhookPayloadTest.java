package com.openelements.spring.base.services.webhook.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.services.webhook.WebhookDataEventType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WebhookPayloadTest {

  private record TestData(UUID id, String value) implements WithId {}

  @Nested
  @DisplayName("WebhookDataEventPayload")
  class DataEventPayloadTest {

    @Test
    void shouldCreatePayloadWithCorrectFields() {
      // GIVEN
      final TestData data = new TestData(UUID.randomUUID(), "test");

      // WHEN
      final WebhookDataEventPayload<TestData> payload =
          WebhookDataEventPayload.of(WebhookDataEventType.CREATED, TestData.class, data);

      // THEN
      assertThat(payload.eventId()).isNotNull();
      assertThat(payload.eventType()).isEqualTo(WebhookDataEventType.CREATED);
      assertThat(payload.dataClass()).isEqualTo(TestData.class);
      assertThat(payload.data()).isSameAs(data);
      assertThat(payload.timestamp()).isNotNull();
    }

    @Test
    void shouldCreatePayloadWithInferredType() {
      // GIVEN
      final TestData data = new TestData(UUID.randomUUID(), "inferred");

      // WHEN
      final WebhookDataEventPayload<TestData> payload =
          WebhookDataEventPayload.of(WebhookDataEventType.UPDATED, data);

      // THEN
      assertThat(payload.dataClass()).isEqualTo(TestData.class);
      assertThat(payload.eventType()).isEqualTo(WebhookDataEventType.UPDATED);
    }

    @Test
    void shouldRejectNullData() {
      assertThatThrownBy(() -> WebhookDataEventPayload.of(WebhookDataEventType.CREATED, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullEventType() {
      final TestData data = new TestData(UUID.randomUUID(), "test");
      assertThatThrownBy(() -> WebhookDataEventPayload.of(null, data))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("WebhookPingEventPayload")
  class PingPayloadTest {

    @Test
    void shouldCreatePingWithUniqueIdAndTimestamp() {
      // WHEN
      final WebhookEventPayload ping1 = WebhookPingEventPayload.create();
      final WebhookEventPayload ping2 = WebhookPingEventPayload.create();

      // THEN
      assertThat(ping1.eventId()).isNotNull();
      assertThat(ping1.timestamp()).isNotNull();
      assertThat(ping1.eventId()).isNotEqualTo(ping2.eventId());
    }
  }
}
