package com.openelements.spring.base.info;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A compact summary of a CycloneDX SBOM: enough to identify the document and describe its shape,
 * without carrying the full component list.
 *
 * <p>This is the SBOM view returned by {@link ApplicationInfoService#getApplicationInfo()}, so an
 * application can expose a small {@code /api/info} payload without serialising several hundred
 * components on every request. The full list is available separately via
 * {@link ApplicationInfoService#findSbom()}.
 *
 * <p>The SBOM's {@code metadata.timestamp} is deliberately <em>not</em> exposed: with reproducible
 * builds it is a fixed constant and would misrepresent "when was this built?". The
 * {@code serialNumber} is kept because {@code cyclonedx-maven-plugin} derives it deterministically
 * from the SBOM's content, so it identifies the document without asserting a time.
 *
 * @param bomFormat the SBOM format, always {@code "CycloneDX"} for a document this reader accepts,
 *     or {@code null} if the field was absent
 * @param specVersion the CycloneDX spec version (e.g. {@code "1.6"}), or {@code null} if absent
 * @param serialNumber the SBOM serial number (e.g. {@code "urn:uuid:…"}), or {@code null} if absent
 * @param application the component declared under {@code metadata.component} — the artifact the SBOM
 *     describes — or {@code null} if the SBOM carried no {@code metadata.component}
 * @param componentCount the number of entries in the SBOM's {@code components} array
 * @param licenses the distinct, sorted union of every license declared by any component; never
 *     {@code null}, never contains {@code null}, and always unmodifiable
 */
public record SbomSummary(
    @Nullable String bomFormat,
    @Nullable String specVersion,
    @Nullable String serialNumber,
    @Nullable SbomComponent application,
    int componentCount,
    List<String> licenses) {

  /**
   * Normalises the {@code licenses} list so the accessor never returns {@code null} and never
   * returns a mutable list.
   *
   * @param bomFormat the SBOM format, or {@code null}
   * @param specVersion the CycloneDX spec version, or {@code null}
   * @param serialNumber the SBOM serial number, or {@code null}
   * @param application the {@code metadata.component}, or {@code null}
   * @param componentCount the number of components
   * @param licenses the distinct union of component licenses, or {@code null} for none
   */
  public SbomSummary {
    licenses = licenses == null ? List.of() : List.copyOf(licenses);
  }
}
