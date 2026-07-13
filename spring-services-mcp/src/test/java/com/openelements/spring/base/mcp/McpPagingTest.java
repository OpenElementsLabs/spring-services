package com.openelements.spring.base.mcp;

import com.openelements.spring.base.mcp.McpProperties.Auth;
import com.openelements.spring.base.mcp.McpProperties.Auth.ApiKey;
import com.openelements.spring.base.mcp.McpProperties.Auth.Oidc;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link McpPaging} clamping and {@link McpPage} envelope mapping.
 * No Spring context / database required.
 */
class McpPagingTest {

    private static final McpProperties PROPS = new McpProperties(
            true, "Open CRM", "0.1.0", 50, 20,
            new Auth(new ApiKey(true), new Oidc(false)));

    private final McpPaging paging = new McpPaging(PROPS);

    @Test
    void resolveSizeAppliesDefaultClampAndRejectsNonPositive() {
        assertEquals(20, paging.resolveSize(null), "default size");
        assertEquals(5, paging.resolveSize(5), "in-range size kept");
        assertEquals(50, paging.resolveSize(200), "clamped to max");
        assertEquals(50, paging.resolveSize(51), "just over max clamps");
        assertThrows(IllegalArgumentException.class, () -> paging.resolveSize(0));
        assertThrows(IllegalArgumentException.class, () -> paging.resolveSize(-5));
    }

    @Test
    void resolvePageDefaultsToZeroAndRejectsNegative() {
        assertEquals(0, paging.resolvePage(null));
        assertEquals(3, paging.resolvePage(3));
        assertThrows(IllegalArgumentException.class, () -> paging.resolvePage(-1));
    }

    @Test
    void toPageableUsesResolvedValues() {
        final var pageable = paging.toPageable(2, 100, org.springframework.data.domain.Sort.by("name"));
        assertEquals(2, pageable.getPageNumber());
        assertEquals(50, pageable.getPageSize());
        assertEquals(org.springframework.data.domain.Sort.by("name"), pageable.getSort());
    }

    @Test
    void envelopeSignalsMoreOnNonLastPage() {
        final McpPage<String> page = McpPage.from(
                new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5));

        assertEquals(2, page.items().size());
        assertEquals(0, page.page());
        assertEquals(2, page.size());
        assertEquals(5, page.totalCount());
        assertTrue(page.hasMore(), "first of three pages has more");
    }

    @Test
    void envelopeSignalsNoMoreOnLastPage() {
        final McpPage<String> page = McpPage.from(
                new PageImpl<>(List.of("e"), PageRequest.of(2, 2), 5));

        assertEquals(5, page.totalCount());
        assertFalse(page.hasMore(), "last page has no more");
    }
}
