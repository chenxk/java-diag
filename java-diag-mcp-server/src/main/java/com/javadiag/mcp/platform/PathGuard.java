package com.javadiag.mcp.platform;

import com.javadiag.mcp.model.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Validates log paths: rejects {@code ..}, requires whitelist membership.
 */
public final class PathGuard {

    private final List<Path> allowedDirs;

    public PathGuard(List<Path> allowedDirs) {
        this.allowedDirs = List.copyOf(allowedDirs);
    }

    public Path resolveAllowed(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new DiagException(ErrorCode.INVALID_ARGUMENT, "path is required");
        }
        if (rawPath.contains("\0")) {
            throw new DiagException(ErrorCode.PATH_NOT_ALLOWED, "path contains NUL");
        }
        Path input = Paths.get(rawPath);
        for (Path part : input) {
            if ("..".equals(part.toString())) {
                throw new DiagException(ErrorCode.PATH_NOT_ALLOWED, "path must not contain '..'");
            }
        }

        Path absolute = input.isAbsolute()
                ? input.normalize()
                : Paths.get(".").toAbsolutePath().resolve(input).normalize();

        Path real;
        try {
            if (Files.exists(absolute)) {
                real = absolute.toRealPath();
            } else {
                Path parent = absolute.getParent();
                if (parent != null && Files.exists(parent)) {
                    real = parent.toRealPath().resolve(absolute.getFileName()).normalize();
                } else {
                    real = absolute;
                }
            }
        } catch (IOException ex) {
            throw new DiagException(ErrorCode.PATH_NOT_ALLOWED, "unable to resolve path: " + rawPath, ex);
        }

        if (!isUnderAllowed(real)) {
            throw new DiagException(
                    ErrorCode.PATH_NOT_ALLOWED,
                    "path not under allowedLogDirs: " + real);
        }
        return real;
    }

    public boolean isUnderAllowed(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        for (Path allowed : allowedDirs) {
            Path base = allowed.toAbsolutePath().normalize();
            if (normalized.startsWith(base)) {
                return true;
            }
        }
        return false;
    }

    public List<Path> allowedDirs() {
        return allowedDirs;
    }
}
