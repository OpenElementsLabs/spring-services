package com.openelements.spring.base.problem;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.Path.Node;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Turns a Bean Validation {@link Path} into an RFC 6901 JSON pointer into the request body.
 *
 * <p>A validation error has to point at the request the client sent, not at the Java object the
 * server bound it to. Those two differ whenever {@code @JsonProperty} renames a field or a {@link
 * com.fasterxml.jackson.databind.PropertyNamingStrategy} is configured, which is the normal case for
 * a snake_case API:
 *
 * <pre>{@code
 * record Address(@JsonProperty("postal_code") @Pattern(regexp = "[0-9]{5}") String postalCode) {}
 * record Order(@Valid Address address) {}
 *
 * address.postalCode   (Bean Validation)
 * /address/postal_code (what the client sent, and what this class produces)
 * }</pre>
 *
 * <p>Each property name is resolved through Jackson's {@link BeanDescription} of the type that
 * <em>declares</em> it, so the mapping is the application's own Jackson configuration rather than a
 * guess. A type Jackson cannot introspect falls back to the Java property name for that one segment
 * — a slightly wrong pointer is more useful to a client than no error at all.
 *
 * <p>Indexes and map keys become their own segments ({@code items[0].name} → {@code /items/0/name}),
 * and keys are escaped per RFC 6901 §3: {@code ~} → {@code ~0}, {@code /} → {@code ~1}, in that
 * order. A class-level constraint has no property segment and yields the pointer of the object it
 * sits on — {@code ""} for the body root, which RFC 6901 defines as "the whole document".
 */
final class JsonPointers {

  private final ObjectMapper objectMapper;

  JsonPointers(final ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  /**
   * Builds the JSON pointer for a validation failure on the given path.
   *
   * @param rootType the Java type of the request body, used to start the name resolution
   * @param path the Bean Validation property path of the violation
   * @return an RFC 6901 pointer, {@code ""} for a constraint on the body root
   */
  String pointer(final Class<?> rootType, final Path path) {
    Objects.requireNonNull(rootType, "rootType must not be null");
    Objects.requireNonNull(path, "path must not be null");

    final StringBuilder pointer = new StringBuilder();
    JavaType currentType = objectMapper.getTypeFactory().constructType(rootType);

    for (final Node node : path) {
      if (!isBodyNode(node)) {
        continue;
      }
      // Bean Validation reports the position inside a container on the node *below* it:
      // items[0].name is "items", then "name" with isInIterable() and index 0. The index therefore
      // has to be written before this node's own name, not after it.
      if (node.isInIterable()) {
        currentType = appendContainerSegment(pointer, node, currentType);
      }
      // A container-element node is named "<list element>" or "<map value>" — a placeholder for the
      // position already written above, not a property of the JSON document.
      if (node.getKind() == ElementKind.CONTAINER_ELEMENT) {
        continue;
      }
      final String property = node.getName();
      if (property != null) {
        final String jsonName = jsonName(currentType, property);
        pointer.append('/').append(escape(jsonName));
        currentType = propertyType(currentType, property);
      }
    }
    return pointer.toString();
  }

  /**
   * Decides whether a path node describes a position inside the request body.
   *
   * <p>Method-level validation prefixes the path with the method and its parameter; neither exists
   * in the JSON document, so both are skipped. {@code BEAN} nodes carry a class-level constraint and
   * contribute no segment of their own.
   *
   * @param node the path node
   * @return {@code true} if the node contributes to the pointer
   */
  private static boolean isBodyNode(final Node node) {
    return switch (node.getKind()) {
      case METHOD, PARAMETER, CONSTRUCTOR, RETURN_VALUE, CROSS_PARAMETER, BEAN -> false;
      default -> true;
    };
  }

  /**
   * Appends the index or map key the node carries, and descends into the container's element type.
   *
   * @param pointer the pointer being built
   * @param node the path node
   * @param currentType the type the node resolved to
   * @return the type to resolve the next node against
   */
  private static JavaType appendContainerSegment(
      final StringBuilder pointer, final Node node, final JavaType currentType) {
    final Object key = node.getKey();
    final Integer index = node.getIndex();
    if (key != null) {
      pointer.append('/').append(escape(String.valueOf(key)));
    } else if (index != null) {
      pointer.append('/').append(index);
    }
    return currentType.getContentType() != null ? currentType.getContentType() : currentType;
  }

  /**
   * Resolves a Java property name to the name Jackson reads and writes for it.
   *
   * @param owner the type declaring the property
   * @param property the Java property name from the Bean Validation path
   * @return the JSON name, or {@code property} when the type cannot be introspected
   */
  private String jsonName(final JavaType owner, final String property) {
    final BeanPropertyDefinition definition = findProperty(owner, property);
    return definition != null ? definition.getName() : property;
  }

  /**
   * Resolves the declared type of a property, so the next segment is looked up on the right class.
   *
   * @param owner the type declaring the property
   * @param property the Java property name
   * @return the property's type, or {@code owner} when it cannot be resolved
   */
  private JavaType propertyType(final JavaType owner, final String property) {
    final BeanPropertyDefinition definition = findProperty(owner, property);
    if (definition == null || definition.getPrimaryType() == null) {
      return owner;
    }
    return definition.getPrimaryType();
  }

  /**
   * Looks a property up in Jackson's description of a type.
   *
   * @param owner the type to introspect
   * @param property the Java property name
   * @return the matching definition, or {@code null} if the type has no such property or cannot be
   *     introspected at all
   */
  private @Nullable BeanPropertyDefinition findProperty(
      final JavaType owner, final String property) {
    if (owner.isPrimitive() || owner.getRawClass().getName().startsWith("java.")) {
      return null;
    }
    final BeanDescription description;
    try {
      description = objectMapper.getSerializationConfig().introspect(owner);
    } catch (final RuntimeException e) {
      return null;
    }
    return description.findProperties().stream()
        .filter(candidate -> property.equals(candidate.getInternalName()))
        .findFirst()
        .orElse(null);
  }

  /**
   * Escapes a pointer segment per RFC 6901 §3.
   *
   * <p>{@code ~} first, then {@code /} — the other order would turn an escaped slash's {@code ~1}
   * back into {@code ~01}.
   *
   * @param segment the raw segment
   * @return the escaped segment
   */
  private static String escape(final String segment) {
    return segment.replace("~", "~0").replace("/", "~1");
  }
}
