# Implementation Steps: Spring Boot 3.5 upgrade

## Step 1: Bump Spring Boot to 3.5.14

- [ ] Update `spring-boot-dependencies` BOM version in `pom.xml` `<dependencyManagement>` from `3.5.13` to `3.5.14`

**Acceptance criteria:**
- [ ] `pom.xml` shows `<version>3.5.14</version>` for `spring-boot-dependencies`
- [ ] `./mvnw dependency:list` resolves `spring-boot-starter-*` to `3.5.14`

**Related behaviors:** Spring Boot version is 3.5.14

---

## Step 2: Pin Testcontainers to 2.0.5 in dependencyManagement

- [ ] Replace `testcontainers.version` and `junit-jupiter.version` properties (both `1.20.4`) with a single `testcontainers.version` property set to `2.0.5`
- [ ] Add a `<dependencyManagement>` section pinning `org.testcontainers:testcontainers`, `org.testcontainers:junit-jupiter`, and `org.testcontainers:postgresql` to `${testcontainers.version}`
- [ ] Include an XML comment explaining the override (Boot BOM still ships 1.21.4, which has the Docker Engine 29+ bug)
- [ ] Remove `<version>` tags from the Testcontainers `<dependency>` entries (now provided by dependencyManagement)

**Acceptance criteria:**
- [ ] `./mvnw dependency:list` resolves Testcontainers artefacts to `2.0.5`
- [ ] `pom.xml` contains the explanatory comment

**Related behaviors:** Testcontainers version is 2.0.5

---

## Step 3: Verify no explicit byte-buddy override remains

- [ ] Confirm `pom.xml` has no `<dependency>` entries for `net.bytebuddy:byte-buddy` or `net.bytebuddy:byte-buddy-agent`
- [ ] If any exist, remove them

**Acceptance criteria:**
- [ ] `./mvnw dependency:list` shows byte-buddy resolved to ≥ `1.17.8` (BOM-provided)

**Related behaviors:** byte-buddy override is removed

---

## Step 4: Build and verify

- [ ] Run `./mvnw clean verify`
- [ ] All integration tests pass on the local Docker Engine
- [ ] No new compiler or deprecation warnings introduced

**Acceptance criteria:**
- [ ] Build exits with code 0
- [ ] All existing tests pass
