package com.openelements.spring.base.info;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;

/**
 * Unit tests for {@link ApplicationInfoService}: coordinate reading, the two-source Git precedence
 * rule, SBOM location resolution, the summary/full-list split, and the caching contract.
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link BuildProperties} and {@link GitProperties} are constructed by hand from {@link
 * Properties}; the SBOM source is controlled through purpose-built {@link ResourceLoader}s — a
 * {@link DefaultResourceLoader} for classpath fixtures, an in-memory {@code MapResourceLoader} to
 * prove location precedence without polluting the classpath, and a {@code ThrowingResourceLoader}
 * that fails if touched, to prove a disabled SBOM is never read. Pure JUnit 5 + AssertJ.
 *
 * <p><b>Mock-Audit.</b> Zero mocking libraries. The test doubles are tiny hand-written {@link
 * ObjectProvider}/{@link ResourceLoader} implementations, not mocks — they carry real behaviour the
 * service depends on (nullable availability, resource lookup), which a mock would only obscure.
 */
@DisplayName("ApplicationInfoService")
class ApplicationInfoServiceTest {

  private static final String NO_SBOM_LOCATION = "classpath:sbom/does-not-exist.cdx.json";

  private ApplicationInfoService service(
      @Nullable final BuildProperties build,
      @Nullable final GitProperties git,
      final ResourceLoader resourceLoader,
      final ApplicationInfoProperties properties) {
    return new ApplicationInfoService(
        new TestObjectProvider<>(build), new TestObjectProvider<>(git), resourceLoader, properties);
  }

  private static ApplicationInfoProperties sbomDisabled() {
    return new ApplicationInfoProperties(new ApplicationInfoProperties.Sbom(false, ""));
  }

  private static ApplicationInfoProperties sbomAt(final String location) {
    return new ApplicationInfoProperties(new ApplicationInfoProperties.Sbom(true, location));
  }

  private static ApplicationInfoProperties sbomAutodetect() {
    return new ApplicationInfoProperties(new ApplicationInfoProperties.Sbom(true, ""));
  }

  private static BuildProperties buildProperties(final Map<String, String> entries) {
    final Properties properties = new Properties();
    properties.putAll(entries);
    return new BuildProperties(properties);
  }

  private static GitProperties gitProperties(final Map<String, String> entries) {
    final Properties properties = new Properties();
    properties.putAll(entries);
    return new GitProperties(properties);
  }

  @Nested
  @DisplayName("Artifact coordinates")
  class Coordinates {

    @Test
    @DisplayName("Coordinates are read from build-info.properties.")
    void readsCoordinates() {
      final BuildProperties build =
          buildProperties(
              Map.of(
                  "group", "com.open-elements",
                  "artifact", "open-crm-backend",
                  "version", "1.2.0",
                  "name", "Open CRM Backend"));

      final ApplicationInfo info =
          service(build, null, new DefaultResourceLoader(), sbomAt(NO_SBOM_LOCATION))
              .getApplicationInfo();

      assertThat(info.group()).isEqualTo("com.open-elements");
      assertThat(info.artifact()).isEqualTo("open-crm-backend");
      assertThat(info.version()).isEqualTo("1.2.0");
      assertThat(info.name()).isEqualTo("Open CRM Backend");
    }

    @Test
    @DisplayName("Missing build-info yields empty coordinates and no exception.")
    void missingBuildInfo() {
      final ApplicationInfo info =
          service(null, null, new DefaultResourceLoader(), sbomAt(NO_SBOM_LOCATION))
              .getApplicationInfo();

      assertThat(info).isNotNull();
      assertThat(info.group()).isNull();
      assertThat(info.artifact()).isNull();
      assertThat(info.version()).isNull();
      assertThat(info.name()).isNull();
    }
  }

  @Nested
  @DisplayName("Git metadata")
  class Git {

