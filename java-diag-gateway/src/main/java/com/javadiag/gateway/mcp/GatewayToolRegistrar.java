package com.javadiag.gateway.mcp;

import com.javadiag.gateway.config.GatewayConfig;
import com.javadiag.gateway.json.JsonSupport;
import com.javadiag.gateway.platform.GatewayAuditLogger;
import com.javadiag.gateway.platform.GatewayRateLimiter;
import com.javadiag.gateway.proxy.ProbeMcpClient;
import com.javadiag.gateway.registry.InMemoryRegistry;
import com.javadiag.gateway.registry.ProbeInstance;
import com.javadiag.gateway.route.TargetSelector;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public final class GatewayToolRegistrar {

    private static final List<String> PROBE_TOOLS = List.of(
            "diag.ping",
            "diag.list_processes",
            "diag.resolve_java_pid",
            "diag.log_paths",
            "diag.tail_log",
            "diag.grep_log",
            "diag.cluster_errors",
            "diag.runtime_summary",
            "diag.jstat",
            "diag.thread_dump",
            "diag.vm_flags",
            "diag.gc_histo",
            "diag.heap_dump",
            "diag.cpu_sample",
            "diag.vm_info");

    private final GatewayConfig config;
    private final InMemoryRegistry registry;
    private final TargetSelector selector;
    private final ProbeMcpClient probeClient;
    private final JsonSupport json;
    private final GatewayAuditLogger auditLogger;
    private final GatewayRateLimiter rateLimiter;

    public GatewayToolRegistrar(GatewayConfig config, InMemoryRegistry registry, JsonSupport json) {
        this.config = config;
        this.registry = registry;
        this.selector = new TargetSelector(registry);
        this.probeClient = new ProbeMcpClient(json, Duration.ofSeconds(60));
        this.json = json;
        this.auditLogger = new GatewayAuditLogger(config.auditPath(), json);
        this.rateLimiter = new GatewayRateLimiter(config.dumpCooldownMs());
    }

    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        List<McpServerFeatures.SyncToolSpecification> specs = new ArrayList<>();
        specs.add(tool(
                "diag.list_instances",
                "List healthy registered probe instances (filter by serviceName/tags)",
                listInstancesSchema(),
                this::listInstances));

        Map<String, Object> targetProp = new LinkedHashMap<>();
        targetProp.put("type", "object");
        targetProp.put("description",
                "Routing target: instanceId (advertise host:port), mcpBaseUrl, or serviceName+tags");
        Map<String, Object> targetFields = new LinkedHashMap<>();
        targetFields.put("instanceId", Map.of(
                "type", "string",
                "description", "Probe identity = advertise host:port from mcpBaseUrl"));
        targetFields.put("mcpBaseUrl", Map.of(
                "type", "string",
                "description", "Registered probe MCP base URL (alternative to instanceId)"));
        targetFields.put("serviceName", Map.of("type", "string"));
        targetFields.put("tags", Map.of("type", "object"));
        targetProp.put("properties", targetFields);

        for (String name : PROBE_TOOLS) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("target", targetProp);
            // permissive additional probe args
            specs.add(tool(
                    name,
                    "Proxy " + name + " to a selected probe. Multi-match returns candidates.",
                    objectSchema(props, List.of()),
                    (exchange, request) -> proxyTool(name, request.arguments())));
        }
        return specs;
    }

    public void registerAll(McpSyncServer server) {
        for (McpServerFeatures.SyncToolSpecification spec : specifications()) {
            server.addTool(spec);
        }
    }

    public List<String> toolNames() {
        return specifications().stream().map(s -> s.tool().name()).toList();
    }

    private McpSchema.CallToolResult listInstances(
            io.modelcontextprotocol.server.McpSyncServerExchange exchange,
            McpSchema.CallToolRequest request) {
        Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
        String serviceName = args.get("serviceName") == null ? null : args.get("serviceName").toString();
        Map<String, String> tags = castTags(args.get("tags"));
        boolean healthyOnly = args.get("healthyOnly") instanceof Boolean b ? b : true;
        List<ProbeInstance> list = registry.list(serviceName, tags, healthyOnly);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", list.size());
        data.put("instances", list.stream().map(ProbeInstance::toPublicMap).collect(Collectors.toList()));
        return envelopeResult("diag.list_instances", true, data, null, false);
    }

    private McpSchema.CallToolResult proxyTool(String toolName, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        TargetSelector.Result resolved = selector.resolve(safeArgs);
        return switch (resolved.outcome()) {
            case NONE -> {
                auditProxy(toolName, "", "", safeArgs, start, false, "INSTANCE_NOT_FOUND");
                yield envelopeResult(
                        toolName,
                        false,
                        null,
                        Map.of("code", "INSTANCE_NOT_FOUND", "message", "no healthy probe matched target"),
                        false);
            }
            case MANY -> {
                auditProxy(toolName, "", "", safeArgs, start, true, null);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("message", "multiple probes matched; choose instanceId (host:port) or mcpBaseUrl and retry");
                data.put("candidates", resolved.candidates().stream()
                        .map(ProbeInstance::toPublicMap)
                        .collect(Collectors.toList()));
                yield envelopeResult(toolName, true, data, null, false);
            }
            case SINGLE -> {
                ProbeInstance probe = resolved.candidates().get(0);
                if (!rateLimiter.tryAcquire(probe.instanceId(), toolName)) {
                    long retryMs = rateLimiter.retryAfterMs(probe.instanceId(), toolName);
                    auditProxy(toolName, probe.instanceId(), probe.mcpBaseUrl(), safeArgs, start, false, "RATE_LIMITED");
                    yield envelopeResult(
                            toolName,
                            false,
                            null,
                            Map.of(
                                    "code",
                                    "RATE_LIMITED",
                                    "message",
                                    "proxy cooldown for " + toolName + " on " + probe.instanceId()
                                            + "; retry after ~" + retryMs + "ms"),
                            false);
                }
                Map<String, Object> forwardArgs = TargetSelector.stripTarget(safeArgs);
                String errorCode = null;
                boolean success;
                Map<String, Object> probeEnvelope;
                try {
                    probeEnvelope = probeClient.callTool(probe, toolName, forwardArgs);
                    success = !Boolean.FALSE.equals(probeEnvelope.get("ok"));
                    if (!success && probeEnvelope.get("error") instanceof Map<?, ?> err) {
                        Object code = err.get("code");
                        if (code != null) {
                            errorCode = code.toString();
                        }
                    }
                } catch (Exception ex) {
                    success = false;
                    errorCode = "INTERNAL";
                    probeEnvelope = envelopeAsMap(
                            toolName,
                            false,
                            null,
                            Map.of("code", "INTERNAL", "message", ex.getMessage()),
                            false);
                }
                auditProxy(toolName, probe.instanceId(), probe.mcpBaseUrl(), forwardArgs, start, success, errorCode);
                yield McpSchema.CallToolResult.builder()
                        .addTextContent(json.toJson(probeEnvelope))
                        .structuredContent(probeEnvelope)
                        .isError(!success)
                        .build();
            }
        };
    }

    private void auditProxy(
            String tool,
            String targetInstance,
            String mcpBaseUrl,
            Map<String, Object> args,
            long startEpochMs,
            boolean success,
            String errorCode) {
        auditLogger.record(
                tool,
                targetInstance,
                mcpBaseUrl,
                args,
                System.currentTimeMillis() - startEpochMs,
                success,
                errorCode);
    }

    private Map<String, Object> envelopeAsMap(
            String tool,
            boolean ok,
            Object data,
            Map<String, String> error,
            boolean truncated) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("ok", ok);
        envelope.put("tool", tool);
        envelope.put("instanceId", config.instanceId());
        envelope.put("tookMs", 0);
        envelope.put("truncated", truncated);
        envelope.put("data", data);
        envelope.put("error", error);
        return envelope;
    }

    private McpSchema.CallToolResult envelopeResult(
            String tool,
            boolean ok,
            Object data,
            Map<String, String> error,
            boolean truncated) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("ok", ok);
        envelope.put("tool", tool);
        envelope.put("instanceId", config.instanceId());
        envelope.put("tookMs", 0);
        envelope.put("truncated", truncated);
        envelope.put("data", data);
        envelope.put("error", error);
        return McpSchema.CallToolResult.builder()
                .addTextContent(json.toJson(envelope))
                .structuredContent(envelope)
                .isError(!ok)
                .build();
    }

    private McpServerFeatures.SyncToolSpecification tool(
            String name,
            String description,
            McpSchema.JsonSchema schema,
            BiFunction<io.modelcontextprotocol.server.McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder().name(name).description(description).inputSchema(schema).build())
                .callHandler(handler)
                .build();
    }

    private static McpSchema.JsonSchema listInstancesSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("serviceName", Map.of("type", "string", "description", "Optional service name filter"));
        props.put("tags", Map.of("type", "object", "description", "Optional tag equals filter"));
        props.put("healthyOnly", Map.of("type", "boolean", "description", "Default true"));
        return objectSchema(props, List.of());
    }

    private static McpSchema.JsonSchema objectSchema(Map<String, Object> properties, List<String> required) {
        return new McpSchema.JsonSchema(
                "object",
                properties,
                required == null || required.isEmpty() ? null : required,
                true,
                null,
                null);
    }

    private static Map<String, String> castTags(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(e.getKey().toString(), e.getValue().toString());
            }
        }
        return out;
    }
}
