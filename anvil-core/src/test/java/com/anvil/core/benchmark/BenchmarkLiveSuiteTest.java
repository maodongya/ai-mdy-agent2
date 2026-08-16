package com.anvil.core.benchmark;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Live LLM benchmarks — skipped unless {@code DEEPSEEK_API_KEY} is set.
 *
 * <pre>
 * export DEEPSEEK_API_KEY=sk-...
 * bash scripts/benchmark-live.sh
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class BenchmarkLiveSuiteTest {

    @TestFactory
    Stream<DynamicTest> runLiveBenchmarks() throws Exception {
        Path repoRoot = repoRoot();
        List<BenchmarkSpec> specs = BenchmarkCatalog.loadLive(repoRoot);
        return specs.stream()
                .map(spec -> dynamicTest(spec.name() + " [" + spec.id() + "]", () -> {
                    BenchmarkRunner.BenchmarkReport report = BenchmarkRunner.runAndScore(repoRoot, spec);
                    String summary = BenchmarkSuiteTest.formatReport(report);
                    assertTrue(report.passed(), summary);
                }));
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(cwd.resolve("fixtures/benchmarks"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
