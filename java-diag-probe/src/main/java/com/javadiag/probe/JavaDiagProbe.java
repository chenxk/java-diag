package com.javadiag.probe;

import com.javadiag.probe.cluster.RegistryHeartbeatClient;
import com.javadiag.probe.config.DiagConfig;
import com.javadiag.probe.http.HttpMcpServerBootstrap;
import com.javadiag.probe.mcp.ToolRegistrar;
import com.javadiag.probe.platform.JsonCodec;
import com.javadiag.probe.process.JavaProcessService;
import com.javadiag.probe.tools.ToolSupport;
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
public final class JavaDiagProbe {

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

                Configuration load order (low to high):
                  built-in defaults; file layer (no --config: classpath /java-diag-probe.properties,
                  then ./java-diag-probe.properties, then java-diag-probe.properties.local
                  from cwd up to 6 parents, nearer wins); --config replaces the file layer;
                  environment variables; CLI flags below (highest).

                Example file (all keys): classpath java-diag-probe.properties.example
                Full reference: docs/CONFIGURATION.md

                Usage:
                  java -jar java-diag-probe.jar --config ./java-diag-probe.properties
                  java -jar java-diag-probe.jar --config ./probe.properties --port 8765 --auth-token probe-secret
                  java -jar java-diag-probe.jar --stdio

                CLI (common overrides):
                  --config <file>              explicit properties file (required to exist)
                  --http | --stdio
                  --host <addr>                default 0.0.0.0
                  --port <port>                default 8765
                  --auth-token <v>             Agent/MCP Bearer (required)
                  --service-name <name>        optional; default primary IP
                  --register-url <gateway>     enables heartbeat
                  --register-token <value>     registry Bearer (required when --register-url set)
                  --advertise-url <url>        public MCP base URL (instanceId = host:port)

                Other CLI (less common; prefer properties file):
                  --tag key=value              repeatable
                  --heartbeat-interval-seconds <n>
                  --app-map <pattern>=<name>   repeatable

                Other: --help / -h   --list-tools
                """);
    }
}
