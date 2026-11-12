package com.javadiag.probe.model;

/**
 * Stable error codes returned in the unified tool envelope.
 */
public enum ErrorCode {
    INVALID_ARGUMENT,
    PID_NOT_FOUND,
    PATH_NOT_ALLOWED,
    TIMEOUT,
    OUTPUT_TRUNCATED,
    INSTANCE_NOT_FOUND,
    REGISTRY_UNAVAILABLE,
    RATE_LIMITED,
    UNAUTHORIZED,
    COMMAND_NOT_ALLOWED,
    TOOL_DISABLED,
    INTERNAL
}
