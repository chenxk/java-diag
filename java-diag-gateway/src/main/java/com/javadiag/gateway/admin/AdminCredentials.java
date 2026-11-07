package com.javadiag.gateway.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Constant-time credential check for the admin console (separate from agent/registry tokens). */
public final class AdminCredentials {

    static final String SESSION_ATTR = "java-diag.admin.authenticated";

    private final String expectedUser;
    private final String expectedPassword;

    public AdminCredentials(String expectedUser, String expectedPassword) {
        this.expectedUser = expectedUser == null ? "" : expectedUser;
        this.expectedPassword = expectedPassword == null ? "" : expectedPassword;
    }

    public boolean verify(String user, String password) {
        if (user == null || password == null) {
            return false;
        }
        return constantTimeEquals(user, expectedUser) && constantTimeEquals(password, expectedPassword);
    }

    static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return MessageDigest.isEqual(left, left) && false;
        }
        return MessageDigest.isEqual(left, right);
    }
}
