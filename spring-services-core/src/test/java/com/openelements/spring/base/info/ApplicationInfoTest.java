package com.openelements.spring.base.info;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link ApplicationInfo} record model.
 *
 * <h2>What is tested</h2>
 *
 * <p>The {@link ApplicationInfo#empty()} factory returns an all-{@code null} instance (the value the
 * service uses when the build produced no metadata), and — as a compile-time-plus-reflection
 * guarantee — the record exposes no build-timestamp component, per design decision D3: a build time
 * would be a fixed reproducible-build constant, not an answer to "when was this deployed?".
 *
 * <p><b>Mock-Audit.</b> Zero mocks — pure record inspection.
 */
@DisplayName("ApplicationInfo")
class ApplicationInfoTest {

  @Test
  @DisplayName("empty() returns an instance whose every component is null.")
  void emptyIsAllNull() {
    final ApplicationInfo info = ApplicationInfo.empty();

    assertThat(info.group()).isNull();
    assertThat(info.artifact()).isNull();
    assertThat(info.version()).isNull();
    assertThat(info.name()).isNull();
    assertThat(info.git()).isNull();
    assertThat(info.sbom()).isNull();
  }

  @Test
  @DisplayName("The record exposes no build-timestamp component.")
  void noBuildTimestampComponent() {
    assertThat(Arrays.stream(ApplicationInfo.class.getRecordComponents()).map(RecordComponent::getName))
        .doesNotContain("buildTime", "time", "timestamp", "buildTimestamp");
  }

  @Test
  @DisplayName("SBOM records normalise a null collection to an empty, immutable list.")
  void recordsNormaliseNullCollections() {
    final SbomComponent component = new SbomComponent(null, "n", null, null, null, null);
    assertThat(component.licenses()).isEmpty();

    final SbomSummary summary = new SbomSummary(null, null, null, null, 0, null);
    assertThat(summary.licenses()).isEmpty();

    final SbomDocument document = new SbomDocument(summary, null);
    assertThat(document.components()).isEmpty();

    org.junit.jupiter.api.Assertions.assertThrows(
        UnsupportedOperationException.class, () -> component.licenses().add("x"));
    org.junit.jupiter.api.Assertions.assertThrows(
        UnsupportedOperationException.class, () -> document.components().add(component));
  }
}
