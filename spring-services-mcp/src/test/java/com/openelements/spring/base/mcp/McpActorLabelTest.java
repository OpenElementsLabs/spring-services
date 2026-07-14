package com.openelements.spring.base.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link McpActorLabel} transport-context label resolution.
 * Lives in the same package so it can exercise the package-private
 * helper directly.
 */
class McpActorLabelTest {

    @Test
    void returnsLabelStoredUnderTheActorKey() {
        final McpTransportContext context = McpTransportContext.create(
                Map.of(McpActorLabel.ACTOR_LABEL_KEY, "apikey:onyx"));

        assertEquals("apikey:onyx", McpActorLabel.from(context));
    }

    @Test
    void fallsBackToUnknownForNullContext() {
        assertEquals("unknown", McpActorLabel.from(null));
    }

    @Test
    void fallsBackToUnknownWhenLabelIsAbsent() {
        assertEquals("unknown", McpActorLabel.from(McpTransportContext.EMPTY));
        assertEquals("unknown", McpActorLabel.from(McpTransportContext.create(Map.of("other", "value"))));
    }
}
