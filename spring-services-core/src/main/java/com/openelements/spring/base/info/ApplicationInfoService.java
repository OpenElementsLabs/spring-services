package com.openelements.spring.base.info;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Read-only entry point for "which build is running, and what is it made of?".
 *
 * <p>The service reads three build-produced inputs — {@link BuildProperties} (artifact coordinates
 * and, as a Git fallback, {@code build.commit}), {@link GitProperties} (Git metadata) and a
 * CycloneDX SBOM on the classpath — and exposes them through an immutable {@link ApplicationInfo}
 * plus a full {@link SbomDocument}. It adds no build timestamp, reads no secrets, and never fails
 * startup: a build that produced no metadata yields an all-{@code null} {@link ApplicationInfo}, and
 * an unreadable SBOM yields no SBOM plus a single {@code WARN}.
 *
 * <p>Both values are computed once in the constructor and cached in final fields: the service is
 * immutable and trivially thread-safe, the SBOM file is read exactly once, and any warning about an
 * unreadable SBOM surfaces during startup rather than on the first admin-view request.
 *
 * <p>The library exposes no REST endpoint on top of this service. An SBOM discloses the exact
 * version of every dependency the application runs — a precise attack-surface map — so the
 * application owns the endpoint and, above all, its authorization (an admin-level guard).
 */
public class ApplicationInfoService {

  /**
   * SBOM autodetection locations, in the same order as Spring Boot's {@code SbomEndpoint}, so a
   * later actuator module serving the raw file and this parsed view describe the same document.
   */
  private static final List<String> AUTODETECT_LOCATIONS =
      List.of(
          "classpath:META-INF/sbom/bom.json",
          "classpath:META-INF/sbom/application.cdx.json",
          "classpath:META-INF/native-image/sbom.json");

  private final ApplicationInfo applicationInfo;

  private final Optional<SbomDocument> sbom;

  /**
   * Reads the build inputs and caches the resulting model.
   *
   * @param buildProperties provider for the optional {@link BuildProperties} bean
   * @param gitProperties provider for the optional {@link GitProperties} bean
   * @param resourceLoader loader used to resolve the SBOM resource
   * @param properties the SBOM-reading configuration
   */
  public ApplicationInfoService(
      final ObjectProvider<BuildProperties> buildProperties,
      final ObjectProvider<GitProperties> gitProperties,
      final ResourceLoader resourceLoader,
      final ApplicationInfoProperties properties) {
    final BuildProperties build = buildProperties.getIfAvailable();
    final GitProperties git = gitProperties.getIfAvailable();
    this.sbom = resolveSbom(resourceLoader, properties);
    this.applicationInfo =
        new ApplicationInfo(
            build == null ? null : build.getGroup(),
            build == null ? null : build.getArtifact(),
            build == null ? null : build.getVersion(),
            build == null ? null : build.getName(),
            resolveGit(git, build),
            this.sbom.map(SbomDocument::summary).orElse(null));
  }

  /**
   * Returns the build snapshot: artifact coordinates, Git info and an SBOM <em>summary</em>. The
   * full component list is deliberately not carried here — use {@link #findSbom()} for that.
   *
   * @return the cached {@link ApplicationInfo}; never {@code null}. Fields the build did not provide
   *     are {@code null}
   */
  public ApplicationInfo getApplicationInfo() {
    return applicationInfo;
  }

  /**
   * Returns the full parsed SBOM.
   *
   * @return the cached SBOM document, or empty if no SBOM was found, it could not be parsed, or SBOM
   *     reading is disabled
   */
  public Optional<SbomDocument> findSbom() {
    return sbom;
  }

  private static Optional<SbomDocument> resolveSbom(
      final ResourceLoader resourceLoader, final ApplicationInfoProperties properties) {
    if (!properties.sbom().enabled()) {
      return Optional.empty();
    }
    final Resource resource = resolveResource(resourceLoader, properties.sbom().location());
    if (resource == null) {
      return Optional.empty();
    }
    return new CycloneDxReader().read(resource);
  }

  private static @Nullable Resource resolveResource(
      final ResourceLoader resourceLoader, final String location) {
    if (location != null && !location.isBlank()) {
      return resourceLoader.getResource(location.trim());
    }
    for (final String candidate : AUTODETECT_LOCATIONS) {
      final Resource resource = resourceLoader.getResource(candidate);
      if (resource.exists()) {
        return resource;
      }
    }
    return null;
  }

  private static @Nullable GitInfo resolveGit(
      final @Nullable GitProperties git, final @Nullable BuildProperties build) {
    if (git != null) {
      final String commitId = git.getCommitId();
      if (commitId != null && !commitId.isBlank()) {
        String shortCommitId = git.getShortCommitId();
        if (shortCommitId == null || shortCommitId.isBlank()) {
          shortCommitId = shorten(commitId);
        }
        return new GitInfo(
            commitId,
            shortCommitId,
            blankToNull(git.getBranch()),
            blankToNull(git.get("tags")),
            parseDirty(git.get("dirty")),
            git.getCommitTime());
      }
    }
    if (build != null) {
      final String commitId = blankToNull(build.get("commit"));
      if (commitId != null) {
        return new GitInfo(
            commitId, shorten(commitId), null, null, null, parseInstant(build.get("commit.time")));
      }
    }
    return null;
  }

  /** Mirrors {@code GitProperties.getShortCommitId()}: first seven chars, or the whole short hash. */
  private static String shorten(final String commitId) {
    return commitId.length() > 7 ? commitId.substring(0, 7) : commitId;
  }

  private static @Nullable Boolean parseDirty(final @Nullable String value) {
    return value == null ? null : Boolean.valueOf(value);
  }

  private static @Nullable Instant parseInstant(final @Nullable String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (final DateTimeParseException e) {
      return null;
    }
  }

  private static @Nullable String blankToNull(final @Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
