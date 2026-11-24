package com.openelements.spring.base.services.slack.events;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/** Loads the Slack request bodies under {@code src/test/resources/slack-events/}. */
public final class SlackFixtures {

  private SlackFixtures() {}

  /**
   * Returns the raw bytes of a fixture.
   *
   * @param name the file name, e.g. {@code message.json}
   * @return the file content
   */
  public static byte[] load(final String name) {
    try (InputStream in =
        Objects.requireNonNull(
            SlackFixtures.class.getResourceAsStream("/slack-events/" + name),
            () -> "no fixture " + name)) {
      return in.readAllBytes();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
