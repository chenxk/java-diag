package com.javadiag.gateway;

import com.javadiag.gateway.config.GatewayConfig;
import com.javadiag.gateway.http.GatewayHttpBootstrap;
import com.javadiag.gateway.json.JsonSupport;
import com.javadiag.gateway.mcp.GatewayToolRegistrar;
import com.javadiag.gateway.registry.InMemoryRegistry;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Cluster MCP gateway entrypoint.
 */
public final class JavaDiagGateway {

    public static void main(String[] args) throws Exception {
        List<String> argList = Arrays.asList(args);
        if (argList.contains("--help") || argList.contains("-h")) {
            printHelp();
            return;
        }

        GatewayConfig config = GatewayConfig.from(argList);
        JsonSupport json = JsonSupport.createDefault();
        InMemoryRegistry registry = new InMemoryRegistry(Duration.ofSeconds(config.heartbeatTtlSeconds()));
        GatewayToolRegistrar tools = new GatewayToolRegistrar(config, registry, json);
        GatewayHttpBootstrap bootstrap = new GatewayHttpBootstrap(config, registry, json, tools);
        bootstrap.start();
        Runtime.getRuntime().addShutdownHook(new Thread(bootstrap::stop, "java-diag-gateway-shutdown"));
        bootstrap.await();
    }

    private static void printHelp() {
        System.out.println("""
                java-diag-gateway — cluster MCP entry + built-in probe registry

                Usage:
                  java -jar java-diag-gateway.jar --token agent-secret
                  java -jar java-diag-gateway.jar --host 0.0.0.0 --port 8787 --token agent-secret

                Flags:
                  --host <addr>                     default 0.0.0.0
                  --port <port>                     default 8787
                  --token / --auth-token <value>    Agent Bearer token (optional but recommended)
                  --heartbeat-ttl-seconds <n>       default 90

                Registry (/registry/*) has no shared-secret auth (trust intranet).
                Keep the gateway registry port off the public internet.

                Endpoints:
                  /sse  /mcp          Agent MCP
                  /registry/*         probe register/heartbeat/deregister/instances
                  /health             liveness
                """);
    }
}
