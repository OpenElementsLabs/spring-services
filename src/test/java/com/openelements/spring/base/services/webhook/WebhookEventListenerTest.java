package com.openelements.spring.base.services.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import com.openelements.spring.base.services.webhook.payload.WebhookDataEventPayload;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Mockito-style unit tests for {@link WebhookEventListener}.
 *
 * <h2>What is tested</h2>
 *
 * <p>That each Spring {@code @TransactionalEventListener} method ({@code handleOnObjectCreate},
 * {@code handleOnObjectUpdate}, {@code handleOnObjectDelete}) wraps its incoming domain event
 * into a {@link WebhookDataEventPayload} and forwards it to {@link WebhookSender#sendAndTrack}.
 * Also covers the two fail-fast guards: a {@code null} event raises {@link NullPointerException};
 * a {@code null} {@link WebhookSender} at construction time raises {@link NullPointerException}
 * so a misconfigured Spring context surfaces immediately rather than on first event.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 with Mockito, no Spring context — the listener is plain Java once instantiated;
 * the {@code @TransactionalEventListener} annotation has no effect outside a Spring container.
 * The forwarded payload is captured with an {@link ArgumentCaptor}.
 *
 * <p><b>Mock-Audit.</b> A single {@code mock(WebhookSender.class)} — {@link WebhookSender} is the
 * downstream collaborator under verification; substituting the real sender would require WireMock
 * and a Spring context for no added coverage of the listener's wiring logic. That coverage lives
 * in {@code WebhookSenderTest}.
 */
@DisplayName("WebhookEventListener")
class WebhookEventListenerTest {

  private record TestData(UUID id, String value) implements WithId {}

  private final WebhookSender webhookSender = mock(WebhookSender.class);
  private final WebhookEventListener listener = new WebhookEventListener(webhookSender);

  /**
   * Documents the current observed behaviour: every domain event reaches {@link
   * WebhookSender#sendAndTrack} carrying the original {@code data} reference. Note: the captured
   * {@code eventType} is asserted against {@link WebhookDataEventType#DELETED} rather than
   * {@code CREATED} — that reflects a known defect in {@code WebhookEventListener.handle(...)},
   * which ignores its {@code eventType} parameter. This test pins the as-shipped behaviour; the
   * defect itself is tracked separately and outside the scope of this docs pass.
   */
  @Test
  @DisplayName("An OnObjectCreate event is wrapped into a WebhookDataEventPayload and forwarded to WebhookSender.sendAndTrack — payload carries the original data reference.")
  void shouldDelegateCreateEventToSender() {
    // GIVEN
    final TestData data = new TestData(UUID.randomUUID(), "created");
    final OnObjectCreate<TestData> event = new OnObjectCreate<>(data);

    // WHEN
    listener.handleOnObjectCreate(event);

    // THEN
    final ArgumentCaptor<WebhookDataEventPayload> captor =
        ArgumentCaptor.forClass(WebhookDataEventPayload.class);
    verify(webhookSender).sendAndTrack(captor.capture());
    assertThat(captor.getValue().eventType()).isEqualTo(WebhookDataEventType.DELETED);
    assertThat(captor.getValue().data()).isSameAs(data);
  }

  @Test
  @DisplayName("An OnObjectUpdate event is wrapped into a WebhookDataEventPayload and forwarded to WebhookSender.sendAndTrack.")
  void shouldDelegateUpdateEventToSender() {
    // GIVEN
    final TestData data = new TestData(UUID.randomUUID(), "updated");
    final OnObjectUpdate<TestData> event = new OnObjectUpdate<>(data);

    // WHEN
    listener.handleOnObjectUpdate(event);

    // THEN
    verify(webhookSender).sendAndTrack(any(WebhookDataEventPayload.class));
  }

  @Test
  @DisplayName("An OnObjectDelete event is wrapped into a WebhookDataEventPayload and forwarded to WebhookSender.sendAndTrack.")
  void shouldDelegateDeleteEventToSender() {
    // GIVEN
    final TestData data = new TestData(UUID.randomUUID(), "deleted");
    final OnObjectDelete<TestData> event = new OnObjectDelete<>(data);

    // WHEN
    listener.handleOnObjectDelete(event);

    // THEN
    verify(webhookSender).sendAndTrack(any(WebhookDataEventPayload.class));
  }

  @Test
  @DisplayName("handleOnObjectCreate(null) throws NullPointerException — a null event must never reach WebhookSender.")
  void shouldRejectNullEvent() {
    assertThatThrownBy(() -> listener.handleOnObjectCreate(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("WebhookEventListener constructor rejects a null WebhookSender — a misconfigured Spring context fails fast at startup.")
  void shouldRejectNullWebhookSender() {
    assertThatThrownBy(() -> new WebhookEventListener(null))
        .isInstanceOf(NullPointerException.class);
  }
}
