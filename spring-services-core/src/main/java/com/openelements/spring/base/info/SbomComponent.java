package com.openelements.spring.base.info;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One component (dependency) from a CycloneDX SBOM.
 *
 * <p>Populated by {@link CycloneDxReader} from a single entry of the SBOM's flat {@code components}
 * array — or, for {@link SbomSummary#application()}, from {@code metadata.component}. Only the
 * handful of fields the admin view needs are surfaced; the CycloneDX {@code dependencies} graph is
 * not parsed, so this type does not distinguish direct from transitive dependencies.
 *
 * @param group the component's group / namespace (Maven {@code groupId}), or {@code null} if the
 *     SBOM did not declare one
 * @param name the component name (Maven {@code artifactId}); never {@code null} — a CycloneDX
 *     component without a name is not a valid component
 * @param version the component version, or {@code null} if the SBOM did not declare one
 * @param type the CycloneDX component type ({@code "library"}, {@code "application"},
 *     {@code "framework"}, …), or {@code null} if absent
 * @param purl the package URL uniquely identifying the artifact, or {@code null} if absent
 * @param licenses the licenses declared for this component, each collapsed to a single string
 *     (SPDX id, else free-text name, else expression); never {@code null}, never contains
 *     {@code null}, and always unmodifiable
 */
public record SbomComponent(
    @Nullable String group,
    String name,
    @Nullable String version,
    @Nullable String type,
    @Nullable String purl,
    List<String> licenses) {

  /**
   * Normalises the {@code licenses} list so the accessor never returns {@code null} and never
   * returns a mutable list.
   *
   * @param group the component group, or {@code null}
   * @param name the component name; must not be {@code null}
   * @param version the component version, or {@code null}
   * @param type the CycloneDX component type, or {@code null}
   * @param purl the package URL, or {@code null}
   * @param licenses the declared licenses, or {@code null} for none
   */
  public SbomComponent {
    licenses = licenses == null ? List.of() : List.copyOf(licenses);
  }
}
