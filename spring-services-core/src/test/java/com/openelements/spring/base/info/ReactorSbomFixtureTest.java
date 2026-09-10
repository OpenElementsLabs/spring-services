package com.openelements.spring.base.info;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Fixture test for {@link CycloneDxReader} against a <em>real</em> {@code cyclonedx-maven-plugin}
 * output ({@code sbom/reactor.cdx.json}), checked in per design decision D8.
 *
 * <p>A hand-written fixture only proves the parser handles what its author imagined. A real
 * generated SBOM is what surfaces a CycloneDX format change: when this test breaks after a plugin or
 * format upgrade, that is the moment someone must look at {@link CycloneDxReader}. The fixture is
 * regenerated deliberately with {@code mvn -pl spring-services-core
 * org.cyclonedx:cyclonedx-maven-plugin:makeBom} (or {@code -Pfull-build}).
 *
 * <p><b>Mock-Audit.</b> Zero mocks — a real SBOM read from the classpath.
 */
@DisplayName("CycloneDxReader — real reactor SBOM fixture")
class ReactorSbomFixtureTest {

  @Test
  @DisplayName("The real reactor SBOM parses: components present, a Spring Boot purl, every name non-null.")
  void realReactorSbomParses() {
    final SbomDocument document =
        new CycloneDxReader().read(new ClassPathResource("sbom/reactor.cdx.json")).orElseThrow();

    assertThat(document.summary().bomFormat()).isEqualTo("CycloneDX");
    assertThat(document.summary().componentCount()).isPositive();
    assertThat(document.components()).isNotEmpty();
    assertThat(document.components()).allSatisfy(component -> assertThat(component.name()).isNotNull());
    assertThat(document.components())
        .anySatisfy(
            component ->
                assertThat(component.purl()).isNotNull().contains("org.springframework.boot"));
  }
}
