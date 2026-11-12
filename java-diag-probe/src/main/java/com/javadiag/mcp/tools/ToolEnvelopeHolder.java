package com.javadiag.mcp.tools;

import com.javadiag.mcp.model.ToolEnvelope;

/**
 * Simple holder so tool classes can return envelopes without leaking model imports everywhere.
 */
public final class ToolEnvelopeHolder {

    private final ToolEnvelope envelope;

    public ToolEnvelopeHolder(ToolEnvelope envelope) {
        this.envelope = envelope;
    }

    public ToolEnvelope envelope() {
        return envelope;
    }
}
