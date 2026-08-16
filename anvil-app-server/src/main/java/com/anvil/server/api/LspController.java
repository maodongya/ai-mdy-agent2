package com.anvil.server.api;

import com.anvil.server.service.RunService;
import com.anvil.server.store.ThreadRecord;
import com.anvil.tools.lsp.LspService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** LSP navigation endpoints (Phase 9.2). */
@RestController
@RequestMapping("/v1/lsp")
public class LspController {

    private final RunService runService;

    public LspController(RunService runService) {
        this.runService = runService;
    }

    @GetMapping("/definition")
    public ResponseEntity<?> definition(
            @RequestParam String thread_id,
            @RequestParam String path,
            @RequestParam(defaultValue = "1") int line,
            @RequestParam(defaultValue = "1") int column)
            throws Exception {
        ThreadRecord thread = threadOr404(thread_id);
        if (thread == null) {
            return ResponseEntity.notFound().build();
        }
        return LspService.definition(thread.workspaceRoot(), path, line, column)
                .map(loc -> ResponseEntity.ok(toMap(loc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/references")
    public ResponseEntity<?> references(
            @RequestParam String thread_id,
            @RequestParam String path,
            @RequestParam(defaultValue = "1") int line,
            @RequestParam(defaultValue = "1") int column)
            throws Exception {
        ThreadRecord thread = threadOr404(thread_id);
        if (thread == null) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> refs = LspService.references(thread.workspaceRoot(), path, line, column).stream()
                .map(LspController::toMap)
                .toList();
        return ResponseEntity.ok(Map.of("references", refs));
    }

    @PostMapping("/diagnostics")
    public ResponseEntity<?> diagnostics(@RequestParam String thread_id, @RequestBody(required = false) DiagnosticsBody body)
            throws Exception {
        ThreadRecord thread = threadOr404(thread_id);
        if (thread == null) {
            return ResponseEntity.notFound().build();
        }
        String focus = body == null ? null : body.path();
        List<Map<String, Object>> items = LspService.compileDiagnostics(thread.workspaceRoot(), focus).stream()
                .map(LspController::toMap)
                .toList();
        return ResponseEntity.ok(Map.of("diagnostics", items, "count", items.size()));
    }

    private ThreadRecord threadOr404(String threadId) {
        return runService.getThread(threadId).orElse(null);
    }

    private static Map<String, Object> toMap(com.anvil.tools.lsp.LspLocation loc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("path", loc.path());
        map.put("line", loc.line());
        map.put("column", loc.column());
        map.put("source", loc.source());
        return map;
    }

    private static Map<String, Object> toMap(LspService.DiagnosticItem d) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("path", d.path());
        map.put("line", d.line());
        map.put("column", d.column());
        map.put("severity", d.severity());
        map.put("message", d.message());
        return map;
    }

    public record DiagnosticsBody(String path) {}
}
