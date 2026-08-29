# Behaviors: Application build and SBOM info

Scenarios marked **[context]** must be exercised against a real Spring application context
(`ApplicationContextRunner` or `@SpringBootTest`); all others are unit-level, with
`BuildProperties` / `GitProperties` constructed by hand and the SBOM resource pointed at a test
fixture.

## Artifact coordinates

### Coordinates are read from build-info.properties

- **Given** a `BuildProperties` carrying `group=com.open-elements`, `artifact=open-crm-backend`,
  `version=1.2.0`, `name=Open CRM Backend`
- **When** `applicationInfoService.getApplicationInfo()` is called
- **Then** `group()`, `artifact()`, `version()` and `name()` return exactly those values

### Missing build-info yields empty coordinates, not an exception

- **Given** no `BuildProperties` bean — the IDE-start case, where no `build-info.properties` exists
- **When** `getApplicationInfo()` is called
- **Then** the returned `ApplicationInfo` is not `null`
- **And** `group()`, `artifact()`, `version()` and `name()` are all `null`
- **And** no exception is thrown

### The model exposes no build time

- **Given** a `BuildProperties` whose `build.time` is set
- **When** the `ApplicationInfo` record is inspected
- **Then** it has no component representing a build timestamp — a value fixed in `java-parent`
  would describe the parent release, not this build

## Git metadata

### Git info is read from git.properties

- **Given** a `GitProperties` with `commit.id=a1b2c3d4e5f6a7b8c9d0`, `commit.id.abbrev=a1b2c3d`,
  `branch=main`, `tags=v1.2.0`, `dirty=false`, `commit.time=2026-08-01T10:15:30Z`
- **When** `getApplicationInfo().git()` is read
- **Then** `commitId()` is `a1b2c3d4e5f6a7b8c9d0`
- **And** `shortCommitId()` is `a1b2c3d`
- **And** `branch()` is `main`, `tag()` is `v1.2.0`
- **And** `dirty()` is `Boolean.FALSE`
- **And** `commitTime()` is `2026-08-01T10:15:30Z`

### Git info falls back to build.commit when git.properties is absent

- **Given** no `GitProperties`
- **And** a `BuildProperties` carrying `commit=a1b2c3d4e5f6a7b8c9d0` — the container build that
  received the hash as a build argument
- **When** `getApplicationInfo().git()` is read
- **Then** `commitId()` is `a1b2c3d4e5f6a7b8c9d0`
- **And** `shortCommitId()` is `a1b2c3d` — the first 7 characters, since no abbrev was supplied
- **And** `branch()`, `tag()` and `commitTime()` are `null`
- **And** `dirty()` is `null`

### git.properties wins when both sources disagree

- **Given** a `GitProperties` with `commit.id=aaaaaaaaaaaa`
- **And** a `BuildProperties` with `commit=bbbbbbbbbbbb`
- **When** `getApplicationInfo().git().commitId()` is read
- **Then** it is `aaaaaaaaaaaa`

### No git information at all yields a null GitInfo

- **Given** neither a `GitProperties` nor a `build.commit` entry
- **When** `getApplicationInfo().git()` is read
- **Then** it is `null` — the absence of a commit is not represented by a `GitInfo` full of nulls

### An unknown dirty state is null, not false

- **Given** a `GitProperties` that carries no `dirty` key
- **When** `getApplicationInfo().git().dirty()` is read
- **Then** it is `null`
- **And** it is therefore distinguishable from a clean build, which yields `Boolean.FALSE`

### A dirty build is reported as dirty

- **Given** a `GitProperties` with `dirty=true`
- **When** `getApplicationInfo().git().dirty()` is read
- **Then** it is `Boolean.TRUE`

### A commit hash shorter than seven characters is not truncated

- **Given** a `BuildProperties` with `commit=abc12`
- **When** `getApplicationInfo().git().shortCommitId()` is read
- **Then** it is `abc12` — the whole value, with no index-out-of-bounds failure

### An empty tag value is treated as absent

- **Given** a `GitProperties` whose `tags` key is present but empty — the normal state of a commit
  that carries no tag
- **When** `getApplicationInfo().git().tag()` is read
- **Then** it is `null`

## SBOM reading

### A valid SBOM is summarised

- **Given** a CycloneDX 1.6 document at `classpath:META-INF/sbom/application.cdx.json` with
  `serialNumber=urn:uuid:1234…`, a `metadata.component` and three components
- **When** `getApplicationInfo().sbom()` is read
- **Then** `bomFormat()` is `CycloneDX` and `specVersion()` is `1.6`
- **And** `serialNumber()` is `urn:uuid:1234…`
- **And** `componentCount()` is `3`
- **And** `application()` is the component declared under `metadata.component`

### The summary carries no SBOM timestamp

- **Given** a CycloneDX document whose `metadata.timestamp` is set
- **When** the `SbomSummary` record is inspected
- **Then** it has no component representing that timestamp

### The full component list is available separately

- **Given** the same document
- **When** `applicationInfoService.findSbom()` is called
- **Then** the result is present
- **And** `components()` has 3 entries with their `group`, `name`, `version`, `type` and `purl`
- **And** `summary()` equals the summary returned by `getApplicationInfo().sbom()`

### getApplicationInfo does not carry the component list

- **Given** a document with 400 components
- **When** `getApplicationInfo()` is called
- **Then** the returned object graph contains no component list — only `componentCount()` is `400`

