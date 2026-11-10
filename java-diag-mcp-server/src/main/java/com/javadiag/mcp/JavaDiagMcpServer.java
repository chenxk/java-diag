package com.javadiag.mcp;

import com.javadiag.mcp.cluster.RegistryHeartbeatClient;
import com.javadiag.mcp.config.DiagConfig;
import com.javadiag.mcp.http.HttpMcpServerBootstrap;
import com.javadiag.mcp.mcp.ToolRegistrar;
import com.javadiag.mcp.platform.JsonCodec;
import com.javadiag.mcp.process.JavaProcessService;
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

        DiagConfig config = DiagConfig.load(System.getenv(), argList);
        JsonCodec codec = JsonCodec.createDefault();
        ToolSupport support = ToolSupport.create(config, codec);
        ToolRegistrar registrar = new ToolRegistrar(support);

        if (config.transport() == DiagConfig.Transport.HTTP) {
            startHttp(config, registrar, codec, support);
        } else {
            startStdio(config, registrar, codec);
        }
    }

    private static void startHttp(
            DiagConfig config, ToolRegistrar registrar, JsonCodec codec, ToolSupport support) throws Exception {
        HttpMcpServerBootstrap bootstrap = new HttpMcpServerBootstrap(config, registrar, codec);
        bootstrap.start();
        JavaProcessService processService = support.processService();
        RegistryHeartbeatClient heartbeat = new RegistryHeartbeatClient(config, codec, processService);
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
        DiagConfig config = DiagConfig.load(System.getenv(), argList);
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

                Configuration load order:
                  1) built-in defaults
                  2) properties file (--config <file>, else ./java-diag-mcp-server.properties if present)
                  3) environment variables (optional override)
                  4) CLI flags below (highest priority)

                Example file (all keys): classpath java-diag-mcp-server.properties.example
                Full reference: docs/CONFIGURATION.md

                Usage:
                  java -jar java-diag-mcp-server.jar --config ./java-diag-mcp-server.properties
                  java -jar java-diag-mcp-server.jar --config ./probe.properties --port 8765 --token probe-secret
                  java -jar java-diag-mcp-server.jar --stdio

                CLI (common overrides):
                  --config <file>              explicit properties file (required to exist)
                  --http | --stdio
                  --host <addr>                default 0.0.0.0
                  --port <port>                default 8765
                  --token / --auth-token <v>   Agent/MCP Bearer (required)
                  --service-name <name>        optional; default primary IP
                  --register-url <gateway>     enables heartbeat
                  --register-token <value>     registry Bearer (required; match gateway)
                  --advertise-url <url>        public MCP base URL (instanceId = host:port)

                Other CLI (less common; prefer properties file):
                  --tag key=value              repeatable
                  --heartbeat-interval-seconds <n>
                  --app-map <pattern>=<name>   repeatable

                Other: --help / -h   --list-tools
                """);
    }
}
