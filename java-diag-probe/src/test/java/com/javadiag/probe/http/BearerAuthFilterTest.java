package com.javadiag.probe.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearerAuthFilterTest {

    @Test
    void onlyHealthAndRootAreExempt() {
        assertTrue(BearerAuthFilter.isExemptPath("/health"));
        assertTrue(BearerAuthFilter.isExemptPath("/"));
        assertFalse(BearerAuthFilter.isExemptPath("/sse"));
        assertFalse(BearerAuthFilter.isExemptPath("/mcp"));
        assertFalse(BearerAuthFilter.isExemptPath("/healthz"));
    }

    @Test
    void normalizeBlocksHealthDotDotBypass() {
        assertEquals("/sse", BearerAuthFilter.normalizePath("/health/../sse"));
        assertFalse(BearerAuthFilter.isExemptPath(
                BearerAuthFilter.normalizePath("/health/../sse")));
    }
}
