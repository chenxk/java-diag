package com.javadiag.mcp.platform;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogTimestampParserTest {

    @Test
    void parseSinceIso() {
        Instant t = LogTimestampParser.parseSince("2026-11-07T12:00:00Z");
        assertNotNull(t);
    }

    @Test
    void parseLineLogback() {
        Instant t = LogTimestampParser.parseLineTimestamp("2026-11-07 12:00:00.123 INFO hello");
        assertNotNull(t);
    }

    @Test
    void nullSinceIsOptional() {
        assertNull(LogTimestampParser.parseSince(null));
    }

    @Test
    void lineWithoutTimestampReturnsNull() {
        assertNull(LogTimestampParser.parseLineTimestamp("no timestamp here"));
    }
}
