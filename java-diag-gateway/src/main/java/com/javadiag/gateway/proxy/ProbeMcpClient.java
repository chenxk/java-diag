package com.javadiag.gateway.proxy;

import com.javadiag.gateway.json.JsonSupport;
import com.javadiag.gateway.registry.ProbeInstance;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Short-lived MCP client session to a single probe for one tool call.
 */
public final class ProbeMcpClient {

    private final JsonSupport json;
    private final Duration requestTimeout;

    public ProbeMcpClient(JsonSupport json, Duration requestTimeout) {
        this.json = json;
        this.requestTimeout = requestTimeout;
    }

    public Map<String, Object> callTool(ProbeInstance probe, String toolName, Map<String, Object> args) {
        String baseUrl = trimTrailingSlash(probe.mcpBaseUrl());
        HttpClientSseClientTransport.Builder transportBuilder = HttpClientSseClientTransport.builder(baseUrl)
                .sseEndpoint(probe.ssePath())
                .jsonMapper(json.mapper())
                .connectTimeout(Duration.ofSeconds(10));

        if (probe.probeToken() != null && !probe.probeToken().isBlank()) {
            String token = probe.probeToken();
            transportBuilder.customizeRequest(b -> b.header("Authorization", "Bearer " + token));
        }

        try (McpSyncClient client = McpClient.sync(transportBuilder.build())
                .requestTimeout(requestTimeout)
                .initializationTimeout(Duration.ofSeconds(15))
                .clientInfo(new McpSchema.Implementation("java-diag-gateway", "0.1.0"))
                .build()) {
            client.initialize();
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(toolName, args == null ? Map.of() : args));
            return toEnvelope(toolName, probe, result);
        } catch (Exception ex) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("ok", false);
            error.put("tool", toolName);
            error.put("instanceId", probe.instanceId());
            error.put("tookMs", 0);
            error.put("truncated", false);
            error.put("data", null);
            error.put("error", Map.of(
                    "code", "INTERNAL",
                    "message", "probe call failed: " + ex.getMessage()));
            return error;
        }
    }

    private Map<String, Object> toEnvelope(String toolName, ProbeInstance probe, McpSchema.CallToolResult result) {
        // Prefer structured content from probe; else parse first text content as JSON envelope.
        if (result.structuredContent() instanceof Map<?, ?> structured) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) structured;
            Map<String, Object> copy = new LinkedHashMap<>(map);
            copy.putIfAbsent("routedInstanceId", probe.instanceId());
            return copy;
        }
        List<McpSchema.Content> contents = result.content();
        if (contents != null) {
            for (McpSchema.Content content : contents) {
                if (content instanceof McpSchema.TextContent text && text.text() != null) {
                    try {
                        Map<String, Object> parsed = json.toMap(text.text());
                        parsed.putIfAbsent("routedInstanceId", probe.instanceId());
                        return parsed;
                    } catch (Exception ignored) {
                        Map<String, Object> wrap = new LinkedHashMap<>();
                        wrap.put("ok", result.isError() == null || !result.isError());
                        wrap.put("tool", toolName);
                        wrap.put("routedInstanceId", probe.instanceId());
                        wrap.put("data", Map.of("text", text.text()));
                        wrap.put("error", null);
                        return wrap;
                    }
                }
            }
        }
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("ok", result.isError() == null || !result.isError());
        empty.put("tool", toolName);
        empty.put("routedInstanceId", probe.instanceId());
        empty.put("data", null);
        empty.put("error", result.isError() != null && result.isError()
                ? Map.of("code", "INTERNAL", "message", "empty probe response")
                : null);
        return empty;
    }

    private static String trimTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
