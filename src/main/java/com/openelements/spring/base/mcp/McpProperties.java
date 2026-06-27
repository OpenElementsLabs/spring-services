package com.openelements.spring.base.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the Model Context Protocol (MCP) server.
 *
 * <p>Bound from the {@code openelements.mcp.*} namespace. The {@link #enabled()}
 * master switch defaults to {@code false}: the MCP endpoint exposes personal CRM
 * data to external clients and must be turned on deliberately per environment,
 * after the Phase-1 GDPR checklist is satisfied.
 *
 * <p>The two auth profiles are toggled independently: Phase 1 uses
 * {@code auth.api-key} (Onyx via {@code X-API-Key}); Phase 2 will add
 * {@code auth.oidc} (Claude via per-user Authentik OIDC).
 *
 * @param enabled         master switch; when {@code false} no MCP endpoint or bean is registered
 * @param serverName      MCP server name advertised to clients (shown in their tool picker); generic default, override per deployment
 * @param serverVersion   MCP server version advertised to clients; generic default, override per deployment
 * @param maxPageSize     hard upper bound for paginated tools; requested sizes are clamped to this
 * @param defaultPageSize page size used when a tool call omits {@code size}
 * @param auth            per-profile authentication switches
 */
@ConfigurationProperties(prefix = "openelements.mcp")
public record McpProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("MCP Server") String serverName,
        @DefaultValue("0.0.0") String serverVersion,
        @DefaultValue("50") int maxPageSize,
        @DefaultValue("20") int defaultPageSize,
        @DefaultValue Auth auth
) {

    /**
     * Authentication profile switches.
     *
     * @param apiKey Phase 1 — API-key profile (Onyx)
     * @param oidc   Phase 2 — per-user OIDC profile (Claude)
     */
    public record Auth(
            @DefaultValue ApiKey apiKey,
            @DefaultValue Oidc oidc
    ) {

        /**
         * API-key auth profile (Phase 1).
         *
         * @param enabled whether the {@code X-API-Key} profile is active on {@code /mcp}
         */
        public record ApiKey(@DefaultValue("true") boolean enabled) {
        }

        /**
         * Per-user OIDC auth profile (Phase 2, not yet implemented).
         *
         * @param enabled whether the OIDC profile is active on {@code /mcp}
         */
        public record Oidc(@DefaultValue("false") boolean enabled) {
        }
    }
}
