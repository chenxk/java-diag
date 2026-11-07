package com.javadiag.gateway.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCredentialsTest {

    @Test
    void verifiesExpectedPair() {
        AdminCredentials creds = new AdminCredentials("admin", "java-diag");
        assertTrue(creds.verify("admin", "java-diag"));
        assertFalse(creds.verify("admin", "wrong"));
        assertFalse(creds.verify("root", "java-diag"));
    }
}
