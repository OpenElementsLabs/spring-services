package com.openelements.spring.base.mcp;

import io.modelcontextprotocol.common.McpTransportContext;

/**
 * Resolves a human-readable actor label for MCP access logging.
 *
 * <p>The label is captured on the request thread by the transport's context
 * extractor (see {@code McpServerConfig}) — the tool handler may run on a
 * different thread without the Spring security context — and stored in the
 * {@link McpTransportContext} under {@link #ACTOR_LABEL_KEY}. For the Phase 1
 * API-key profile the label is {@code "apikey:<key-name>"}.
 *
 * <p>This is used only for operational log lines; MCP reads are not written to
 * the {@code audit_log} (that table records mutations, and read access — like a
 * frontend record view — is not audited). See {@code docs/TODO.md} for a possible
 * future read-access audit hung off API keys / controller endpoints.
 */
final class McpActorLabel {

    /** Key under which the actor label is stored in the {@link McpTransportContext}. */
    static final String ACTOR_LABEL_KEY = "mcp.actor.label";

    private static final String UNKNOWN = "unknown";

    private McpActorLabel() {
    }

    /**
     * Reads the actor label from the transport context.
     *
     * @param context the transport context for the current tool call (may be {@code null})
     * @return the actor label, or {@code "unknown"} if none was captured
     */
    static String from(final McpTransportContext context) {
        final Object label = context == null ? null : context.get(ACTOR_LABEL_KEY);
        return label != null ? label.toString() : UNKNOWN;
    }
}
