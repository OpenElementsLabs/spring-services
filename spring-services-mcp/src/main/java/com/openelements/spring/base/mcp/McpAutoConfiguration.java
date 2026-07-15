package com.openelements.spring.base.mcp;

import io.modelcontextprotocol.server.McpServer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the optional Model Context Protocol (MCP) server feature.
 *
 * <p>Registered through this module's
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so the
 * feature self-activates when {@code spring-services-mcp} is on the classpath — the endpoint and its
 * dedicated security filter chain can no longer be accidentally present in an application that never
 * added the module. {@link ConditionalOnClass @ConditionalOnClass(McpServer.class)} keeps it inert
 * if the MCP SDK is absent, so a bundle shipping this class without the SDK causes no
 * {@code NoClassDefFoundError}.
 *
 * <p>Imports {@link McpConfiguration}, the feature entry point, which binds {@link McpProperties}
 * unconditionally and wires the server/security beans only when {@code openelements.mcp.enabled=true}
 * (unchanged {@code @ConditionalOnProperty} behaviour). {@code @Import(McpConfiguration.class)}
 * therefore remains valid for consumers who prefer explicit wiring.
 */
@AutoConfiguration
@ConditionalOnClass(McpServer.class)
@Import(McpConfiguration.class)
public class McpAutoConfiguration {

    /** Creates the auto-configuration. */
    public McpAutoConfiguration() {
    }
}
