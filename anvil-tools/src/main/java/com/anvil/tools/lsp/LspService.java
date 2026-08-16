package com.anvil.tools.lsp;

import com.anvil.tools.DiagnosticParser;
import com.anvil.tools.DiagnosticsTool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Facade for LSP navigation + compile diagnostics (Phase 9.2/9.3). */
public final class LspService {

    private LspService() {}

    public static java.util.Optional<LspLocation> definition(Path workspace, String path, int line, int column) {
        return JdtlsBridge.forWorkspace(workspace).definition(path, line, column);
    }

    public static List<LspLocation> references(Path workspace, String path, int line, int column) {
        return JdtlsBridge.forWorkspace(workspace).references(path, line, column);
    }

    public static List<DiagnosticItem> compileDiagnostics(Path workspace, String focusPath) {
        try {
            var result = DiagnosticsTool.collect(workspace, "editor-save", "compile", 90_000);
            List<DiagnosticItem> items = new ArrayList<>();
            for (DiagnosticParser.Diagnostic d : DiagnosticParser.parse(result.content())) {
                items.add(toItem(workspace, d));
            }
            if (focusPath != null && !focusPath.isBlank()) {
                return items.stream()
                        .filter(i -> i.path().endsWith(focusPath) || focusPath.endsWith(i.path()))
                        .toList();
            }
            return items;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static DiagnosticItem toItem(Path workspace, DiagnosticParser.Diagnostic d) {
        String path = d.file();
        if (path.startsWith("/")) {
            try {
                path = workspace.toAbsolutePath().normalize().relativize(Path.of(path).normalize()).toString();
            } catch (Exception ignored) {
                // keep absolute
            }
        }
        path = path.replace('\\', '/');
        return new DiagnosticItem(path, d.line(), d.column(), d.severity(), d.message());
    }

    public record DiagnosticItem(String path, int line, int column, String severity, String message) {}
}
