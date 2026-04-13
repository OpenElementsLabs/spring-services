package com.openelements.spring.base.services.apikey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiKeyDataService SHA-256 hashing")
class ApiKeyDataServiceSha256Test {

    @Test
    void shouldProduceCorrectSha256ForKnownInput() {
        //GIVEN
        final String input = "hello";

        //WHEN
        final String hash = ApiKeyDataService.sha256Hex(input);

        //THEN
        // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void shouldProduceConsistentHashForSameInput() {
        //GIVEN
        final String input = "crm_abc123";

        //WHEN
        final String hash1 = ApiKeyDataService.sha256Hex(input);
        final String hash2 = ApiKeyDataService.sha256Hex(input);

        //THEN
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void shouldProduceDifferentHashesForDifferentInputs() {
        //WHEN
        final String hash1 = ApiKeyDataService.sha256Hex("key1");
        final String hash2 = ApiKeyDataService.sha256Hex("key2");

        //THEN
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void shouldReturn64CharHexString() {
        //WHEN
        final String hash = ApiKeyDataService.sha256Hex("test");

        //THEN
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void shouldHandleEmptyString() {
        //WHEN
        final String hash = ApiKeyDataService.sha256Hex("");

        //THEN
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}