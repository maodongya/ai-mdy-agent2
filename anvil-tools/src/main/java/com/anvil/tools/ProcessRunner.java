package com.anvil.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Runs shell commands in a workspace directory. */
public final class ProcessRunner {

    public record Result(int exitCode, String output, boolean timedOut) {}

    private ProcessRunner() {}

    public static Result run(Path workspaceRoot, String command, long timeoutMs) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
            pb.directory(workspaceRoot.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Result(-1, "command timed out after " + timeoutMs + "ms", true);
            }
            String output;
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce((a, b) -> a + "\n" + b).orElse("");
            }
            return new Result(process.exitValue(), output, false);
        } catch (Exception e) {
            return new Result(-1, e.getMessage(), false);
        }
    }
}
