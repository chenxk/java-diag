package com.javadiag.probe.platform;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapperSupplier;

import java.util.ServiceLoader;

/**
 * Thin wrapper over the MCP SDK JSON mapper (Jackson 3 via ServiceLoader).
 */
public final class JsonCodec {

    private final McpJsonMapper mapper;

    public JsonCodec(McpJsonMapper mapper) {
        this.mapper = mapper;
    }

    public static JsonCodec createDefault() {
        ServiceLoader<McpJsonMapperSupplier> loader = ServiceLoader.load(McpJsonMapperSupplier.class);
        McpJsonMapperSupplier supplier = loader.findFirst()
                .orElseThrow(() -> new IllegalStateException("No McpJsonMapperSupplier on classpath"));
        return new JsonCodec(supplier.get());
    }

    public McpJsonMapper mapper() {
        return mapper;
    }

    public String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON serialization failed", ex);
        }
    }
}
