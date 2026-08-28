package com.openelements.spring.base.info;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

/**
 * Package-private parser that turns a CycloneDX JSON SBOM into an {@link SbomDocument}.
 *
 * <p>The reader walks a Jackson {@link JsonNode} tree and extracts only the fields the model needs,
 * rather than binding to {@code cyclonedx-core-java} — which would pull {@code packageurl-java}, an
 * XML dataformat and a schema validator into a module contractually kept free of heavy optional
 * dependencies. Tree parsing also ignores unknown fields, so a document from a newer CycloneDX spec
 * version still yields its components.
 *
 * <p>The reader never throws: a missing resource yields an empty {@link Optional} silently (an
 * absent SBOM is a normal development state), while a present-but-unreadable resource — malformed
 * JSON, an I/O error, or a document that is not CycloneDX — yields an empty {@link Optional} and a
 * single {@code WARN} naming the resource.
 */
class CycloneDxReader {

  private static final Logger LOG = LoggerFactory.getLogger(CycloneDxReader.class);

  private static final String CYCLONE_DX = "CycloneDX";

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Creates a reader with its own {@link ObjectMapper}. */
  CycloneDxReader() {}

  /**
   * Reads and parses the given SBOM resource.
   *
   * @param resource the resource to read, or {@code null}
   * @return the parsed document, or empty if the resource is absent, unreadable, or not a CycloneDX
   *     document
   */
  Optional<SbomDocument> read(final @Nullable Resource resource) {
    if (resource == null || !resource.exists()) {
      return Optional.empty();
    }
    final JsonNode root;
    try (InputStream in = resource.getInputStream()) {
      root = objectMapper.readTree(in);
    } catch (final IOException e) {
      LOG.warn("Failed to read SBOM from {}: {}", resource.getDescription(), e.getMessage());
      return Optional.empty();
    }
    if (root == null || !root.isObject()) {
      LOG.warn("SBOM at {} is not a JSON object; ignoring it", resource.getDescription());
      return Optional.empty();
    }
    final String bomFormat = text(root, "bomFormat");
    if (bomFormat != null && !CYCLONE_DX.equals(bomFormat)) {
      LOG.warn(
          "SBOM at {} declares bomFormat '{}', not CycloneDX; ignoring it",
          resource.getDescription(),
          bomFormat);
      return Optional.empty();
    }
    return Optional.of(parse(root, bomFormat));
  }

  private SbomDocument parse(final JsonNode root, final @Nullable String bomFormat) {
    final List<SbomComponent> components = parseComponents(root.get("components"));
    final SbomSummary summary =
        new SbomSummary(
            bomFormat,
            text(root, "specVersion"),
            text(root, "serialNumber"),
            parseApplicationComponent(root.get("metadata")),
            components.size(),
            aggregateLicenses(components));
    return new SbomDocument(summary, components);
  }

  private @Nullable SbomComponent parseApplicationComponent(final @Nullable JsonNode metadata) {
    if (metadata == null) {
      return null;
    }
    final JsonNode component = metadata.get("component");
    if (component == null || !component.isObject()) {
      return null;
    }
    return parseComponent(component);
  }

  private List<SbomComponent> parseComponents(final @Nullable JsonNode componentsNode) {
    if (componentsNode == null || !componentsNode.isArray()) {
      return List.of();
    }
    final List<SbomComponent> components = new ArrayList<>();
    for (final JsonNode component : componentsNode) {
      if (component.isObject()) {
        components.add(parseComponent(component));
      }
    }
    return components;
  }

  private SbomComponent parseComponent(final JsonNode node) {
    final String name = text(node, "name");
    return new SbomComponent(
        text(node, "group"),
        name == null ? "" : name,
        text(node, "version"),
        text(node, "type"),
        text(node, "purl"),
        parseLicenses(node.get("licenses")));
  }

  private List<String> parseLicenses(final @Nullable JsonNode licensesNode) {
    if (licensesNode == null || !licensesNode.isArray()) {
      return List.of();
    }
    final List<String> licenses = new ArrayList<>();
    for (final JsonNode entry : licensesNode) {
      final String value = licenseValue(entry);
      if (value != null) {
        licenses.add(value);
      }
    }
    return licenses;
  }

  /**
   * Collapses one CycloneDX {@code licenses[]} entry to a single string, preferring an SPDX id over
   * a free-text name over an expression.
   */
  private @Nullable String licenseValue(final JsonNode entry) {
    final JsonNode license = entry.get("license");
    if (license != null && license.isObject()) {
      final String id = text(license, "id");
      if (id != null) {
        return id;
      }
      final String name = text(license, "name");
      if (name != null) {
        return name;
      }
    }
    return text(entry, "expression");
  }

  /** Distinct, sorted union of every license declared by any component. */
  private List<String> aggregateLicenses(final List<SbomComponent> components) {
    final TreeSet<String> distinct = new TreeSet<>();
    for (final SbomComponent component : components) {
      distinct.addAll(component.licenses());
    }
    return List.copyOf(distinct);
  }

  /**
   * Returns the trimmed text value of {@code field} on {@code node}, or {@code null} if the field is
   * absent, {@code null}, a non-scalar, or blank.
   */
  private static @Nullable String text(final @Nullable JsonNode node, final String field) {
    if (node == null) {
      return null;
    }
    final JsonNode value = node.get(field);
    if (value == null || value.isNull() || !value.isValueNode()) {
      return null;
    }
    final String text = value.asText();
    return text.isBlank() ? null : text;
  }
}
