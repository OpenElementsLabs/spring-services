package com.openelements.spring.base.mcp;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Paginated tool-response envelope.
 *
 * <p>Collection tools return this instead of a bare array so the model can tell
 * whether it has seen everything: {@link #hasMore()} together with
 * {@link #totalCount()} signals that further pages exist, preventing answers from
 * a silently truncated first page.
 *
 * @param items      the items on this page (at most {@link #size()})
 * @param page       the zero-based page index
 * @param size       the page size used for this response
 * @param totalCount the total number of matching items across all pages
 * @param hasMore    {@code true} if at least one more page exists after this one
 * @param <T>        the item type
 */
public record McpPage<T>(
        List<T> items,
        int page,
        int size,
        long totalCount,
        boolean hasMore
) {

    /**
     * Wraps a Spring Data {@link Page} into the MCP envelope.
     *
     * @param page the source page
     * @param <T>  the item type
     * @return the envelope
     */
    public static <T> McpPage<T> from(final Page<T> page) {
        return new McpPage<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                !page.isLast()
        );
    }
}
