/**
 * Read-only application build and SBOM information.
 *
 * <p>This package answers one operational question — <em>"which build is running, and what is it
 * made of?"</em> — through a single service, {@link
 * com.openelements.spring.base.info.ApplicationInfoService}, and an immutable record model rooted at
 * {@link com.openelements.spring.base.info.ApplicationInfo}.
 *
 * <h2>Data sources</h2>
 *
 * <p>All inputs are produced by the build, never by this library:
 *
 * <ul>
 *   <li><b>Artifact coordinates</b> — from {@code META-INF/build-info.properties}, exposed by Spring
 *       Boot as {@link org.springframework.boot.info.BuildProperties}.
 *   <li><b>Git metadata</b> — from {@code META-INF/git.properties}, exposed as {@link
 *       org.springframework.boot.info.GitProperties}; with a fallback to {@code build.commit} in
 *       {@code build-info.properties} for container builds that have no {@code .git} directory.
 *   <li><b>SBOM</b> — a CycloneDX document on the classpath (default {@code
 *       META-INF/sbom/application.cdx.json}), parsed by the package-private {@link
 *       com.openelements.spring.base.info.CycloneDxReader}.
 * </ul>
 *
 * <h2>Design notes</h2>
 *
 * <ul>
 *   <li>The service is contributed by {@link
 *       com.openelements.spring.base.info.ApplicationInfoAutoConfiguration}, which is <em>not</em>
 *       guarded by JPA — application info must work in an application that has no persistence layer.
 *   <li>No build timestamp is exposed anywhere in the model: it would be a fixed reproducible-build
 *       constant, not an answer to "when was this deployed?".
 *   <li>The library exposes <b>no REST endpoint</b>. An SBOM is an attack-surface map, so the
 *       application owns the endpoint and, above all, its authorization.
 * </ul>
 */
@NullMarked
package com.openelements.spring.base.info;

import org.jspecify.annotations.NullMarked;
