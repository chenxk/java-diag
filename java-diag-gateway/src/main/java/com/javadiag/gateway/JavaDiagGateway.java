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

        GatewayConfig config = GatewayConfig.load(argList);
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

                Configuration load order (low to high):
                  built-in defaults; file layer (no --config: classpath /java-diag-gateway.properties,
                  then ./java-diag-gateway.properties, then java-diag-gateway.properties.local
                  from cwd up to 6 parents, nearer wins); --config replaces the file layer;
                  environment variables; CLI flags (highest).

                Example file: java-diag-gateway.properties.example — see docs/CONFIGURATION.md

                Usage:
                  java -jar java-diag-gateway.jar --config ./java-diag-gateway.properties
                  java -jar java-diag-gateway.jar --config ./gw.properties --token agent-secret

                CLI (common overrides):
                  --config <file>
                  --host <addr>                     default 0.0.0.0
                  --port <port>                     default 8787
                  --token / --auth-token <value>    Agent Bearer token (required)
                  --register-token <value>          registry API Bearer (required)
                  --admin-user / --admin-password   admin console (prefer properties file)

                Less common (properties or env):
                  --heartbeat-ttl-seconds <n>       default 90, must be >= 5

                auth.token and register.token are mandatory (non-empty). Keep /registry/* off the public internet.

                Endpoints:
                  /sse  /mcp          Agent MCP
                  /registry/*         probe register/heartbeat/deregister/instances
                  /admin/             registry console (session login; API under /admin/api/*)
                  /health             liveness
                """);
    }
}
