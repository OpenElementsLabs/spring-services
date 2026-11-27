package com.openelements.spring.base.services.slack.authorization;

import java.util.Optional;

/**
 * Stores the Slack user tokens that allow editing a user's messages, one per user and workspace.
 *
 * <p>A user token with the scope {@code chat:write} lets its holder post and edit as that user in
 * every conversation the user can write to. Implementations must therefore keep tokens encrypted or
 * in a secret store, never in plain text; {@link SlackTokenCipher} does the encryption. The default
 * implementation is {@link JdbcSlackUserTokenStore}; an application replaces it by declaring a bean
 * of this type.
 */
public interface SlackUserTokenStore {

  /**
   * Returns the token of a user.
   *
   * @param teamId the id of the Slack workspace
   * @param userId the id of the Slack user
   * @return the token, or empty if none is stored or it can no longer be read
   */
  Optional<String> find(String teamId, String userId);

  /**
   * Stores the token of a user, replacing an existing one.
   *
   * @param teamId the id of the Slack workspace
   * @param userId the id of the Slack user
   * @param token the user token
   */
  void save(String teamId, String userId, String token);

  /**
   * Removes the token of a user. Does nothing if none is stored.
   *
   * @param teamId the id of the Slack workspace
   * @param userId the id of the Slack user
   */
  void delete(String teamId, String userId);
}
