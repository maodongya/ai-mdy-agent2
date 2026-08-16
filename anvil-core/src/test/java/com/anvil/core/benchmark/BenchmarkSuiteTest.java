package com.anvil.core.benchmark;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/** Automated benchmark scoring over scripted agent runs. */
class BenchmarkSuiteTest {

    @TestFactory
    Stream<DynamicTest> runAllBenchmarks() throws Exception {
        Path repoRoot = repoRoot();
        List<BenchmarkSpec> specs = BenchmarkCatalog.loadAll(repoRoot);
        return specs.stream()
                .map(spec -> dynamicTest(spec.name() + " [" + spec.id() + "]", () -> {
                    BenchmarkRunner.BenchmarkReport report = BenchmarkRunner.runAndScore(repoRoot, spec);
                    String summary = formatReport(report);
                    assertTrue(report.passed(), summary);
                }));
    }

    static String formatReport(BenchmarkRunner.BenchmarkReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(report.id())
                .append(" score ")
                .append(report.score())
                .append('/')
                .append(report.maxScore())
                .append(" status=")
                .append(report.status().wireValue())
                .append('\n');
        for (BenchmarkRunner.CheckResult check : report.checks()) {
            sb.append(check.passed() ? "  ✓ " : "  ✗ ")
                    .append(check.name())
                    .append(": ")
                    .append(check.detail())
                    .append('\n');
        }
        return sb.toString();
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(cwd.resolve("fixtures/benchmarks"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
