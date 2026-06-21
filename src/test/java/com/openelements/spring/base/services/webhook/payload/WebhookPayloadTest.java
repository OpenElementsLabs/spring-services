package com.openelements.spring.base.services.webhook.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.services.webhook.WebhookDataEventType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the immutable {@link WebhookDataEventPayload} and {@link
 * WebhookPingEventPayload} records — the JSON shapes serialised onto outgoing webhook HTTP POST
 * bodies.
 *
 * <h2>What is tested</h2>
 *
 * <p>Three properties of the payload factories:
 *
 * <ol>
 *   <li><b>Explicit class form.</b> {@code WebhookDataEventPayload.of(eventType, dataClass, data)}
 *       generates a fresh {@code eventId}, captures a {@code timestamp}, and stores the supplied
 *       {@code eventType}, {@code dataClass} and {@code data} unmodified.
 *   <li><b>Inferred-class form.</b> {@code of(eventType, data)} derives {@code dataClass} from
 *       {@code data.getClass()} — used by listeners that do not have a class reference handy.
 *   <li><b>Constructor null-guard.</b> The compact record constructor rejects {@code null}
 *       {@code data} and {@code null} {@code eventType} so a malformed payload never reaches the
 *       wire.
 *   <li><b>Ping payload.</b> Each {@code WebhookPingEventPayload.create()} yields a distinct
 *       {@code eventId} so retries and idempotency keys remain meaningful.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5, no Spring context. AssertJ assertions on the record accessors.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. The payloads are pure records with no collaborators.
 */
@DisplayName("Webhook payloads")
class WebhookPayloadTest {

  private record TestData(UUID id, String value) implements WithId {}

  @Nested
  @DisplayName("WebhookDataEventPayload")
  class DataEventPayloadTest {

    @Test
    @DisplayName("of(eventType, dataClass, data) populates eventId, eventType, dataClass, data and a non-null timestamp.")
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
    @DisplayName("of(eventType, data) without an explicit class infers dataClass from data.getClass().")
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
    @DisplayName("of(eventType, null) throws NullPointerException — a payload with null data must not reach the wire.")
    void shouldRejectNullData() {
      assertThatThrownBy(() -> WebhookDataEventPayload.of(WebhookDataEventType.CREATED, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(null, data) throws NullPointerException — a payload with null eventType must not reach the wire.")
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
    @DisplayName("Each WebhookPingEventPayload.create() invocation yields a distinct eventId and a non-null timestamp.")
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
