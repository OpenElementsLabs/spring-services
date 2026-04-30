package com.openelements.spring.base.services.translation;

import com.openelements.spring.base.data.AbstractEntity;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "translation", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"type", "id_by_type"})
})
public class TranslationEntity extends AbstractEntity {

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "id_by_type", nullable = false)
    private String idByType;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false)
    private Language language;

    @Column(name = "original", nullable = false, columnDefinition = "TEXT")
    private String original;

    @Column(name = "translation", columnDefinition = "TEXT")
    private String translation;

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    public String getIdByType() {
        return idByType;
    }

    public void setIdByType(final String idByType) {
        this.idByType = Objects.requireNonNull(idByType, "idByType must not be null");
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(final Language language) {
        this.language = Objects.requireNonNull(language, "language must not be null");
    }

    public String getOriginal() {
        return original;
    }

    public void setOriginal(final String original) {
        this.original = Objects.requireNonNull(original, "original must not be null");
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(final String translation) {
        this.translation = translation;
    }
}
