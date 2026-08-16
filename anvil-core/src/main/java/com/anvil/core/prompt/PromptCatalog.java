package com.anvil.core.prompt;

import com.anvil.protocol.Mode;

/** Mode-specific instructions, few-shots, and anti-patterns (Phase 10.5). */
public final class PromptCatalog {

    private PromptCatalog() {}

    public static String modeInstructions(Mode mode) {
        String raw =
                switch (mode) {
                    case ASK -> """
                            ASK mode: read-only exploration. Use search/read tools only.
                            Answer with citations (path:line). Never modify files or run destructive shell.
                            """;
                    case AGENT -> """
                            AGENT mode: implement changes end-to-end.
                            Explore → small edits → verify → summarize. Prefer patch tools over fs.write.
                            """;
                    case PLAN -> """
                            PLAN mode: produce a structured plan in plan.update before any code edits.
                            Use numbered steps or `- [ ]` checkboxes. Wait for user confirmation between phases.
                            """;
                    case DEBUG -> """
                            DEBUG mode: diagnose failures with diagnostics.collect and targeted reads.
                            Fix root cause, re-run verify, explain the fix briefly.
                            """;
                };
        return raw.trim();
    }

    public static String antiPatterns() {
        return """
                <anti_patterns>
                NEVER use shell.exec for grep/find/cat/sed when grep/fs.read tools exist.
                NEVER fs.write entire existing large files — use search_replace or apply_patch.
                NEVER skip verify/diagnostics after Java edits in agent mode.
                NEVER guess file paths — search first (codebase.search, symbols.search, grep).
                NEVER complete with summary text while compile/test errors are still open.
                </anti_patterns>
                """
                .trim();
    }

    public static String toolFewShots(Mode mode) {
        if (mode == Mode.ASK) {
            return """
                    <tool_examples>
                    Find usage: codebase.search query="AuthService login"
                    Read slice: fs.read path="src/.../Foo.java" offset=1 limit=80
                    </tool_examples>
                    """
                    .trim();
        }
        return """
                <tool_examples>
                Discovery: symbols.search query="UserService" → fs.read with offset/limit
                Single edit: search_replace path="Foo.java" old_string="..." new_string="..."
                Multi-file: apply_patch with unified diff OR edit.plan then execute ops
                Verify: diagnostics.collect scope="compile" after edits (also auto-run by harness)
                </tool_examples>
                """
                .trim();
    }
}
