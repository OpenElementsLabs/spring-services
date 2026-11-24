package com.openelements.spring.base.services.slack.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.slack.events.MutableClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * {@link JdbcSlackUserTokenStore} against a real Postgres, with the table created from the same SQL
 * the upgrade guide ships. No Spring context: the store gets a {@link JdbcClient} on a plain data
 * source.
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("JdbcSlackUserTokenStore")
class JdbcSlackUserTokenStoreTest {

  private static final String TOKEN = "xoxp-1234-5678-abcdef";

  @Container
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static JdbcClient jdbc;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-24T10:00:00Z"));

  private final JdbcSlackUserTokenStore store =
      new JdbcSlackUserTokenStore(jdbc, new SlackTokenCipher(SlackTokenCipherTest.key(1)), clock);

  @BeforeAll
  static void createTable() {
    final PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUser(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    new ResourceDatabasePopulator(new ClassPathResource("db/slack-user-tokens.sql"))
        .execute(dataSource);
    jdbc = JdbcClient.create(dataSource);
  }

  @BeforeEach
  void clearTable() {
    jdbc.sql("DELETE FROM " + JdbcSlackUserTokenStore.TABLE).update();
  }

  @Test
  @DisplayName("Finds a saved token for its workspace and user only")
  void roundTrip() {
    store.save("T1", "U1", TOKEN);

    assertThat(store.find("T1", "U1")).contains(TOKEN);
    assertThat(store.find("T1", "U2")).isEmpty();
    assertThat(store.find("T2", "U1")).isEmpty();
  }

  @Test
  @DisplayName("Stores the token encrypted, never in plain text")
  void encryptedAtRest() {
    store.save("T1", "U1", TOKEN);

    final byte[] stored = encryptedToken("U1");
    assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(TOKEN);
  }

  @Test
  @DisplayName("Saving again replaces the token and keeps created_at")
  void upsert() {
    store.save("T1", "U1", TOKEN);
    clock.advance(Duration.ofHours(1));

    store.save("T1", "U1", "xoxp-new");

    assertThat(store.find("T1", "U1")).contains("xoxp-new");
    assertThat(count()).isEqualTo(1);
    final OffsetDateTime createdAt =
        jdbc.sql("SELECT created_at FROM " + JdbcSlackUserTokenStore.TABLE)
            .query(OffsetDateTime.class)
            .single();
    final OffsetDateTime updatedAt =
        jdbc.sql("SELECT updated_at FROM " + JdbcSlackUserTokenStore.TABLE)
            .query(OffsetDateTime.class)
            .single();
    assertThat(createdAt.toInstant()).isEqualTo(Instant.parse("2026-11-24T10:00:00Z"));
    assertThat(updatedAt.toInstant()).isEqualTo(Instant.parse("2026-11-24T11:00:00Z"));
  }

  @Test
  @DisplayName("Deletes a token; deleting a missing token does nothing")
  void delete() {
    store.save("T1", "U1", TOKEN);

    store.delete("T1", "U1");
    store.delete("T1", "U1");

    assertThat(store.find("T1", "U1")).isEmpty();
  }

  @Test
  @DisplayName("Removes a row whose ciphertext was copied from another user")
  void copiedCiphertext(final CapturedOutput output) {
    store.save("T1", "U1", TOKEN);
    store.save("T1", "U2", "xoxp-other");
    jdbc.sql(
            "UPDATE "
                + JdbcSlackUserTokenStore.TABLE
                + " SET encrypted_token = :token WHERE user_id = 'U2'")
        .param("token", encryptedToken("U1"))
        .update();

    assertThat(store.find("T1", "U2")).isEmpty();
    assertThat(count()).isEqualTo(1);
    assertThat(output).contains("user U2 in workspace T1").doesNotContain(TOKEN);
  }

  @Test
  @DisplayName("Removes a row that was encrypted with another key")
  void otherKey(final CapturedOutput output) {
    store.save("T1", "U1", TOKEN);
    final JdbcSlackUserTokenStore otherKey =
        new JdbcSlackUserTokenStore(
            jdbc, new SlackTokenCipher(SlackTokenCipherTest.key(2)), clock);

    assertThat(otherKey.find("T1", "U1")).isEmpty();
    assertThat(count()).isZero();
    assertThat(output).contains("cannot be decrypted").doesNotContain(TOKEN);
  }

  private static byte[] encryptedToken(final String userId) {
    return jdbc.sql(
            "SELECT encrypted_token FROM " + JdbcSlackUserTokenStore.TABLE + " WHERE user_id = :u")
        .param("u", userId)
        .query(byte[].class)
        .single();
  }

  private static long count() {
    return jdbc.sql("SELECT count(*) FROM " + JdbcSlackUserTokenStore.TABLE)
        .query(Long.class)
        .single();
  }
}
