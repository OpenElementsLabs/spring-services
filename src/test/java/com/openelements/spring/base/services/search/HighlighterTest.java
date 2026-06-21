package com.openelements.spring.base.services.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Highlighter#safeHighlight(String)}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The XSS firewall between Meilisearch's {@code _formatted} output and a frontend that renders
 * the result via {@code dangerouslySetInnerHTML}. Specifically: HTML special characters in
 * user-typed text are escaped (angle brackets, ampersands, single and double quotes); the
 * private-use Unicode boundary markers Meilisearch wraps around matches are converted to literal
 * {@code <em>} / {@code </em>} tags <em>after</em> escaping, so a hostile match like {@code
 * <script>} round-trips as {@code <em>&lt;script&gt;</em>}; {@code null} input degrades to an
 * empty string; ordinary text passes through unchanged.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 unit tests against the static {@code Highlighter.safeHighlight(String)} method.
 * No Spring context, no I/O, no time-dependence.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. {@code Highlighter} is a pure function over a {@code String};
 * no collaborator exists to mock.
 */
class HighlighterTest {

  /**
   * Pins the order of operations: escaping must happen <em>before</em> the marker substitution.
   * If a future refactor flipped the order, a hostile match like {@code <script>} embedded inside
   * the markers would survive as raw HTML. The assertion both confirms the escaped form and
   * explicitly rejects the substring {@code <script>}.
   */
  @Test
  @DisplayName("Plain text without highlight markers is HTML-escaped — <script> never survives.")
  void plainTextWithoutMarkersIsHtmlEscaped() {
    final String input = "<script>alert(1)</script>";
    final String out = Highlighter.safeHighlight(input);
    assertFalse(out.contains("<script>"), "Raw <script> must not survive");
    assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", out);
  }

  /**
   * Verifies the round-trip contract: Meilisearch's PRE/POST markers around hostile user content
   * become {@code <em>} / {@code </em>} wrapping the escaped form, never the raw form.
   */
  @Test
  @DisplayName(
      "Highlight markers wrap escaped (not raw) user text — <script> inside markers becomes <em>&lt;script&gt;</em>.")
  void markersAroundEscapedTextProduceEmWrappedSafeHtml() {
    final String input = Highlighter.PRE_MARK + "<script>" + Highlighter.POST_MARK + " and friends";
    final String out = Highlighter.safeHighlight(input);
    assertEquals("<em>&lt;script&gt;</em> and friends", out);
  }

  @Test
  @DisplayName("safeHighlight(null) returns an empty string rather than throwing NullPointerException.")
  void nullInputReturnsEmptyString() {
    assertEquals("", Highlighter.safeHighlight(null));
  }

  @Test
  @DisplayName("Ampersands and both quote characters are escaped to their named/numeric entities.")
  void ampersandsAndQuotesAreEscaped() {
    assertEquals(
        "a &amp; b &quot;c&quot; &#39;d&#39;", Highlighter.safeHighlight("a & b \"c\" 'd'"));
  }

  @Test
  @DisplayName("Text without HTML-special characters or markers passes through verbatim.")
  void normalTextPassesThroughUnchanged() {
    assertEquals("Hello world", Highlighter.safeHighlight("Hello world"));
  }
}
