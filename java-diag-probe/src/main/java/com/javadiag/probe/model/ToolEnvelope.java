package com.javadiag.probe.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified JSON response envelope for all diag.* tools.
 */
public final class ToolEnvelope {

    private final boolean ok;
    private final String tool;
    private final String instanceId;
    private final long tookMs;
    private final boolean truncated;
    private final Object data;
    private final ErrorPayload error;

    private ToolEnvelope(
            boolean ok,
            String tool,
            String instanceId,
            long tookMs,
            boolean truncated,
            Object data,
            ErrorPayload error) {
        this.ok = ok;
        this.tool = tool;
        this.instanceId = instanceId;
        this.tookMs = tookMs;
        this.truncated = truncated;
        this.data = data;
        this.error = error;
    }

    public static ToolEnvelope success(
            String tool, String instanceId, long tookMs, boolean truncated, Object data) {
        return new ToolEnvelope(true, tool, instanceId, tookMs, truncated, data, null);
    }

    public static ToolEnvelope failure(
            String tool, String instanceId, long tookMs, ErrorCode code, String message) {
        return new ToolEnvelope(
                false,
                tool,
                instanceId,
                tookMs,
                false,
                null,
                new ErrorPayload(code.name(), message));
    }

    public boolean ok() {
        return ok;
    }

    public String tool() {
        return tool;
    }

    public String instanceId() {
        return instanceId;
    }

    public long tookMs() {
        return tookMs;
    }

    public boolean truncated() {
        return truncated;
    }

    public Object data() {
        return data;
    }

    public ErrorPayload error() {
        return error;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", ok);
        map.put("tool", tool);
        map.put("instanceId", instanceId);
        map.put("tookMs", tookMs);
        map.put("truncated", truncated);
        map.put("data", data);
        if (error == null) {
            map.put("error", null);
        } else {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("code", error.code());
            err.put("message", error.message());
            map.put("error", err);
        }
        return map;
    }

    public record ErrorPayload(String code, String message) {
    }
}
