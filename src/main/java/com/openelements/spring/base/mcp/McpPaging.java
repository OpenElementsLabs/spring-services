package com.openelements.spring.base.mcp;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Resolves and clamps the {@code page}/{@code size} parameters of paginated MCP
 * tools against {@link McpProperties}.
 *
 * <p>An omitted {@code size} falls back to the configured default; a {@code size}
 * above the configured maximum is clamped down; a non-positive {@code size} or a
 * negative {@code page} is rejected with {@link IllegalArgumentException} (mapped
 * to a JSON-RPC invalid-parameter error in the tool layer).
 */
@Component
public class McpPaging {

    private final McpProperties properties;

    public McpPaging(final McpProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * Resolves the effective page size: default when {@code null}, clamped to the
     * configured maximum, rejecting non-positive values.
     *
     * @param requested the requested size, or {@code null}
     * @return the effective size in {@code [1, maxPageSize]}
     */
    public int resolveSize(final Integer requested) {
        if (requested == null) {
            return properties.defaultPageSize();
        }
        if (requested <= 0) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        return Math.min(requested, properties.maxPageSize());
    }

    /**
     * Resolves the effective page index: {@code 0} when {@code null}, rejecting negatives.
     *
     * @param requested the requested zero-based page, or {@code null}
     * @return the effective page index
     */
    public int resolvePage(final Integer requested) {
        if (requested == null) {
            return 0;
        }
        if (requested < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        return requested;
    }

    /**
     * Builds a sorted {@link Pageable} from the requested parameters.
     *
     * @param page the requested page, or {@code null}
     * @param size the requested size, or {@code null}
     * @param sort the sort to apply
     * @return the resolved pageable
     */
    public Pageable toPageable(final Integer page, final Integer size, final Sort sort) {
        return PageRequest.of(resolvePage(page), resolveSize(size), sort);
    }
}
