package com.openelements.spring.base.mcp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Entry point for the generic MCP server support.
 *
 * <p>Binds {@link McpProperties} unconditionally so the {@code @ConditionalOnProperty}
 * MCP beans (security chain, server wiring) can read the master switch and the
 * per-profile toggles; the endpoint and server beans themselves are only created
 * when {@code openelements.mcp.enabled=true}.
 *
 * <p>Because these classes live outside an application's component-scan base
 * package, the application wires the feature in with
 * {@code @Import(McpConfiguration.class)} (mirroring how
 * {@code com.openelements.spring.base.FullSpringServiceConfig} pulls in the rest
 * of the library). Domain modules contribute tools by exposing
 * {@link McpToolProvider} beans, which {@code McpServerConfig} discovers and
 * aggregates.
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
@Import({McpPaging.class, McpToolSupport.class, McpSecurityConfig.class, McpServerConfig.class})
public class McpConfiguration {
}
