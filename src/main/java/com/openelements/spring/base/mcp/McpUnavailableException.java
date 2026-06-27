package com.openelements.spring.base.mcp;

/**
 * Signals that an MCP tool cannot be served right now because a backing
 * subsystem is temporarily unavailable (e.g. the search index is still
 * bootstrapping). Mapped to a JSON-RPC error result by the tool dispatcher.
 */
public class McpUnavailableException extends RuntimeException {

    public McpUnavailableException(final String message) {
        super(message);
    }
}
