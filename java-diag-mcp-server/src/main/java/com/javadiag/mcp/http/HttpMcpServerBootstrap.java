package com.javadiag.mcp.http;

import com.javadiag.mcp.config.DiagConfig;
import com.javadiag.mcp.mcp.ToolRegistrar;
import com.javadiag.mcp.platform.JsonCodec;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.util.EnumSet;

/**
 * Embedded Jetty host for remote MCP over HTTP (SSE + Streamable HTTP).
 */
public final class HttpMcpServerBootstrap {

    public static final String SSE_ENDPOINT = "/sse";
    public static final String MESSAGE_ENDPOINT = "/mcp/message";
    public static final String STREAMABLE_ENDPOINT = "/mcp";

    private final DiagConfig config;
    private final ToolRegistrar registrar;
    private final JsonCodec codec;

    private Server jetty;
    private McpSyncServer sseMcp;
    private McpSyncServer streamableMcp;

    public HttpMcpServerBootstrap(DiagConfig config, ToolRegistrar registrar, JsonCodec codec) {
        this.config = config;
        this.registrar = registrar;
        this.codec = codec;
    }

    public void start() throws Exception {
        McpJsonMapper mapper = codec.mapper();

        HttpServletSseServerTransportProvider sseTransport = HttpServletSseServerTransportProvider.builder()
                .jsonMapper(mapper)
                .baseUrl("")
                .sseEndpoint(SSE_ENDPOINT)
                .messageEndpoint(MESSAGE_ENDPOINT)
                .build();

        HttpServletStreamableServerTransportProvider streamableTransport =
                HttpServletStreamableServerTransportProvider.builder()
                        .jsonMapper(mapper)
                        .mcpEndpoint(STREAMABLE_ENDPOINT)
                        .build();

        sseMcp = buildServer(sseTransport, mapper);
        streamableMcp = buildServer(streamableTransport, mapper);

        jetty = new Server();
        ServerConnector connector = new ServerConnector(jetty);
        connector.setHost(config.httpHost());
        connector.setPort(config.httpPort());
        jetty.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        jetty.setHandler(context);

        config.authToken().ifPresent(token -> context.addFilter(
                new FilterHolder(new BearerAuthFilter(token)),
                "/*",
                EnumSet.of(DispatcherType.REQUEST)));

        ServletHolder sseHolder = new ServletHolder(sseTransport);
        sseHolder.setAsyncSupported(true);
        context.addServlet(sseHolder, SSE_ENDPOINT);
        context.addServlet(sseHolder, MESSAGE_ENDPOINT);

        ServletHolder streamableHolder = new ServletHolder(streamableTransport);
        streamableHolder.setAsyncSupported(true);
        context.addServlet(streamableHolder, STREAMABLE_ENDPOINT);
        context.addServlet(streamableHolder, STREAMABLE_ENDPOINT + "/*");

        context.addServlet(new ServletHolder(new HealthServlet(config)), "/health");
        context.addServlet(new ServletHolder(new RootInfoServlet(config)), "/");

        jetty.start();

        System.err.println("java-diag MCP HTTP listening on http://"
                + config.httpHost() + ":" + config.httpPort());
        System.err.println("  SSE:        http://<host>:" + config.httpPort() + SSE_ENDPOINT);
        System.err.println("  Streamable: http://<host>:" + config.httpPort() + STREAMABLE_ENDPOINT);
        System.err.println("  Health:     http://<host>:" + config.httpPort() + "/health");
        System.err.println("  tools:      " + String.join(", ", registrar.toolNames()));
        if (config.authToken().isEmpty()) {
            System.err.println("  WARNING: JAVA_DIAG_AUTH_TOKEN is not set; remote MCP is unauthenticated");
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
            System.err.println("shutdown error: " + ex.getMessage());
        }
    }

    public int boundPort() {
        if (jetty == null || jetty.getConnectors().length == 0) {
            return config.httpPort();
        }
        return ((ServerConnector) jetty.getConnectors()[0]).getLocalPort();
    }

    private McpSyncServer buildServer(
            io.modelcontextprotocol.spec.McpServerTransportProvider transport,
            McpJsonMapper mapper) {
        return McpServer.sync(transport)
                .serverInfo("java-diag", config.version())
                .instructions("Read-only Java diagnostics MCP (remote HTTP). Tools are prefixed with diag.*")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .strictToolNameValidation(false)
                .jsonMapper(mapper)
                .tools(registrar.specifications())
                .build();
    }

    private McpSyncServer buildServer(
            io.modelcontextprotocol.spec.McpStreamableServerTransportProvider transport,
            McpJsonMapper mapper) {
        return McpServer.sync(transport)
                .serverInfo("java-diag", config.version())
                .instructions("Read-only Java diagnostics MCP (remote HTTP). Tools are prefixed with diag.*")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .strictToolNameValidation(false)
                .jsonMapper(mapper)
                .tools(registrar.specifications())
                .build();
    }
}
