package com.openelements.spring.base.services.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link ApiKeyDto#fromEntity(ApiKeyEntity)} mapping helper.
 *
 * <h2>What is tested</h2>
 *
 * <p>The entity-to-DTO projection: {@code id}, {@code name}, the display-only {@code keyPrefix},
 * and {@code createdBy} are copied across one-for-one. The mapping deliberately omits the
 * {@code keyHash} field — confirming it would have been a leak from the trust boundary into a
 * DTO that is returned by REST endpoints.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 with AssertJ. The test constructs an {@link ApiKeyEntity} in memory, invokes
 * the static factory, and asserts each field on the resulting DTO.
 *
 * <p><b>Mock-Audit.</b> Zero mocks.
 */
@DisplayName("ApiKeyDto")
class ApiKeyDtoTest {

  @Test
  @DisplayName(
      "fromEntity(ApiKeyEntity) copies id, name, keyPrefix and createdBy — keyHash is deliberately not exposed on the DTO.")
  void shouldConvertFromEntity() {
    // GIVEN
    final UUID id = UUID.randomUUID();
    final ApiKeyEntity entity = new ApiKeyEntity();
    entity.setId(id);
    entity.setName("CI Key");
    entity.setKeyPrefix("crm_a1B2...w3X4");
    entity.setKeyHash("somehash");
    entity.setCreatedBy("admin");

    // WHEN
    final ApiKeyDto dto = ApiKeyDto.fromEntity(entity);

    // THEN
    assertThat(dto.id()).isEqualTo(id);
    assertThat(dto.name()).isEqualTo("CI Key");
    assertThat(dto.keyPrefix()).isEqualTo("crm_a1B2...w3X4");
    assertThat(dto.createdBy()).isEqualTo("admin");
  }
}
