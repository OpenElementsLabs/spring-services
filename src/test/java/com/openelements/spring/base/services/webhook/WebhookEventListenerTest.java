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

@DisplayName("WebhookEventListener")
class WebhookEventListenerTest {

  private record TestData(UUID id, String value) implements WithId {}

  private final WebhookSender webhookSender = mock(WebhookSender.class);
  private final WebhookEventListener listener = new WebhookEventListener(webhookSender);

  @Test
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
  void shouldRejectNullEvent() {
    assertThatThrownBy(() -> listener.handleOnObjectCreate(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectNullWebhookSender() {
    assertThatThrownBy(() -> new WebhookEventListener(null))
        .isInstanceOf(NullPointerException.class);
  }
}