    @Test
    @DisplayName("Git info is read from git.properties (all fields).")
    void readsGitProperties() {
      final GitProperties git =
          gitProperties(
              Map.of(
                  "commit.id", "a1b2c3d4e5f6a7b8c9d0",
                  "commit.id.abbrev", "a1b2c3d",
                  "branch", "main",
                  "tags", "v1.2.0",
                  "dirty", "false",
                  "commit.time", "2026-08-01T10:15:30Z"));

      final GitInfo info =
          service(null, git, new DefaultResourceLoader(), sbomAt(NO_SBOM_LOCATION))
              .getApplicationInfo()
              .git();

      assertThat(info).isNotNull();
      assertThat(info.commitId()).isEqualTo("a1b2c3d4e5f6a7b8c9d0");
      assertThat(info.shortCommitId()).isEqualTo("a1b2c3d");
      assertThat(info.branch()).isEqualTo("main");
      assertThat(info.tag()).isEqualTo("v1.2.0");
      assertThat(info.dirty()).isEqualTo(Boolean.FALSE);
      assertThat(info.commitTime()).isEqualTo(Instant.parse("2026-08-01T10:15:30Z"));
    }

    @Test
    @DisplayName("Git info falls back to build.commit when git.properties is absent.")
    void fallsBackToBuildCommit() {
      final BuildProperties build =
          buildProperties(Map.of("commit", "a1b2c3d4e5f6a7b8c9d0"));

      final GitInfo info =
          service(build, null, new DefaultResourceLoader(), sbomAt(NO_SBOM_LOCATION))
              .getApplicationInfo()
              .git();

      assertThat(info).isNotNull();
      assertThat(info.commitId()).isEqualTo("a1b2c3d4e5f6a7b8c9d0");
      assertThat(info.shortCommitId()).isEqualTo("a1b2c3d");
      assertThat(info.branch()).isNull();
      assertThat(info.tag()).isNull();
      assertThat(info.commitTime()).isNull();
      assertThat(info.dirty()).isNull();
    }

    @Test
    @DisplayName("git.properties wins when both sources disagree.")
    void gitPropertiesWins() {
      final GitProperties git = gitProperties(Map.of("commit.id", "aaaaaaaaaaaa"));
      final BuildProperties build = buildProperties(Map.of("commit", "bbbbbbbbbbbb"));

      final GitInfo info =
          service(build, git, new DefaultResourceLoader(), sbomAt(NO_SBOM_LOCATION))
              .getApplicationInfo()
              .git();

      assertThat(info).isNotNull();
      assertThat(info.commitId()).isEqualTo("aaaaaaaaaaaa");
    }

    @Test
    @DisplayName("No git information at all yields a null GitInfo.")
    void noGitYieldsNull() {
      final GitInfo info =
          service(null, null, new DefaultResourceLoader(), sbomAt(NO_SBOM_LOCATION))
              .getApplicationInfo()
              .git();

      assertThat(info).isNull();
    }

    @Test
    @DisplayName("An unknown dirty state is null, not false.")
    void unknownDirtyIsNull() {
      final GitProperties git = gitProperties(Map.of("commit.id", "abcdef1234567890"));

      final GitInfo info =
          service(null, git, new DefaultResourceLoader(), sbomAt(NO_SBOM_LOCATION))
              .getApplicationInfo()
              .git();

      assertThat(info).isNotNull();
      assertThat(info.dirty()).isNull();
    }

    @Test
    @DisplayName("A dirty build is reported as dirty.")
    void dirtyIsReported() {
      final GitProperties git =
          gitProperties(Map.of("commit.id", "abcdef1234567890", "dirty", "true"));

      final GitInfo info =
          service(null, git, new DefaultResourceLoader(), sbomAt(NO_SBOM_LOCATION))
              .getApplicationInfo()
              .git();

      assertThat(info).isNotNull();
      assertThat(info.dirty()).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("A commit hash shorter than seven characters is not truncated.")
    void shortHashNotTruncated() {
      final BuildProperties build = buildProperties(Map.of("commit", "abc12"));

      final GitInfo info =
          service(build, null, new DefaultResourceLoader(), sbomAt(NO_SBOM_LOCATION))
              .getApplicationInfo()
              .git();

      assertThat(info).isNotNull();
      assertThat(info.shortCommitId()).isEqualTo("abc12");
    }

    @Test
    @DisplayName("An empty tag value is treated as absent.")
    void emptyTagIsAbsent() {
      final Map<String, String> entries = new HashMap<>();
      entries.put("commit.id", "abcdef1234567890");
      entries.put("tags", "");
      final GitProperties git = gitProperties(entries);

      final GitInfo info =
          service(null, git, new DefaultResourceLoader(), sbomAt(NO_SBOM_LOCATION))
              .getApplicationInfo()
              .git();

      assertThat(info).isNotNull();
      assertThat(info.tag()).isNull();
    }
  }

