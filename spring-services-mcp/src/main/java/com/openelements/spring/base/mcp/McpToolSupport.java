package com.openelements.spring.base.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generic runtime support for MCP tool implementations.
 *
 * <p>{@link #spec} wraps a tool's {@link McpToolLogic} into a
 * {@link SyncToolSpecification} that resolves the calling actor, runs the logic,
 * serializes the payload to JSON, logs one structured access line
 * ({@code tool=… actor=…}, never the arguments), and maps failures to JSON-RPC
 * error results. {@link #paginate} slices a fully materialized list into the
 * {@link McpPage} envelope using the configured paging defaults and clamps.
 *
 * <p>This class carries no domain knowledge; {@link McpToolProvider}
 * implementations build their schemas and parse arguments with {@link McpTools}.
 *
 * <p>MCP reads are <em>not</em> written to an audit log: read access (like
 * viewing a record in a frontend) is recorded only as a structured INFO log line.
 */
@Component
public class McpToolSupport {

    private static final Logger log = LoggerFactory.getLogger(McpToolSupport.class);

    private final McpPaging paging;
    private final ObjectMapper objectMapper;

    public McpToolSupport(final McpPaging paging, final ObjectMapper objectMapper) {
        this.paging = Objects.requireNonNull(paging, "paging must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Wraps tool logic into a sync tool specification with access logging and
     * JSON-RPC error mapping.
     *
     * @param tool  the tool definition (see {@link McpTools#tool})
     * @param logic the tool body
     * @return the sync tool specification
     */
    public SyncToolSpecification spec(final McpSchema.Tool tool, final McpToolLogic logic) {
        return new SyncToolSpecification(tool, (exchange, args) ->
                invoke(tool.name(), logic, args, McpActorLabel.from(exchange.transportContext())));
    }

    private McpSchema.CallToolResult invoke(final String name, final McpToolLogic logic,
                                            final Map<String, Object> rawArgs, final String actor) {
        final Map<String, Object> args = rawArgs == null ? Map.of() : rawArgs;
        try {
            final Object payload = logic.run(args);
            log.info("MCP tool call tool={} actor={}", name, actor);
            return new McpSchema.CallToolResult(json(payload), false);
        } catch (final IllegalArgumentException e) {
            log.info("MCP tool call rejected tool={} actor={} reason=invalid-argument", name, actor);
            return new McpSchema.CallToolResult("Invalid argument: " + e.getMessage(), true);
        } catch (final NoSuchElementException e) {
            log.info("MCP tool call not-found tool={} actor={}", name, actor);
            return new McpSchema.CallToolResult(e.getMessage(), true);
        } catch (final McpUnavailableException e) {
            log.info("MCP tool call unavailable tool={} actor={}", name, actor);
            return new McpSchema.CallToolResult(e.getMessage(), true);
        } catch (final Exception e) {
            log.warn("MCP tool call errored tool={} actor={} error={}", name, actor, e.getClass().getSimpleName());
            return new McpSchema.CallToolResult("Internal error executing tool " + name, true);
        }
    }

    private String json(final Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize MCP tool payload", e);
        }
    }

    /**
     * Slices a fully materialized list into the {@link McpPage} envelope using the
     * configured paging defaults and clamps.
     *
     * @param all     the full result list
     * @param pageReq the requested zero-based page, or {@code null}
     * @param sizeReq the requested page size, or {@code null}
     * @param <T>     the item type
     * @return the page envelope
     */
    public <T> McpPage<T> paginate(final List<T> all, final Integer pageReq, final Integer sizeReq) {
        final int size = paging.resolveSize(sizeReq);
        final int page = paging.resolvePage(pageReq);
        final int from = Math.min(page * size, all.size());
        final int to = Math.min(from + size, all.size());
        return new McpPage<>(new ArrayList<>(all.subList(from, to)), page, size, all.size(), to < all.size());
    }
}
