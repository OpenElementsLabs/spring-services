package com.openelements.spring.base.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;

import java.util.List;

/**
 * SPI for contributing MCP tools to the server.
 *
 * <p>Each feature module implements this interface and is discovered as a Spring
 * bean. {@code McpServerConfig} aggregates the tool specifications of all
 * providers into the single {@code McpSyncServer}, so a module adds tools without
 * touching the generic server wiring.
 *
 * <p>Implementations typically build their specifications with {@link McpTools}
 * (schema and argument helpers) and {@link McpToolSupport#spec} (dispatch, access
 * logging, and JSON-RPC error mapping).
 */
public interface McpToolProvider {

    /**
     * Returns the MCP tool specifications contributed by this provider.
     *
     * @return the tool specifications contributed by this provider
     */
    List<SyncToolSpecification> toolSpecifications();
}
