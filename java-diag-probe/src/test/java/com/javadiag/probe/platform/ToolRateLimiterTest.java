package com.javadiag.probe.platform;

import com.javadiag.probe.model.ErrorCode;
import com.javadiag.probe.tools.JvmTools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolRateLimiterTest {

    @Test
    void coolsDownExpensiveTools() {
        ToolRateLimiter limiter = new ToolRateLimiter(60_000);
        assertDoesNotThrow(() -> limiter.check(JvmTools.THREAD_DUMP));
        DiagException ex = assertThrows(DiagException.class, () -> limiter.check(JvmTools.THREAD_DUMP));
        assertEquals(ErrorCode.RATE_LIMITED, ex.code());
    }

    @Test
    void ignoresNonDumpTools() {
        ToolRateLimiter limiter = new ToolRateLimiter(60_000);
        assertDoesNotThrow(() -> limiter.check("diag.ping"));
        assertDoesNotThrow(() -> limiter.check("diag.ping"));
    }

    @Test
    void zeroCooldownDisables() {
        ToolRateLimiter limiter = new ToolRateLimiter(0);
        assertDoesNotThrow(() -> limiter.check(JvmTools.HEAP_DUMP));
        assertDoesNotThrow(() -> limiter.check(JvmTools.HEAP_DUMP));
    }
}
