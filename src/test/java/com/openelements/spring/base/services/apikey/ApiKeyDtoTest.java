package com.openelements.spring.base.services.apikey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiKeyDto")
class ApiKeyDtoTest {

    @Test
    void shouldConvertFromEntity() {
        //GIVEN
        final UUID id = UUID.randomUUID();
        final ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(id);
        entity.setName("CI Key");
        entity.setKeyPrefix("crm_a1B2...w3X4");
        entity.setKeyHash("somehash");
        entity.setCreatedBy("admin");

        //WHEN
        final ApiKeyDto dto = ApiKeyDto.fromEntity(entity);

        //THEN
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.name()).isEqualTo("CI Key");
        assertThat(dto.keyPrefix()).isEqualTo("crm_a1B2...w3X4");
        assertThat(dto.createdBy()).isEqualTo("admin");
    }
}