package com.openelements.spring.base.services.tag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TagDto")
class TagDtoTest {

    @Test
    void shouldConvertFromEntity() {
        //GIVEN
        final UUID id = UUID.randomUUID();
        final TagEntity entity = new TagEntity();
        entity.setId(id);
        entity.setName("Important");
        entity.setDescription("High priority items");
        entity.setColor("#FF0000");

        //WHEN
        final TagDto dto = TagDto.fromEntity(entity);

        //THEN
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.name()).isEqualTo("Important");
        assertThat(dto.description()).isEqualTo("High priority items");
        assertThat(dto.color()).isEqualTo("#FF0000");
    }
}