### An explicit location overrides autodetection

- **Given** `openelements.info.sbom.location=classpath:custom/my-bom.json`
- **And** a valid SBOM at both that location and `classpath:META-INF/sbom/bom.json`
- **When** the SBOM is read
- **Then** it is the document from `classpath:custom/my-bom.json`

### Autodetection prefers bom.json over application.cdx.json

- **Given** no explicit location
- **And** valid but different SBOMs at `classpath:META-INF/sbom/bom.json` and
  `classpath:META-INF/sbom/application.cdx.json`
- **When** the SBOM is read
- **Then** it is the document from `bom.json` — matching the probe order of Spring Boot's
  `SbomEndpoint`

### SBOM reading can be switched off

- **Given** `openelements.info.sbom.enabled=false`
- **And** a valid SBOM on the classpath
- **When** `getApplicationInfo().sbom()` is read
- **Then** it is `null`
- **And** `findSbom()` is empty
- **And** the file is never opened

### No SBOM on the classpath yields no SBOM

- **Given** no SBOM at any autodetected location
- **When** `getApplicationInfo().sbom()` is read
- **Then** it is `null`
- **And** `findSbom()` is empty
- **And** nothing is logged at WARN — an absent SBOM is a normal development state

## SBOM parsing edge cases

### Malformed JSON degrades to no SBOM **[context]**

- **Given** a truncated `application.cdx.json` on the classpath
- **When** the application context starts
- **Then** startup succeeds
- **And** a warning naming the resource is logged
- **And** `findSbom()` is empty

### A JSON document that is not CycloneDX degrades to no SBOM

- **Given** a well-formed JSON document whose `bomFormat` is `SPDX`
- **When** the SBOM is read
- **Then** `findSbom()` is empty
- **And** a warning is logged

### An SBOM with no components is still a valid SBOM

- **Given** a CycloneDX document whose `components` array is empty
- **When** `getApplicationInfo().sbom()` is read
- **Then** it is **not** `null`
- **And** `componentCount()` is `0`
- **And** `findSbom().get().components()` is empty

### An SBOM with no metadata section parses

- **Given** a CycloneDX document with no `metadata` key
- **When** the SBOM is read
- **Then** it parses successfully
- **And** `application()` is `null`

### Unknown fields from a future spec version are ignored

- **Given** a CycloneDX document declaring `specVersion=1.7` and carrying an unrecognised top-level
  key and unrecognised keys inside each component
- **When** the SBOM is read
- **Then** it parses successfully and all components are returned

### The real reactor SBOM parses **[fixture]**

- **Given** the checked-in SBOM generated by `cyclonedx-maven-plugin` for this reactor
- **When** it is read
- **Then** parsing succeeds
- **And** `componentCount()` is greater than zero
- **And** the components include one whose `purl` identifies a Spring Boot artifact
- **And** every component has a non-null `name`

## Licenses

### An SPDX license id is surfaced as the id

- **Given** a component whose `licenses` is `[{"license": {"id": "Apache-2.0"}}]`
- **When** the component is read
- **Then** `licenses()` is `["Apache-2.0"]`

### A free-text license name is surfaced when no id is declared

- **Given** a component whose `licenses` is
  `[{"license": {"name": "The Apache Software License, Version 2.0"}}]`
- **When** the component is read
- **Then** `licenses()` is `["The Apache Software License, Version 2.0"]`

### A license expression is surfaced as the expression

- **Given** a component whose `licenses` is `[{"expression": "MIT OR Apache-2.0"}]`
- **When** the component is read
- **Then** `licenses()` is `["MIT OR Apache-2.0"]`

### An id takes precedence over a name on the same entry

- **Given** a component whose license entry declares both `id=Apache-2.0` and a free-text `name`
- **When** the component is read
- **Then** `licenses()` is `["Apache-2.0"]`

### A component without licenses yields an empty list

- **Given** a component with no `licenses` key
- **When** the component is read
- **Then** `licenses()` is empty and not `null`

### The summary aggregates distinct licenses in sorted order

- **Given** components declaring `MIT`, `Apache-2.0`, `MIT` and no license respectively
- **When** `getApplicationInfo().sbom().licenses()` is read
- **Then** it is exactly `["Apache-2.0", "MIT"]`

## Service contract

### The service is available without JPA **[context]**

- **Given** an application context with `EntityManagerFactory` absent from the classpath
- **When** the context starts with `spring-services-core` on the classpath
- **Then** an `ApplicationInfoService` bean exists
- **And** `SpringServicesCoreAutoConfiguration` has not been applied

### An application can substitute its own service **[context]**

- **Given** an application that declares its own `ApplicationInfoService` bean
- **When** the context starts
- **Then** the application's bean is used and the library's is not registered

### The service never returns null

- **Given** any combination of present and absent build inputs
- **When** `getApplicationInfo()` is called
- **Then** the result is non-null

### Returned collections are immutable

- **Given** any parsed SBOM
- **When** a caller attempts to modify `components()` or any `licenses()` list
- **Then** an `UnsupportedOperationException` is thrown

### Repeated calls return the same cached result

- **Given** a service constructed over a valid SBOM
- **When** `getApplicationInfo()` and `findSbom()` are each called twice
- **Then** both calls return the identical instance
- **And** the SBOM resource is read exactly once, during construction
