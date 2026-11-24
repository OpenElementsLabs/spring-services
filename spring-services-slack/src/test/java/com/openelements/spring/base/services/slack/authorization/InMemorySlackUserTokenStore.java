package com.openelements.spring.base.services.slack.authorization;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps tokens in a map, for tests that are not about the storage itself. */
public final class InMemorySlackUserTokenStore implements SlackUserTokenStore {

  private final Map<String, String> tokens = new ConcurrentHashMap<>();

  @Override
  public Optional<String> find(final String teamId, final String userId) {
    return Optional.ofNullable(tokens.get(key(teamId, userId)));
  }

  @Override
  public void save(final String teamId, final String userId, final String token) {
    tokens.put(key(teamId, userId), token);
  }

  @Override
  public void delete(final String teamId, final String userId) {
    tokens.remove(key(teamId, userId));
  }

  /**
   * Returns the number of stored tokens.
   *
   * @return the count
   */
  public int size() {
    return tokens.size();
  }

  private static String key(final String teamId, final String userId) {
    return teamId + '/' + userId;
  }
}
