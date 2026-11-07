package com.javadiag.mcp.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdkToolchainTest {

    @Test
    void parseMajorVersions() {
        assertEquals(8, JdkToolchain.parseMajor("1.8.0_402"));
        assertEquals(11, JdkToolchain.parseMajor("11.0.22"));
        assertEquals(17, JdkToolchain.parseMajor("17"));
        assertEquals(21, JdkToolchain.parseMajor("21.0.12.1"));
    }
}
