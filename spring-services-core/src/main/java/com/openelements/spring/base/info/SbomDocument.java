package com.openelements.spring.base.info;

import java.util.List;

/**
 * The full parsed CycloneDX SBOM: its {@link SbomSummary} plus the complete list of components.
 *
 * <p>Returned by {@link ApplicationInfoService#findSbom()}. The {@link #summary()} is the same value
 * carried by {@link ApplicationInfo#sbom()}, so the small info payload and the full admin view agree
 * on format, serial number, component count and license union.
 *
 * @param summary the compact summary of this SBOM; never {@code null}
 * @param components every component in the SBOM's {@code components} array; never {@code null},
 *     never contains {@code null}, and always unmodifiable
 */
public record SbomDocument(SbomSummary summary, List<SbomComponent> components) {

  /**
   * Normalises the {@code components} list so the accessor never returns {@code null} and never
   * returns a mutable list.
   *
   * @param summary the compact summary; must not be {@code null}
   * @param components the component list, or {@code null} for none
   */
  public SbomDocument {
    components = components == null ? List.of() : List.copyOf(components);
  }
}
