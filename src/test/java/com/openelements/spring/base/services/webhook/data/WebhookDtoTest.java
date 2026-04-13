package com.openelements.spring.base.services.webhook.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebhookDto")
class WebhookDtoTest {

    @Test
    void shouldConvertFromEntity() {
        //GIVEN
        final UUID id = UUID.randomUUID();
        final Instant lastCalledAt = Instant.now();
        final WebhookEntity entity = new WebhookEntity();
        entity.setId(id);
        entity.setUrl("https://example.com/hook");
        entity.setActive(true);
        entity.setLastStatus(200);
        entity.setLastCalledAt(lastCalledAt);

        //WHEN
        final WebhookDto dto = WebhookDto.fromEntity(entity);

        //THEN
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.url()).isEqualTo("https://example.com/hook");
        assertThat(dto.active()).isTrue();
        assertThat(dto.lastStatus()).isEqualTo(200);
        assertThat(dto.lastCalledAt()).isEqualTo(lastCalledAt);
    }

    @Test
    void shouldConvertInactiveWebhookWithNullStatus() {
        //GIVEN
        final WebhookEntity entity = new WebhookEntity();
        entity.setId(UUID.randomUUID());
        entity.setUrl("https://example.com/inactive");
        entity.setActive(false);

        //WHEN
        final WebhookDto dto = WebhookDto.fromEntity(entity);

        //THEN
        assertThat(dto.active()).isFalse();
        assertThat(dto.lastStatus()).isNull();
        assertThat(dto.lastCalledAt()).isNull();
    }
}