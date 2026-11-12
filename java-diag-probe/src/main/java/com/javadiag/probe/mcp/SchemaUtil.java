package com.javadiag.probe.mcp;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SchemaUtil {

    private SchemaUtil() {
    }

    static McpSchema.JsonSchema emptyObject() {
        return object(Map.of(), List.of());
    }

    static McpSchema.JsonSchema object(Map<String, Object> properties, List<String> required) {
        return new McpSchema.JsonSchema(
                "object",
                properties,
                required == null || required.isEmpty() ? null : required,
                false,
                null,
                null);
    }

    static Map<String, Object> stringProp(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("description", description);
        return m;
    }

    static Map<String, Object> integerProp(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "integer");
        m.put("description", description);
        return m;
    }

    static Map<String, Object> booleanProp(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "boolean");
        m.put("description", description);
        return m;
    }
}
