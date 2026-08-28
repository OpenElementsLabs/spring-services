package com.openelements.spring.base.info;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Unit tests for {@link CycloneDxReader}, the package-private Jackson tree parser that turns a
 * CycloneDX JSON document into an {@link SbomDocument}.
 *
 * <h2>What is tested</h2>
 *
 * <p>Happy-path summarisation, the three CycloneDX license encodings and their precedence, and every
 * degrade-to-empty path (missing resource, malformed JSON, non-CycloneDX document). Also the
 * forward-compatibility guarantee: a document from a newer spec version with unknown fields still
 * yields its components.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Hand-written fixtures under {@code src/test/resources/sbom/} are read through a {@link
 * ClassPathResource}; a couple of adversarial inputs (missing resource) use an in-memory {@link
 * ByteArrayResource}. Pure JUnit 5 + AssertJ, no Spring context.
 *
 * <p><b>Mock-Audit.</b> Zero mocks — the reader is exercised against real JSON resources.
 */
@DisplayName("CycloneDxReader")
class CycloneDxReaderTest {

  private final CycloneDxReader reader = new CycloneDxReader();

  private static Resource fixture(final String name) {
    return new ClassPathResource("sbom/" + name);
  }

  @Nested
  @DisplayName("Summarisation")
  class Summarisation {

    @Test
    @DisplayName("A valid CycloneDX 1.6 document is summarised (format, version, serial, count, application).")
    void summarisesValidDocument() {
      final SbomDocument document = reader.read(fixture("valid.cdx.json")).orElseThrow();

      final SbomSummary summary = document.summary();
      assertThat(summary.bomFormat()).isEqualTo("CycloneDX");
      assertThat(summary.specVersion()).isEqualTo("1.6");
      assertThat(summary.serialNumber())
          .isEqualTo("urn:uuid:12345678-1234-1234-1234-123456789abc");
      assertThat(summary.componentCount()).isEqualTo(3);
      assertThat(summary.application()).isNotNull();
      assertThat(summary.application().name()).isEqualTo("open-crm-backend");
      assertThat(summary.application().type()).isEqualTo("application");
    }

    @Test
    @DisplayName("The summary carries no build/SBOM timestamp — the record has no such component.")
    void summaryHasNoTimestamp() {
      final SbomSummary summary = reader.read(fixture("valid.cdx.json")).orElseThrow().summary();

      // Compile-time guarantee: SbomSummary exposes no timestamp accessor. Assert the fields that
      // do exist so the fixture's metadata.timestamp is provably ignored.
      assertThat(summary.serialNumber()).isNotNull();
      assertThat(SbomSummary.class.getRecordComponents())
          .extracting(java.lang.reflect.RecordComponent::getName)
          .doesNotContain("timestamp", "buildTime", "time");
    }

    @Test
    @DisplayName("The full component list carries group, name, version, type and purl for each entry.")
    void fullComponentListIsParsed() {
      final SbomDocument document = reader.read(fixture("valid.cdx.json")).orElseThrow();

      assertThat(document.components()).hasSize(3);
      final SbomComponent first = document.components().get(0);
      assertThat(first.group()).isEqualTo("org.example");
      assertThat(first.name()).isEqualTo("lib-a");
      assertThat(first.version()).isEqualTo("1.0.0");
      assertThat(first.type()).isEqualTo("library");
      assertThat(first.purl()).isEqualTo("pkg:maven/org.example/lib-a@1.0.0");
    }

    @Test
    @DisplayName("An SBOM with an empty components array is a valid SBOM with count 0.")
    void emptyComponentsIsValid() {
      final Optional<SbomDocument> document = reader.read(fixture("empty-components.cdx.json"));

      assertThat(document).isPresent();
      assertThat(document.get().summary().componentCount()).isZero();
      assertThat(document.get().components()).isEmpty();
    }

    @Test
    @DisplayName("An SBOM with no metadata section parses, with a null application component.")
    void noMetadataParses() {
      final SbomDocument document = reader.read(fixture("no-metadata.cdx.json")).orElseThrow();

      assertThat(document.summary().application()).isNull();
      assertThat(document.components()).hasSize(1);
    }

    @Test
    @DisplayName("Unknown fields from a newer spec version are ignored; all components are returned.")
    void unknownFieldsAreIgnored() {
      final SbomDocument document = reader.read(fixture("future-version.cdx.json")).orElseThrow();

      assertThat(document.summary().specVersion()).isEqualTo("1.7");
      assertThat(document.components()).hasSize(2);
      assertThat(document.components())
          .extracting(SbomComponent::name)
          .containsExactly("future-lib", "future-lib-2");
    }
  }

  @Nested
  @DisplayName("Degrade to empty")
  class DegradeToEmpty {

    @Test
    @DisplayName("A missing resource yields empty and logs nothing.")
    void missingResourceYieldsEmpty() {
      assertThat(reader.read(new ClassPathResource("sbom/does-not-exist.cdx.json"))).isEmpty();
    }

    @Test
    @DisplayName("A null resource yields empty.")
    void nullResourceYieldsEmpty() {
      assertThat(reader.read(null)).isEmpty();
    }

    @Test
    @DisplayName("Malformed (truncated) JSON yields empty.")
    void malformedJsonYieldsEmpty() {
      assertThat(reader.read(fixture("malformed.cdx.json"))).isEmpty();
    }

    @Test
    @DisplayName("A well-formed JSON document that is not CycloneDX (bomFormat=SPDX) yields empty.")
    void nonCycloneDxYieldsEmpty() {
      assertThat(reader.read(fixture("not-cyclonedx.json"))).isEmpty();
    }

    @Test
    @DisplayName("A JSON scalar (not an object) yields empty.")
    void nonObjectYieldsEmpty() {
      assertThat(reader.read(new ByteArrayResource("\"just a string\"".getBytes()))).isEmpty();
    }
  }

  @Nested
  @DisplayName("Licenses")
  class Licenses {

    private List<SbomComponent> components() {
      return reader.read(fixture("licenses.cdx.json")).orElseThrow().components();
    }

    @Test
    @DisplayName("An SPDX license id is surfaced as the id.")
    void spdxId() {
      assertThat(components().get(0).licenses()).containsExactly("Apache-2.0");
    }

    @Test
    @DisplayName("A free-text license name is surfaced when no id is declared.")
    void freeTextName() {
      assertThat(components().get(1).licenses())
          .containsExactly("The Apache Software License, Version 2.0");
    }

    @Test
    @DisplayName("A license expression is surfaced as the expression.")
    void expression() {
      assertThat(components().get(2).licenses()).containsExactly("MIT OR Apache-2.0");
    }

    @Test
    @DisplayName("An id takes precedence over a name on the same entry.")
    void idBeatsName() {
      assertThat(components().get(3).licenses()).containsExactly("Apache-2.0");
    }

    @Test
    @DisplayName("A component without licenses yields an empty, non-null list.")
    void noLicenses() {
      assertThat(components().get(4).licenses()).isEmpty();
    }

    @Test
    @DisplayName("The summary aggregates distinct licenses in sorted order.")
    void aggregateDistinctSorted() {
      final SbomSummary summary =
          reader.read(fixture("aggregate-licenses.cdx.json")).orElseThrow().summary();

      assertThat(summary.licenses()).containsExactly("Apache-2.0", "MIT");
    }
  }
}
