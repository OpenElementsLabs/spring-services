package com.openelements.spring.base.info;

import org.jspecify.annotations.Nullable;

/**
 * A read-only snapshot of "which build is running": the artifact coordinates, the Git commit, and a
 * compact SBOM summary.
 *
 * <p>Returned by {@link ApplicationInfoService#getApplicationInfo()}, which never returns
 * {@code null}: fields the build did not provide are simply {@code null}. The SBOM is carried only
 * as a {@link SbomSummary} here; the full component list is fetched separately via
 * {@link ApplicationInfoService#findSbom()} so a small info endpoint need not serialise hundreds of
 * components.
 *
 * <p>There is deliberately no build-timestamp field: with reproducible builds
 * {@code project.build.outputTimestamp} is a fixed constant, so a build time would describe the
 * parent release rather than this deployment. A build is identified by {@link #version()} and the
 * commit hash in {@link #git()} — both true statements about the code.
 *
 * @param group the artifact group / Maven {@code groupId}, or {@code null} if no build-info was
 *     produced
 * @param artifact the artifact id / Maven {@code artifactId}, or {@code null} if no build-info was
 *     produced
 * @param version the artifact version, or {@code null} if no build-info was produced
 * @param name the human-readable application name, or {@code null} if no build-info was produced
 * @param git the Git metadata, or {@code null} if no commit hash is known
 * @param sbom the SBOM summary, or {@code null} if no SBOM was found, it could not be parsed, or
 *     SBOM reading is disabled
 */
public record ApplicationInfo(
    @Nullable String group,
    @Nullable String artifact,
    @Nullable String version,
    @Nullable String name,
    @Nullable GitInfo git,
    @Nullable SbomSummary sbom) {

  /**
   * Returns an {@code ApplicationInfo} with every field {@code null} — the value used when the build
   * produced no metadata at all.
   *
   * @return an all-{@code null} {@code ApplicationInfo}; never {@code null}
   */
  public static ApplicationInfo empty() {
    return new ApplicationInfo(null, null, null, null, null, null);
  }
}
