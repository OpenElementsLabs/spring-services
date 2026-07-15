package com.openelements.spring.base.mcp;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

/**
 * Domain-agnostic helpers for building MCP tool input schemas and for parsing and
 * validating tool arguments.
 *
 * <p>Intended for static import by {@link McpToolProvider} implementations. The
 * schema builders produce the JSON-schema fragments the MCP SDK expects; the
 * argument accessors coerce the loosely typed argument map into Java types,
 * throwing {@link IllegalArgumentException} on malformed or missing required
 * input (which {@link McpToolSupport} maps to a JSON-RPC invalid-argument error).
 */
public final class McpTools {

    private McpTools() {
    }

    // -- Schema builders ----------------------------------------------------

    /**
     * Builds a tool with an {@code object} input schema.
     *
     * @param name        the tool name
     * @param description the tool description shown to the model
     * @param properties  the input properties (insertion order preserved)
     * @param required    the names of required properties
     * @return the tool definition
     */
    public static McpSchema.Tool tool(final String name, final String description,
                                      final Map<String, Object> properties, final List<String> required) {
        return McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(new McpSchema.JsonSchema("object", properties, required, Boolean.FALSE, null, null))
                .build();
    }

    /**
     * Builds the standard pagination input properties.
     *
     * @return a mutable property map pre-populated with the standard {@code page}
     * and {@code size} pagination properties
     */
    public static Map<String, Object> paginationProps() {
        final Map<String, Object> props = new LinkedHashMap<>();
        props.put("page", prop("integer", "Zero-based page index (default 0)."));
        props.put("size", prop("integer", "Page size (default 20, max 50)."));
        return props;
    }

    /**
     * Builds a schema fragment for a single typed property.
     *
     * @param type        the JSON-schema type (e.g. {@code "string"}, {@code "integer"})
     * @param description the property description
     * @return a single-property schema fragment
     */
    public static Map<String, Object> prop(final String type, final String description) {
        return Map.of("type", type, "description", description);
    }

    /**
     * Builds a schema fragment for a UUID-formatted string property.
     *
     * @param description the property description
     * @return a schema fragment for a UUID-formatted string property
     */
    public static Map<String, Object> uuidProp(final String description) {
        return Map.of("type", "string", "format", "uuid", "description", description);
    }

    /**
     * Builds a schema fragment for an array of UUID-formatted strings.
     *
     * @param description the property description
     * @return a schema fragment for an array of UUID-formatted strings
     */
    public static Map<String, Object> uuidArray(final String description) {
        return Map.of("type", "array", "description", description,
                "items", Map.of("type", "string", "format", "uuid"));
    }

    // -- Argument parsing ---------------------------------------------------

    /**
     * Reads an optional string argument.
     *
     * @param args the tool argument map
     * @param key  the argument name
     * @return the argument as a string, or {@code null} if absent
     */
    public static String string(final Map<String, Object> args, final String key) {
        final Object value = args.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * Reads a required, non-blank string argument.
     *
     * @param args the tool argument map
     * @param key  the argument name
     * @return the non-blank argument as a string
     * @throws IllegalArgumentException if absent or blank
     */
    public static String requiredString(final Map<String, Object> args, final String key) {
        final String value = string(args, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    /**
     * Reads an optional integer argument.
     *
     * @param args the tool argument map
     * @param key  the argument name
     * @return the argument as an integer, or {@code null} if absent
     * @throws IllegalArgumentException if present but not a valid integer
     */
    public static Integer integer(final Map<String, Object> args, final String key) {
        final Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
    }

    /**
     * Reads an optional boolean argument.
     *
     * @param args the tool argument map
     * @param key  the argument name
     * @return the argument as a boolean, or {@code null} if absent
     */
    public static Boolean bool(final Map<String, Object> args, final String key) {
        final Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.valueOf(value.toString().trim());
    }

    /**
     * Reads an optional UUID argument.
     *
     * @param args the tool argument map
     * @param key  the argument name
     * @return the argument as a UUID, or {@code null} if absent or blank
     * @throws IllegalArgumentException if present but not a valid UUID
     */
    public static UUID uuid(final Map<String, Object> args, final String key) {
        final String value = string(args, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(key + " must be a valid UUID");
        }
    }

    /**
     * Reads a required UUID argument.
     *
     * @param args the tool argument map
     * @param key  the argument name
     * @return the required argument as a UUID
     * @throws IllegalArgumentException if absent, blank, or not a valid UUID
     */
    public static UUID requiredUuid(final Map<String, Object> args, final String key) {
        final UUID value = uuid(args, key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    /**
     * Reads an optional array-of-UUIDs argument.
     *
     * @param args the tool argument map
     * @param key  the argument name
     * @return the argument as a list of UUIDs, or {@code null} if absent
     * @throws IllegalArgumentException if present but not an array of valid UUIDs
     */
    public static List<UUID> uuidList(final Map<String, Object> args, final String key) {
        final Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(key + " must be an array of UUIDs");
        }
        final List<UUID> result = new ArrayList<>(list.size());
        for (final Object element : list) {
            try {
                result.add(UUID.fromString(String.valueOf(element).trim()));
            } catch (final IllegalArgumentException e) {
                throw new IllegalArgumentException(key + " must contain valid UUIDs");
            }
        }
        return result;
    }
}
