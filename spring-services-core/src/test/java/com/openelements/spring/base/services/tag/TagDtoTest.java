package com.openelements.spring.base.services.tag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link TagDto} record's {@code fromEntity(...)} mapper.
 *
 * <h2>What is tested</h2>
 *
 * <p>That every field on a populated {@link TagEntity} ({@code id}, {@code name}, {@code
 * description}, {@code color}) is copied 1:1 into the immutable {@link TagDto} record. This pins
 * the contract between persistence layer and API/DTO layer — a silent rename or omitted field
 * would corrupt the JSON payload sent to consumers.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 — instantiate a {@link TagEntity}, run the static mapper, assert each
 * accessor.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. The entity is a POJO with public setters and the mapper has
 * no collaborators; nothing to stub.
 */
@DisplayName("TagDto")
class TagDtoTest {

  @Test
  @DisplayName("TagDto.fromEntity(...) copies id, name, description and color 1:1 from the TagEntity.")
  void shouldConvertFromEntity() {
    // GIVEN
    final UUID id = UUID.randomUUID();
    final TagEntity entity = new TagEntity();
    entity.setId(id);
    entity.setName("Important");
    entity.setDescription("High priority items");
    entity.setColor("#FF0000");

    // WHEN
    final TagDto dto = TagDto.fromEntity(entity);

    // THEN
    assertThat(dto.id()).isEqualTo(id);
    assertThat(dto.name()).isEqualTo("Important");
    assertThat(dto.description()).isEqualTo("High priority items");
    assertThat(dto.color()).isEqualTo("#FF0000");
  }
}
