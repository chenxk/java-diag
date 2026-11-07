package com.javadiag.gateway.http;

import com.javadiag.gateway.config.GatewayConfig;
import com.javadiag.gateway.json.JsonSupport;
import com.javadiag.gateway.mcp.GatewayToolRegistrar;
import com.javadiag.gateway.registry.InMemoryRegistry;
import com.javadiag.gateway.registry.RegistryServlet;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

public final class GatewayHttpBootstrap {

    private final GatewayConfig config;
    private final InMemoryRegistry registry;
    private final JsonSupport json;
    private final GatewayToolRegistrar tools;

    private Server jetty;
    private McpSyncServer sseMcp;
    private McpSyncServer streamableMcp;

    public GatewayHttpBootstrap(
            GatewayConfig config,
            InMemoryRegistry registry,
            JsonSupport json,
            GatewayToolRegistrar tools) {
        this.config = config;
        this.registry = registry;
        this.json = json;
        this.tools = tools;
    }

    public void start() throws Exception {
        HttpServletSseServerTransportProvider sseTransport = HttpServletSseServerTransportProvider.builder()
                .jsonMapper(json.mapper())
                .baseUrl("")
                .sseEndpoint("/sse")
                .messageEndpoint("/mcp/message")
                .build();
        HttpServletStreamableServerTransportProvider streamableTransport =
                HttpServletStreamableServerTransportProvider.builder()
                        .jsonMapper(json.mapper())
                        .mcpEndpoint("/mcp")
                        .build();

        sseMcp = buildMcp(sseTransport);
        streamableMcp = buildMcp(streamableTransport);

        jetty = new Server();
        ServerConnector connector = new ServerConnector(jetty);
        connector.setHost(config.httpHost());
        connector.setPort(config.httpPort());
        jetty.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        jetty.setHandler(context);

        config.authToken().ifPresent(token -> context.addFilter(
                new FilterHolder(new AgentAuthFilter(token)),
                "/*",
                EnumSet.of(DispatcherType.REQUEST)));

        ServletHolder sseHolder = new ServletHolder(sseTransport);
        sseHolder.setAsyncSupported(true);
        context.addServlet(sseHolder, "/sse");
        context.addServlet(sseHolder, "/mcp/message");

        ServletHolder streamableHolder = new ServletHolder(streamableTransport);
        streamableHolder.setAsyncSupported(true);
        context.addServlet(streamableHolder, "/mcp");
        context.addServlet(streamableHolder, "/mcp/*");

        context.addServlet(
                new ServletHolder(new RegistryServlet(registry, json)),
                "/registry/*");
        context.addServlet(new ServletHolder(new HealthServlet()), "/health");
        context.addServlet(new ServletHolder(new RootServlet()), "/");

        jetty.start();
        System.err.println("java-diag gateway listening on http://" + config.httpHost() + ":" + config.httpPort());
        System.err.println("  Agent SSE:     /sse");
        System.err.println("  Agent MCP:     /mcp");
        System.err.println("  Registry:      /registry/register|heartbeat|deregister|instances");
        System.err.println("  tools:         " + String.join(", ", tools.toolNames()));
        if (config.authToken().isEmpty()) {
            System.err.println("  WARNING: agent auth token not set (--token / JAVA_DIAG_AUTH_TOKEN)");
        }
    }

    public void await() throws InterruptedException {
        if (jetty != null) {
            jetty.join();
        }
    }

    public void stop() {
        try {
            if (sseMcp != null) {
                sseMcp.closeGracefully();
            }
            if (streamableMcp != null) {
                streamableMcp.closeGracefully();
            }
            if (jetty != null) {
                jetty.stop();
            }
        } catch (Exception ex) {
            System.err.println("gateway shutdown error: " + ex.getMessage());
        }
    }

    public int boundPort() {
        return ((ServerConnector) jetty.getConnectors()[0]).getLocalPort();
    }

    private McpSyncServer buildMcp(io.modelcontextprotocol.spec.McpServerTransportProvider transport) {
        return McpServer.sync(transport)
                .serverInfo("java-diag-gateway", config.version())
                .instructions("Cluster gateway. Use diag.list_instances then pass target.instanceId for tools.")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .strictToolNameValidation(false)
                .jsonMapper(json.mapper())
                .tools(tools.specifications())
                .build();
    }

    private McpSyncServer buildMcp(io.modelcontextprotocol.spec.McpStreamableServerTransportProvider transport) {
        return McpServer.sync(transport)
                .serverInfo("java-diag-gateway", config.version())
                .instructions("Cluster gateway. Use diag.list_instances then pass target.instanceId for tools.")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .strictToolNameValidation(false)
                .jsonMapper(json.mapper())
                .tools(tools.specifications())
                .build();
    }

    private final class HealthServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            registry.refresh();
            String body = "{\"ok\":true,\"service\":\"java-diag-gateway\",\"version\":\""
                    + config.version() + "\",\"registeredProbes\":" + registry.size()
                    + ",\"heartbeatTtlSeconds\":" + config.heartbeatTtlSeconds() + "}";
            resp.setStatus(200);
            resp.setContentType("application/json; charset=utf-8");
            resp.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private final class RootServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String body = """
                    java-diag-gateway %s
                    Agent MCP:  /sse  and  /mcp
                    Registry:   /registry/*
                    Health:     /health
                    Direct probe access remains allowed by design.
                    """.formatted(config.version());
            resp.setStatus(200);
            resp.setContentType("text/plain; charset=utf-8");
            resp.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
    }
}
