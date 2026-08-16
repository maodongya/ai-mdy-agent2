package com.anvil.core.tools;

import com.anvil.protocol.Mode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Built-in tool JSON schemas for PromptBuilder / OpenAI tools array. */
public final class ToolCatalog {

    private ToolCatalog() {}

    public static List<Map<String, Object>> builtinSchemas(Mode mode) {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(schema(
                "grep",
                "Search file contents in workspace (regex). Prefer over shell grep.",
                Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of(
                                "pattern",
                                Map.of("type", "string", "description", "Java regex pattern"),
                                "path_glob",
                                Map.of("type", "string", "description", "Optional glob filter e.g. **/*.java"),
                                "case_insensitive",
                                Map.of("type", "boolean"),
                                "max_matches",
                                Map.of("type", "integer")),
                        "required",
                        List.of("pattern"))));
        tools.add(schema(
                "codebase.search",
                "Find files relevant to a query (indexed paths, symbols, content snippets with line ranges).",
                Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of(
                                "query",
                                Map.of("type", "string"),
                                "top_k",
                                Map.of("type", "integer", "description", "Max files to return (default 20)")),
                        "required",
                        List.of("query"))));
        tools.add(schema(
                "symbols.search",
                "Find Java type/method definitions by name (shallow index).",
                Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of(
                                "query",
                                Map.of("type", "string", "description", "Symbol name or substring"),
                                "top_k",
                                Map.of("type", "integer", "description", "Max symbols (default 30)")),
                        "required",
                        List.of("query"))));
        tools.add(schema("fs.read", "Read a file in the workspace (optional 1-based line offset/limit)", Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string"),
                        "offset", Map.of("type", "integer", "description", "1-based start line"),
                        "limit", Map.of("type", "integer", "description", "max lines to read")),
                "required", List.of("path"))));
        if (mode != Mode.ASK) {
            tools.add(schema(
                    "search_replace",
                    "Replace old_string with new_string in a file. Fuzzy match on whitespace/indent differences.",
                    Map.of(
                            "type",
                            "object",
                            "properties",
                            Map.of(
                                    "path",
                                    Map.of("type", "string"),
                                    "old_string",
                                    Map.of("type", "string"),
                                    "new_string",
                                    Map.of("type", "string"),
                                    "replace_all",
                                    Map.of("type", "boolean")),
                            "required",
                            List.of("path", "old_string", "new_string"))));
            tools.add(schema(
                    "apply_patch",
                    "Apply unified diff patch to one or more files (multi-file patch with ---/+++ headers supported).",
                    Map.of(
                            "type",
                            "object",
                            "properties",
                            Map.of(
                                    "path",
                                    Map.of(
                                            "type",
                                            "string",
                                            "description",
                                            "Target file (optional when patch includes ---/+++ headers)"),
                                    "patch",
                                    Map.of("type", "string", "description", "Unified diff hunks")),
                            "required",
                            List.of("patch"))));
            tools.add(schema(
                    "edit.plan",
                    "Batch edit plan: JSON array of {path, old_string, new_string} or {path, patch}. Requires approval; applies atomically.",
                    Map.of(
                            "type",
                            "object",
                            "properties",
                            Map.of(
                                    "operations",
                                    Map.of(
                                            "type",
                                            "string",
                                            "description",
                                            "JSON array of edit operations")),
                            "required",
                            List.of("operations"))));
            tools.add(schema(
                    "fs.write",
                    "Write entire file (new/small files only, max "
                            + com.anvil.tools.EditTools.maxWriteLinesHint()
                            + " lines; use search_replace for large edits)",
                    Map.of(
                            "type",
                            "object",
                            "properties",
                            Map.of(
                                    "path", Map.of("type", "string"),
                                    "content", Map.of("type", "string")),
                            "required",
                            List.of("path", "content"))));
            tools.add(schema("fs.glob", "Glob files in workspace", Map.of(
                    "type", "object",
                    "properties", Map.of("pattern", Map.of("type", "string")),
                    "required", List.of("pattern"))));
            tools.add(schema("plan.update", "Update .anvil/plan.md", Map.of(
                    "type", "object",
                    "properties", Map.of("content", Map.of("type", "string")),
                    "required", List.of("content"))));
            tools.add(schema("git.status", "Git status (short); fails gracefully if not a git repo", Map.of(
                    "type", "object",
                    "properties", Map.of(),
                    "required", List.of())));
            tools.add(schema("git.diff", "Git diff --stat; fails gracefully if not a git repo", Map.of(
                    "type", "object",
                    "properties", Map.of(),
                    "required", List.of())));
        }
        if (mode == Mode.AGENT || mode == Mode.DEBUG) {
            tools.add(schema("shell.exec", "Execute shell command in workspace (last resort)", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "command", Map.of("type", "string"),
                            "timeout_ms", Map.of("type", "integer")),
                    "required", List.of("command"))));
            tools.add(schema(
                    "diagnostics.collect",
                    "Run Maven compile/test and return structured compiler diagnostics",
                    Map.of(
                            "type",
                            "object",
                            "properties",
                            Map.of(
                                    "scope",
                                    Map.of(
                                            "type",
                                            "string",
                                            "description",
                                            "compile (default) or test")),
                            "required",
                            List.of())));
        }
        return tools;
    }

    public static List<Map<String, Object>> merge(List<Map<String, Object>> builtin, List<Map<String, Object>> extra) {
        List<Map<String, Object>> all = new ArrayList<>(builtin);
        if (extra != null) {
            all.addAll(extra);
        }
        return List.copyOf(all);
    }

    private static Map<String, Object> schema(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }
}
