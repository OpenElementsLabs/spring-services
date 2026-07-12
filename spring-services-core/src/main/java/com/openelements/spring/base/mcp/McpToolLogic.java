package com.openelements.spring.base.mcp;

import java.util.Map;

/**
 * The executable body of an MCP tool: validates and parses its
 * arguments, calls a backing service, and returns the payload object to
 * serialize as the tool result.
 *
 * <p>Thrown exceptions are mapped to JSON-RPC error results by
 * {@link McpToolSupport#spec}: {@link IllegalArgumentException} becomes an
 * invalid-argument error, {@link java.util.NoSuchElementException} a not-found
 * error, {@link McpUnavailableException} a temporary-unavailable error, and any
 * other exception a generic internal error.
 */
@FunctionalInterface
public interface McpToolLogic {

    /**
     * Runs the tool.
     *
     * @param args the tool arguments (never {@code null}; empty when none were sent)
     * @return the payload to serialize as the tool result
     * @throws Exception to signal an error, mapped to a JSON-RPC error result
     */
    Object run(Map<String, Object> args) throws Exception;
}
