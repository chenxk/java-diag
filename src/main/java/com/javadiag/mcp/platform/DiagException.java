package com.javadiag.mcp.platform;

import com.javadiag.mcp.model.ErrorCode;

/**
 * Domain exception carrying a stable {@link ErrorCode}.
 */
public final class DiagException extends RuntimeException {

    private final ErrorCode code;

    public DiagException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public DiagException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
