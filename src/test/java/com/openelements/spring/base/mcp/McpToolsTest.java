package com.openelements.spring.base.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the domain-agnostic schema builders and argument accessors in
 * {@link McpTools}. No Spring context required.
 */
class McpToolsTest {

    private static final String VALID_UUID = "11111111-1111-1111-1111-111111111111";

    // -- Schema builders -----------------------------------------------------

    @Test
    void toolBuildsObjectSchemaWithPropertiesAndRequired() {
        final McpSchema.Tool tool = McpTools.tool(
                "do_thing", "Does a thing.",
                Map.of("id", McpTools.uuidProp("the id")),
                List.of("id"));

        assertEquals("do_thing", tool.name());
        assertEquals("Does a thing.", tool.description());

        final McpSchema.JsonSchema schema = tool.inputSchema();
        assertEquals("object", schema.type());
        assertTrue(schema.properties().containsKey("id"));
        assertIterableEquals(List.of("id"), schema.required());
        assertFalse(schema.additionalProperties(), "additionalProperties must be locked to false");
    }

    @Test
    void paginationPropsExposesPageAndSizeAndIsMutable() {
        final Map<String, Object> props = McpTools.paginationProps();

        assertTrue(props.containsKey("page"));
        assertTrue(props.containsKey("size"));
        // callers add their own filters on top, so the map must be mutable
        props.put("name", McpTools.prop("string", "filter"));
        assertEquals(3, props.size());
    }

    @Test
    void propBuildsTypeAndDescription() {
        assertEquals(Map.of("type", "integer", "description", "count"),
                McpTools.prop("integer", "count"));
    }

    @Test
    void uuidPropAndUuidArrayCarryUuidFormat() {
        final Map<String, Object> single = McpTools.uuidProp("the id");
        assertEquals("string", single.get("type"));
        assertEquals("uuid", single.get("format"));

        final Map<String, Object> array = McpTools.uuidArray("the ids");
        assertEquals("array", array.get("type"));
        assertEquals(Map.of("type", "string", "format", "uuid"), array.get("items"));
    }

    // -- String arguments ----------------------------------------------------

    @Test
    void stringReturnsValueOrNullAndCoercesNonStrings() {
        assertEquals("hello", McpTools.string(Map.of("q", "hello"), "q"));
        assertNull(McpTools.string(Map.of(), "q"));
        assertEquals("42", McpTools.string(Map.of("q", 42), "q"));
    }

    @Test
    void requiredStringRejectsMissingAndBlank() {
        assertEquals("x", McpTools.requiredString(Map.of("q", "x"), "q"));
        assertThrows(IllegalArgumentException.class, () -> McpTools.requiredString(Map.of(), "q"));
        assertThrows(IllegalArgumentException.class, () -> McpTools.requiredString(Map.of("q", "  "), "q"));
    }

    // -- Integer arguments ---------------------------------------------------

    @Test
    void integerAcceptsNumbersAndNumericStrings() {
        assertEquals(5, McpTools.integer(Map.of("n", 5), "n"));
        assertEquals(7, McpTools.integer(Map.of("n", "7"), "n"));
        assertEquals(3, McpTools.integer(Map.of("n", " 3 "), "n"), "surrounding whitespace is trimmed");
    }

    @Test
    void integerReturnsNullWhenAbsentAndRejectsNonNumeric() {
        assertNull(McpTools.integer(Map.of(), "n"));
        assertThrows(IllegalArgumentException.class, () -> McpTools.integer(Map.of("n", "abc"), "n"));
    }

    // -- Boolean arguments ---------------------------------------------------

    @Test
    void boolAcceptsBooleanAndStringAndNull() {
        assertTrue(McpTools.bool(Map.of("b", true), "b"));
        assertTrue(McpTools.bool(Map.of("b", "true"), "b"));
        assertFalse(McpTools.bool(Map.of("b", "false"), "b"));
        assertNull(McpTools.bool(Map.of(), "b"));
    }

    @Test
    void boolTreatsNonBooleanTextAsFalse() {
        assertFalse(McpTools.bool(Map.of("b", "yes"), "b"),
                "Boolean.valueOf maps anything but \"true\" to false");
    }

    // -- UUID arguments ------------------------------------------------------

    @Test
    void uuidParsesValidValueAndNullsBlank() {
        assertEquals(UUID.fromString(VALID_UUID), McpTools.uuid(Map.of("id", VALID_UUID), "id"));
        assertNull(McpTools.uuid(Map.of(), "id"));
        assertNull(McpTools.uuid(Map.of("id", "   "), "id"));
    }

    @Test
    void uuidRejectsMalformedValue() {
        assertThrows(IllegalArgumentException.class, () -> McpTools.uuid(Map.of("id", "not-a-uuid"), "id"));
    }

    @Test
    void requiredUuidRejectsMissing() {
        assertEquals(UUID.fromString(VALID_UUID), McpTools.requiredUuid(Map.of("id", VALID_UUID), "id"));
        assertThrows(IllegalArgumentException.class, () -> McpTools.requiredUuid(Map.of(), "id"));
    }

    @Test
    void uuidListParsesElementsAndNullsAbsent() {
        final String other = "22222222-2222-2222-2222-222222222222";
        assertEquals(List.of(UUID.fromString(VALID_UUID), UUID.fromString(other)),
                McpTools.uuidList(Map.of("ids", List.of(VALID_UUID, other)), "ids"));
        assertNull(McpTools.uuidList(Map.of(), "ids"));
    }

    @Test
    void uuidListRejectsNonListAndMalformedElements() {
        assertThrows(IllegalArgumentException.class, () -> McpTools.uuidList(Map.of("ids", VALID_UUID), "ids"));
        assertThrows(IllegalArgumentException.class,
                () -> McpTools.uuidList(Map.of("ids", List.of("nope")), "ids"));
    }
}
