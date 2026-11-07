package com.javadiag.mcp;

import com.javadiag.mcp.cluster.RegistryHeartbeatClient;
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
 * Entry point: MCP probe over HTTP (cloud IP:port) or stdio (local IDE).
 * Optional gateway registry heartbeat when --register-url is set.
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
        RegistryHeartbeatClient heartbeat = new RegistryHeartbeatClient(config, codec);
        heartbeat.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            heartbeat.stop();
            bootstrap.stop();
        }, "java-diag-shutdown"));
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
                java-diag MCP probe

                Default transport: HTTP (Agent or Gateway connects via IP:port)
                Local IDE fallback: --stdio
                Config: env first, then CLI overrides. Full list: docs/CONFIGURATION.md

                Usage:
                  java -jar java-diag-mcp-server.jar --port 8765 --token probe-secret
                  java -jar java-diag-mcp-server.jar --port 8765 --token probe-secret \\
                       --service-name order-service --instance-id order-1 --tag env=prod \\
                       --register-url http://gateway:8787 \\
                       --advertise-url http://10.0.1.11:8765
                  java -jar java-diag-mcp-server.jar --stdio

                Transport / HTTP:
                  --http | --stdio
                  --host <addr>                default 0.0.0.0 (JAVA_DIAG_HTTP_HOST)
                  --port <port>                default 8765 (JAVA_DIAG_HTTP_PORT)
                  --token / --auth-token <v>   Bearer token (JAVA_DIAG_AUTH_TOKEN)

                Cluster:
                  --service-name <name>        JAVA_DIAG_SERVICE_NAME (optional; default primary IP)
                  --instance-id <id>           JAVA_DIAG_INSTANCE_ID (optional; default primary IP)
                  --tag key=value              repeatable; or JAVA_DIAG_TAGS=k=v,k2=v2
                  --register-url <gateway>     enables heartbeat (no register-token; trust intranet)
                  --advertise-url <url>        public base URL registered to gateway
                  --heartbeat-interval-seconds <n>   default 20 (min effective 5)

                Env-only (no CLI):
                  JAVA_DIAG_TRANSPORT=http|stdio
                  JAVA_DIAG_ALLOWED_LOG_DIRS=/a,/b
                  JAVA_DIAG_ALLOWED_MAIN_CLASS_REGEX=<regex>
                  JAVA_DIAG_TOOL_TIMEOUT_MS=15000
                  JAVA_DIAG_MAX_OUTPUT_BYTES=262144
                  JAVA_DIAG_DANGEROUS_TOOLS_ENABLED=false
                  JAVA_DIAG_AUDIT_PATH=./logs/java-diag-audit.log

                Other: --help / -h   --list-tools
                """);
    }
}
