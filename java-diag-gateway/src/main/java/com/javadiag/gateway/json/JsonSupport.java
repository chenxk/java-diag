package com.javadiag.gateway.json;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapperSupplier;
import io.modelcontextprotocol.json.TypeRef;

import java.util.Map;
import java.util.ServiceLoader;

public final class JsonSupport {

    private final McpJsonMapper mapper;

    public JsonSupport(McpJsonMapper mapper) {
        this.mapper = mapper;
    }

    public static JsonSupport createDefault() {
        McpJsonMapperSupplier supplier = ServiceLoader.load(McpJsonMapperSupplier.class).findFirst()
                .orElseThrow(() -> new IllegalStateException("No McpJsonMapperSupplier found"));
        return new JsonSupport(supplier.get());
    }

    public McpJsonMapper mapper() {
        return mapper;
    }

    public String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("json serialize failed", ex);
        }
    }

    public Map<String, Object> toMap(String json) {
        try {
            return mapper.readValue(json, new TypeRef<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid json body", ex);
        }
    }
}