  @Nested
  @DisplayName("SBOM resolution")
  class Sbom {

    @Test
    @DisplayName("A valid SBOM is summarised in getApplicationInfo().sbom().")
    void summarised() {
      final SbomSummary summary =
          service(null, null, new DefaultResourceLoader(), sbomAt("classpath:sbom/valid.cdx.json"))
              .getApplicationInfo()
              .sbom();

      assertThat(summary).isNotNull();
      assertThat(summary.componentCount()).isEqualTo(3);
      assertThat(summary.licenses()).containsExactly("Apache-2.0", "MIT");
    }

    @Test
    @DisplayName("getApplicationInfo() does not carry the component list; findSbom() does.")
    void summaryVersusFullList() {
      final ApplicationInfoService service =
          service(null, null, new DefaultResourceLoader(), sbomAt("classpath:sbom/valid.cdx.json"));

      final Optional<SbomDocument> full = service.findSbom();
      assertThat(full).isPresent();
      assertThat(full.get().components()).hasSize(3);
      assertThat(full.get().summary()).isSameAs(service.getApplicationInfo().sbom());
    }

    @Test
    @DisplayName("An explicit location overrides autodetection.")
    void explicitLocationOverrides() {
      final MapResourceLoader loader = new MapResourceLoader();
      loader.put("classpath:custom/my-bom.json", doc("urn:uuid:custom"));
      loader.put("classpath:META-INF/sbom/bom.json", doc("urn:uuid:autodetected"));

      final SbomSummary summary =
          service(null, null, loader, sbomAt("classpath:custom/my-bom.json"))
              .getApplicationInfo()
              .sbom();

      assertThat(summary).isNotNull();
      assertThat(summary.serialNumber()).isEqualTo("urn:uuid:custom");
    }

    @Test
    @DisplayName("Autodetection prefers bom.json over application.cdx.json.")
    void autodetectionPrefersBomJson() {
      final MapResourceLoader loader = new MapResourceLoader();
      loader.put("classpath:META-INF/sbom/bom.json", doc("urn:uuid:bom"));
      loader.put("classpath:META-INF/sbom/application.cdx.json", doc("urn:uuid:application"));

      final SbomSummary summary =
          service(null, null, loader, sbomAutodetect()).getApplicationInfo().sbom();

      assertThat(summary).isNotNull();
      assertThat(summary.serialNumber()).isEqualTo("urn:uuid:bom");
    }

    @Test
    @DisplayName("SBOM reading can be switched off — and the file is never opened.")
    void disabledNeverOpensFile() {
      final ApplicationInfoService service =
          service(null, null, new ThrowingResourceLoader(), sbomDisabled());

      assertThat(service.getApplicationInfo().sbom()).isNull();
      assertThat(service.findSbom()).isEmpty();
    }

