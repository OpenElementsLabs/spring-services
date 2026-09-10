package com.openelements.spring.base.info;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for {@link ApplicationInfoService}, bound from the {@code openelements.info.*}
 * namespace.
 *
 * <p>The {@code openelements.} prefix (rather than {@code open-elements.}) matches the prefixes used
 * by the {@code scim}, {@code mcp} and {@code db-backup} features; the inconsistency with
 * {@code slack} / {@code email} predates this feature.
 *
 * @param sbom SBOM-reading configuration; defaulted when the {@code openelements.info.sbom} block is
 *     omitted
 */
@ConfigurationProperties("openelements.info")
public record ApplicationInfoProperties(@DefaultValue Sbom sbom) {

  /**
   * SBOM-reading configuration.
   *
   * @param enabled whether the SBOM is read at all; when {@code false} no SBOM file is ever opened.
   *     Defaults to {@code true}
   * @param location an explicit Spring {@link org.springframework.core.io.Resource} location (e.g.
   *     {@code classpath:custom/my-bom.json} or {@code file:/etc/app/sbom.json}). When blank, the
   *     SBOM is autodetected. Defaults to blank
   */
  public record Sbom(@DefaultValue("true") boolean enabled, @DefaultValue("") String location) {}
}
