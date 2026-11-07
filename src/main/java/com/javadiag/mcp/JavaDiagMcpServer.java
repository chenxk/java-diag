package com.javadiag.mcp;

import com.javadiag.mcp.config.DiagConfig;
import com.javadiag.mcp.mcp.ToolRegistrar;
import com.javadiag.mcp.platform.JsonCodec;
import com.javadiag.mcp.tools.ToolSupport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;

/**
 * Entry point: stdio MCP server exposing read-only diag.* tools.
 */
public final class JavaDiagMcpServer {

    public static void main(String[] args) {
        if (args.length > 0 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            printHelp();
            return;
        }
        if (args.length > 0 && "--list-tools".equals(args[0])) {
            listToolsAndExit();
            return;
        }

        DiagConfig config = DiagConfig.fromEnvironment();
        JsonCodec codec = JsonCodec.createDefault();
        ToolSupport support = ToolSupport.create(config, codec);
        ToolRegistrar registrar = new ToolRegistrar(support);

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

        // Stdio transport keeps the JVM alive via its reader thread; park the main thread.
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            server.closeGracefully();
        }
    }

    private static void listToolsAndExit() {
        DiagConfig config = DiagConfig.fromEnvironment();
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
                java-diag MCP server (stdio)

                Usage:
                  java -jar java-diag-mcp-server.jar
                  java -jar java-diag-mcp-server.jar --list-tools
                  java -jar java-diag-mcp-server.jar --help

                Environment:
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
