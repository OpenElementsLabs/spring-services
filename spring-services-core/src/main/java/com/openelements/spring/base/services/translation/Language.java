package com.openelements.spring.base.services.translation;

/** Languages supported for translations, each with its English display name. */
public enum Language {
  /** English. */
  EN("English"),
  /** German. */
  DE("German");

  private final String langName;

  Language(String langName) {
    this.langName = langName;
  }

  /**
   * Returns the English display name of this language.
   *
   * @return the display name of the language
   */
  public String getLangName() {
    return langName;
  }
}
