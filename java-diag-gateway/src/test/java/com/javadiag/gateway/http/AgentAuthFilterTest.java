package com.javadiag.gateway.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAuthFilterTest {

    @Test
    void registryPrefixMustBePathSegment() {
        assertTrue(AgentAuthFilter.isExemptPath("/registry"));
        assertTrue(AgentAuthFilter.isExemptPath("/registry/register"));
        assertFalse(AgentAuthFilter.isExemptPath("/registry-admin"));
        assertFalse(AgentAuthFilter.isExemptPath("/registryevil"));
        assertFalse(AgentAuthFilter.isExemptPath("/sse"));
        assertFalse(AgentAuthFilter.isExemptPath("/mcp"));
        assertTrue(AgentAuthFilter.isExemptPath("/admin"));
        assertTrue(AgentAuthFilter.isExemptPath("/admin/api/login"));
        assertFalse(AgentAuthFilter.isExemptPath("/admin-evil"));
    }

    @Test
    void extraExemptPrefixesAreSegmentAware() {
        var prefixes = java.util.List.of("/api");
        assertTrue(AgentAuthFilter.isExemptPath("/api", prefixes));
        assertTrue(AgentAuthFilter.isExemptPath("/api/v1/health", prefixes));
        assertFalse(AgentAuthFilter.isExemptPath("/apiv1", prefixes));
        assertFalse(AgentAuthFilter.isExemptPath("/sse", prefixes));
    }

    @Test
    void normalizeCollapsesDotDotTraversal() {
        assertTrue(AgentAuthFilter.isExemptPath(
                AgentAuthFilter.normalizePath("/health")));
        assertFalse(AgentAuthFilter.isExemptPath(
                AgentAuthFilter.normalizePath("/registry/../sse")));
        assertFalse(AgentAuthFilter.isExemptPath(
                AgentAuthFilter.normalizePath("/health/../sse")));
        assertTrue(AgentAuthFilter.isExemptPath(
                AgentAuthFilter.normalizePath("/registry/../registry/register")));
    }
}
