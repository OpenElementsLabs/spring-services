package com.openelements.spring.base.services.search;

/**
 * Turns Meilisearch's {@code _formatted} output into HTML-safe highlighted fragments. This is the
 * XSS firewall between Meilisearch and a frontend that renders the result via {@code
 * dangerouslySetInnerHTML}.
 *
 * <p>Meilisearch is configured to wrap matched fragments in the private-use Unicode {@link
 * #PRE_MARK} / {@link #POST_MARK} markers rather than literal {@code <em>} tags. {@link
 * #safeHighlight(String)} escapes the whole string first, then converts the markers to literal
 * {@code <em>} / {@code </em>} — so any user-typed HTML inside the match is escaped while the
 * highlight markup is not.
 */
public final class Highlighter {

  /**
   * Private-use Unicode marker that Meilisearch places before a matched fragment, detected inside
   * otherwise HTML-escaped text and later replaced with an opening {@code <em>} tag.
   */
  public static final String PRE_MARK = "";

  /**
   * Private-use Unicode marker that Meilisearch places after a matched fragment, detected inside
   * otherwise HTML-escaped text and later replaced with a closing {@code </em>} tag.
   */
  public static final String POST_MARK = "";

  private Highlighter() {}

  /**
   * Escapes HTML special characters in user-typed text, then turns the Meilisearch boundary markers
   * back into {@code <em>} / {@code </em>}. The result is safe to render with {@code
   * dangerouslySetInnerHTML}.
   *
   * @param raw the {@code _formatted} value from Meilisearch, containing the private-use highlight
   *     markers; may be {@code null}
   * @return an HTML-safe string with user content escaped and highlight markers converted to {@code
   *     <em>} tags, or an empty string when {@code raw} is {@code null}
   */
  public static String safeHighlight(final String raw) {
    if (raw == null) {
      return "";
    }
    final String escaped =
        raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    return escaped.replace(PRE_MARK, "<em>").replace(POST_MARK, "</em>");
  }
}
