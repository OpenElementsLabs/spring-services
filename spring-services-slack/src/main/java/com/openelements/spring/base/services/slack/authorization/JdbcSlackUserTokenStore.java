package com.openelements.spring.base.services.slack.authorization;

import com.openelements.spring.base.data.DbSchema;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Stores Slack user tokens encrypted in the table {@code oe_spring_services.slack_user_tokens}.
 *
 * <p>Plain JDBC rather than a JPA entity: the library keeps every entity in {@code
 * spring-services-core}, and an optional module owns none. The table is created by the
 * application's migrations; the SQL is part of the upgrade guide.
 *
 * <p>A row that cannot be decrypted — a changed key or a tampered row — is treated as missing and
 * deleted, so the user is asked to authorize again.
 */
public final class JdbcSlackUserTokenStore implements SlackUserTokenStore {

  /** The table, qualified with the library schema. */
  public static final String TABLE = DbSchema.NAME + ".slack_user_tokens";

  private static final Logger LOG = LoggerFactory.getLogger(JdbcSlackUserTokenStore.class);

  private final JdbcClient jdbc;

  private final SlackTokenCipher cipher;

  private final Clock clock;

  /**
   * Creates the store.
   *
   * @param jdbc the database access
   * @param cipher encrypts and decrypts the tokens
   * @param clock the source of {@code created_at} and {@code updated_at}
   */
  public JdbcSlackUserTokenStore(
      final JdbcClient jdbc, final SlackTokenCipher cipher, final Clock clock) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    this.cipher = Objects.requireNonNull(cipher, "cipher must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public Optional<String> find(final String teamId, final String userId) {
    Objects.requireNonNull(teamId, "teamId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    final Optional<byte[]> encrypted =
        jdbc.sql(
                "SELECT encrypted_token FROM "
                    + TABLE
                    + " WHERE team_id = :teamId AND user_id = :userId")
            .param("teamId", teamId)
            .param("userId", userId)
            .query(byte[].class)
            .optional();
    if (encrypted.isEmpty()) {
      return Optional.empty();
    }
    final Optional<String> token = cipher.decrypt(encrypted.get(), teamId, userId);
    if (token.isEmpty()) {
      LOG.warn(
          "Deleted the Slack user token of user {} in workspace {}: it cannot be decrypted",
          userId,
          teamId);
      delete(teamId, userId);
    }
    return token;
  }

  @Override
  public void save(final String teamId, final String userId, final String token) {
    Objects.requireNonNull(teamId, "teamId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(token, "token must not be null");
    final OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    jdbc.sql(
            "INSERT INTO "
                + TABLE
                + " (team_id, user_id, encrypted_token, created_at, updated_at)"
                + " VALUES (:teamId, :userId, :encryptedToken, :now, :now)"
                + " ON CONFLICT (team_id, user_id)"
                + " DO UPDATE SET encrypted_token = EXCLUDED.encrypted_token,"
                + " updated_at = EXCLUDED.updated_at")
        .param("teamId", teamId)
        .param("userId", userId)
        .param("encryptedToken", cipher.encrypt(token, teamId, userId))
        .param("now", now)
        .update();
  }

  @Override
  public void delete(final String teamId, final String userId) {
    Objects.requireNonNull(teamId, "teamId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    jdbc.sql("DELETE FROM " + TABLE + " WHERE team_id = :teamId AND user_id = :userId")
        .param("teamId", teamId)
        .param("userId", userId)
        .update();
  }
}
