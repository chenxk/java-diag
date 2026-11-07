package com.javadiag.mcp;

import com.javadiag.mcp.config.DiagConfig;
import com.javadiag.mcp.http.HttpMcpServerBootstrap;
import com.javadiag.mcp.mcp.ToolRegistrar;
import com.javadiag.mcp.platform.JsonCodec;
import com.javadiag.mcp.tools.ToolSupport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Arrays;
import java.util.List;

/**
 * Entry point: MCP server over HTTP (cloud IP:port) or stdio (local IDE).
 */
public final class JavaDiagMcpServer {

    public static void main(String[] args) throws Exception {
        List<String> argList = Arrays.asList(args);
        if (argList.contains("--help") || argList.contains("-h")) {
            printHelp();
            return;
        }
        if (argList.contains("--list-tools")) {
            listToolsAndExit(argList);
            return;
        }

        DiagConfig config = DiagConfig.fromEnvironment(System.getenv(), argList);
        JsonCodec codec = JsonCodec.createDefault();
        ToolSupport support = ToolSupport.create(config, codec);
        ToolRegistrar registrar = new ToolRegistrar(support);

        if (config.transport() == DiagConfig.Transport.HTTP) {
            startHttp(config, registrar, codec);
        } else {
            startStdio(config, registrar, codec);
        }
    }

    private static void startHttp(DiagConfig config, ToolRegistrar registrar, JsonCodec codec) throws Exception {
        HttpMcpServerBootstrap bootstrap = new HttpMcpServerBootstrap(config, registrar, codec);
        bootstrap.start();
        Runtime.getRuntime().addShutdownHook(new Thread(bootstrap::stop, "java-diag-shutdown"));
        bootstrap.await();
    }

    private static void startStdio(DiagConfig config, ToolRegistrar registrar, JsonCodec codec) {
        McpJsonMapper mapper = codec.mapper();
        StdioServerTransportProvider transport = new StdioServerTransportProvider(mapper);

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("java-diag", config.version())
                .instructions("Read-only Java diagnostics MCP. Tools are prefixed with diag.*")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .strictToolNameValidation(false)
                .jsonMapper(mapper)
                .tools(registrar.specifications())
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully, "java-diag-shutdown"));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            server.closeGracefully();
        }
    }

    private static void listToolsAndExit(List<String> argList) {
        DiagConfig config = DiagConfig.fromEnvironment(System.getenv(), argList);
        JsonCodec codec = JsonCodec.createDefault();
        ToolSupport support = ToolSupport.create(config, codec);
        ToolRegistrar registrar = new ToolRegistrar(support);
        List<String> names = registrar.toolNames();
        System.out.println("java-diag tools (" + names.size() + "):");
        for (String name : names) {
            System.out.println("  " + name);
        }
    }

    private static void printHelp() {
        System.out.println("""
                java-diag MCP server

                Default transport: HTTP (Agent connects via IP:port)
                Local IDE fallback: --stdio

                Usage:
                  java -jar java-diag-mcp-server.jar
                  java -jar java-diag-mcp-server.jar --http --host 0.0.0.0 --port 8765 --token my-secret
                  java -jar java-diag-mcp-server.jar --stdio
                  java -jar java-diag-mcp-server.jar --list-tools
                  java -jar java-diag-mcp-server.jar --help

                Flags:
                  --http | --stdio
                  --host <addr>          bind address (default 0.0.0.0)
                  --port <port>          listen port (default 8765)
                  --token <value>        Bearer auth token (alias: --auth-token); overrides env

                Remote endpoints (HTTP mode):
                  GET  /sse            MCP SSE
                  POST /mcp/message    MCP SSE messages
                  *    /mcp            MCP Streamable HTTP
                  GET  /health         liveness

                Environment:
                  JAVA_DIAG_TRANSPORT                 http (default) | stdio
                  JAVA_DIAG_HTTP_HOST                 bind address (default 0.0.0.0)
                  JAVA_DIAG_HTTP_PORT                 listen port (default 8765)
                  JAVA_DIAG_AUTH_TOKEN                Bearer token (overridden by --token)
                  JAVA_DIAG_ALLOWED_LOG_DIRS          comma-separated whitelist roots
                  JAVA_DIAG_ALLOWED_MAIN_CLASS_REGEX  optional main-class allow regex
                  JAVA_DIAG_TOOL_TIMEOUT_MS           default tool timeout (ms)
                  JAVA_DIAG_MAX_OUTPUT_BYTES          max captured output bytes
                  JAVA_DIAG_DANGEROUS_TOOLS_ENABLED   allow live=true histo (default false)
                  JAVA_DIAG_AUDIT_PATH                audit JSONL path
                  JAVA_DIAG_INSTANCE_ID               instance id in envelopes
                """);
    }
}
