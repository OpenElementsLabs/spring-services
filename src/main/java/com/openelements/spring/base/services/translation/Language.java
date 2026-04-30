package com.openelements.spring.base.services.translation;

public enum Language {
    EN("English"), DE("German");

    private final String langName;

    Language(String langName) {
        this.langName = langName;
    }

    public String getLangName() {
        return langName;
    }
}
