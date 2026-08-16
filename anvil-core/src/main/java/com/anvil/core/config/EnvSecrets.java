package com.anvil.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Reads secrets from process env, falling back to {@code .env.local} in the project tree. */
public final class EnvSecrets {

    private static volatile Map<String, String> dotEnv;

    private EnvSecrets() {}

    public static String get(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String fromEnv = System.getenv(name);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return dotEnv().getOrDefault(name, "");
    }

    private static Map<String, String> dotEnv() {
        Map<String, String> cached = dotEnv;
        if (cached != null) {
            return cached;
        }
        synchronized (EnvSecrets.class) {
            if (dotEnv != null) {
                return dotEnv;
            }
            dotEnv = loadDotEnvLocal();
            return dotEnv;
        }
    }

    static void resetForTests() {
        synchronized (EnvSecrets.class) {
            dotEnv = null;
        }
    }

    private static Map<String, String> loadDotEnvLocal() {
        Path file = findEnvLocal();
        if (file == null) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = new HashMap<>();
            for (String line : Files.readAllLines(file)) {
                parseLine(line, parsed);
            }
            return Collections.unmodifiableMap(parsed);
        } catch (IOException e) {
            return Map.of();
        }
    }

    private static Path findEnvLocal() {
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int depth = 0; depth < 8 && dir != null; depth++) {
            Path candidate = dir.resolve(".env.local");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static void parseLine(String line, Map<String, String> out) {
        if (line == null) {
            return;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        if (trimmed.startsWith("export ")) {
            trimmed = trimmed.substring("export ".length()).trim();
        }
        int eq = trimmed.indexOf('=');
        if (eq <= 0) {
            return;
        }
        String key = trimmed.substring(0, eq).trim();
        String value = trimmed.substring(eq + 1).trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        if (!key.isEmpty()) {
            out.put(key, value);
        }
    }
}
