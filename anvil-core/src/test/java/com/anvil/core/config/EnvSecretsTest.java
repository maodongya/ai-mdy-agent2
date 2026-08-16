package com.anvil.core.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvSecretsTest {

    @AfterEach
    void reset() {
        EnvSecrets.resetForTests();
    }

    @Test
    void loadsFromDotEnvLocalInWorkingDirectory() throws Exception {
        Path dir = Files.createTempDirectory("anvil-env-secrets");
        Path previousDir = Path.of(System.getProperty("user.dir"));
        Path envFile = dir.resolve(".env.local");
        Files.writeString(envFile, "ANVIL_TEST_SECRET=from-file\n");

        try {
            System.setProperty("user.dir", dir.toString());
            EnvSecrets.resetForTests();
            assertEquals("from-file", EnvSecrets.get("ANVIL_TEST_SECRET"));
        } finally {
            System.setProperty("user.dir", previousDir.toString());
            Files.deleteIfExists(envFile);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void ignoresCommentsAndBlankLines() throws Exception {
        Path dir = Files.createTempDirectory("anvil-env-secrets");
        Path previousDir = Path.of(System.getProperty("user.dir"));
        Path envFile = dir.resolve(".env.local");
        Files.writeString(
                envFile,
                """
                # comment
                ANVIL_TEST_SECRET=quoted
                """);

        try {
            System.setProperty("user.dir", dir.toString());
            EnvSecrets.resetForTests();
            assertEquals("quoted", EnvSecrets.get("ANVIL_TEST_SECRET"));
            assertTrue(EnvSecrets.get("MISSING_KEY").isBlank());
        } finally {
            System.setProperty("user.dir", previousDir.toString());
            Files.deleteIfExists(envFile);
            Files.deleteIfExists(dir);
        }
    }
}