    @Test
    @DisplayName("No SBOM on the classpath yields no SBOM.")
    void noSbomYieldsEmpty() {
      final ApplicationInfoService service =
          service(null, null, new MapResourceLoader(), sbomAutodetect());

      assertThat(service.getApplicationInfo().sbom()).isNull();
      assertThat(service.findSbom()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Service contract")
  class Contract {

    @Test
    @DisplayName("The service never returns null from getApplicationInfo().")
    void neverReturnsNull() {
      final ApplicationInfoService service =
          service(null, null, new MapResourceLoader(), sbomAutodetect());

      assertThat(service.getApplicationInfo()).isNotNull();
    }

    @Test
    @DisplayName("Returned collections are immutable.")
    void collectionsAreImmutable() {
      final SbomDocument document =
          service(null, null, new DefaultResourceLoader(), sbomAt("classpath:sbom/valid.cdx.json"))
              .findSbom()
              .orElseThrow();

      org.junit.jupiter.api.Assertions.assertThrows(
          UnsupportedOperationException.class, () -> document.components().clear());
      org.junit.jupiter.api.Assertions.assertThrows(
          UnsupportedOperationException.class, () -> document.summary().licenses().clear());
    }

    @Test
    @DisplayName("Repeated calls return the same cached instances; the resource is read once.")
    void cachesResults() {
      final CountingResourceLoader loader =
          new CountingResourceLoader(new DefaultResourceLoader());
      final ApplicationInfoService service =
          service(null, null, loader, sbomAt("classpath:sbom/valid.cdx.json"));

      assertThat(service.getApplicationInfo()).isSameAs(service.getApplicationInfo());
      assertThat(service.findSbom()).isSameAs(service.findSbom());
      assertThat(loader.getInputStreamCount()).isEqualTo(1);
    }
  }

  private static Resource doc(final String serialNumber) {
    final String json =
        """
        {
          "bomFormat": "CycloneDX",
          "specVersion": "1.6",
          "serialNumber": "%s",
          "components": []
        }
        """
            .formatted(serialNumber);
    return new ByteArrayResource(json.getBytes());
  }

  /** Minimal {@link ObjectProvider} that yields a single (possibly {@code null}) value. */
  private static final class TestObjectProvider<T> implements ObjectProvider<T> {

    @Nullable private final T value;

    TestObjectProvider(@Nullable final T value) {
      this.value = value;
    }

    @Override
    public T getObject() throws BeansException {
      if (value == null) {
        throw new IllegalStateException("no value");
      }
      return value;
    }

    @Override
    public T getObject(final Object... args) throws BeansException {
      return getObject();
    }

    @Override
    @Nullable
    public T getIfAvailable() throws BeansException {
      return value;
    }

    @Override
    @Nullable
    public T getIfUnique() throws BeansException {
      return value;
    }
  }

  /** {@link ResourceLoader} backed by an explicit location→resource map. */
  private static final class MapResourceLoader implements ResourceLoader {

    private final Map<String, Resource> resources = new HashMap<>();
    private final ResourceLoader delegate = new DefaultResourceLoader();

    void put(final String location, final Resource resource) {
      resources.put(location, resource);
    }

    @Override
    public Resource getResource(final String location) {
      return resources.getOrDefault(location, new NonExistentResource(location));
    }

    @Override
    public ClassLoader getClassLoader() {
      return delegate.getClassLoader();
    }
  }

  /** A resource whose {@link #exists()} is always {@code false}. */
  private static final class NonExistentResource extends ByteArrayResource {

    NonExistentResource(final String description) {
      super(new byte[0], description);
    }

    @Override
    public boolean exists() {
      return false;
    }
  }

  /** {@link ResourceLoader} that fails if used — proves a disabled SBOM never touches the loader. */
  private static final class ThrowingResourceLoader implements ResourceLoader {

    @Override
    public Resource getResource(final String location) {
      throw new AssertionError("SBOM resource must not be resolved when reading is disabled");
    }

    @Override
    public ClassLoader getClassLoader() {
      return getClass().getClassLoader();
    }
  }

  /** Counts how often {@code getInputStream()} is invoked on the resolved resource. */
  private static final class CountingResourceLoader implements ResourceLoader {

    private final ResourceLoader delegate;
    private int inputStreamCount;

    CountingResourceLoader(final ResourceLoader delegate) {
      this.delegate = delegate;
    }

    int getInputStreamCount() {
      return inputStreamCount;
    }

    @Override
    public Resource getResource(final String location) {
      final Resource resource = delegate.getResource(location);
      return new org.springframework.core.io.AbstractResource() {
        @Override
        public String getDescription() {
          return resource.getDescription();
        }

        @Override
        public boolean exists() {
          return resource.exists();
        }

        @Override
        public java.io.InputStream getInputStream() throws java.io.IOException {
          inputStreamCount++;
          return resource.getInputStream();
        }
      };
    }

    @Override
    public ClassLoader getClassLoader() {
      return delegate.getClassLoader();
    }
  }
}
