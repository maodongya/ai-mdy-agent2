package com.anvil.core.orchestrator;

import com.anvil.core.compact.ContextBudget;
import com.anvil.core.compact.ContextCompactor;
import com.anvil.core.compact.ToolContentBudget;
import com.anvil.core.loop.RunContext;
import com.anvil.core.model.ModelProvider;
import com.anvil.core.model.ModelTurn;
import com.anvil.core.model.ModelTurnContext;
import com.anvil.core.model.ToolCallIntent;
import com.anvil.core.prompt.PromptBuildOptions;
import com.anvil.core.prompt.PromptBuilder;
import com.anvil.core.prompt.PromptBundle;
import com.anvil.core.tools.ToolCatalog;
import com.anvil.core.tools.ToolExecutor;
import com.anvil.protocol.Mode;
import com.anvil.protocol.SandboxTier;
import com.anvil.protocol.SideEffect;
import com.anvil.protocol.ToolResult;
import com.anvil.tools.ToolSideEffects;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only explore sub-agent: file list + summaries for the main agent (Phase 10.1 / 11.4). */
public final class ExploreAgent {

    private static final Set<String> READ_TOOLS = Set.of(
            "grep",
            "codebase.search",
            "symbols.search",
            "fs.read",
            "fs.glob",
            "git.status",
            "git.diff",
            "diagnostics.collect");

    private ExploreAgent() {}

    public record ExploreReport(String markdown, List<String> files, int toolCalls) {}

    public static ExploreReport run(
            Path workspace,
            Mode mode,
            String userMessage,
            ModelProvider model,
            long shellTimeoutMs,
            int maxSteps,
            RunContext ctx) {
        return run(workspace, mode, userMessage, model, shellTimeoutMs, maxSteps, 8_000, ctx);
    }

    public static ExploreReport run(
            Path workspace,
            Mode mode,
            String userMessage,
            ModelProvider model,
            long shellTimeoutMs,
            int maxSteps,
            int maxTokensBudget,
            RunContext ctx) {
        if (userMessage == null || userMessage.isBlank()) {
            return empty();
        }
        ctx.emit("explore.started", Map.of("max_steps", maxSteps, "token_budget", maxTokensBudget));
        ToolExecutor tools = new ToolExecutor(workspace, shellTimeoutMs, null);
        List<Map<String, Object>> readSchemas = ToolCatalog.builtinSchemas(mode).stream()
                .filter(s -> READ_TOOLS.contains(schemaName(s)))
                .toList();

        List<Map<String, Object>> history = new ArrayList<>();
        history.add(Map.of(
                "role",
                "developer",
                "content",
                """
                Explore sub-agent (read-only). Use codebase.search / symbols.search / grep / fs.read \
                to map relevant files. Reply with ONLY a markdown bullet list:
                - `path` — one-line summary
                Do not edit files.
                """
                        .trim()));
        history.add(Map.of("role", "user", "content", userMessage));

        Set<String> files = new LinkedHashSet<>();
        int toolCalls = 0;
        int tokensUsed = 0;

        for (int step = 0; step < maxSteps; step++) {
            if (tokensUsed >= maxTokensBudget) {
                break;
            }
            if (step > 0 && step % 2 == 0) {
                ContextCompactor.Result compacted =
                        ContextCompactor.compact(history, ContextBudget.standard(), null);
                if (compacted.compacted()) {
                    history.clear();
                    history.addAll(compacted.messages());
                }
            }

            PromptBuildOptions opts = step == 0 ? PromptBuildOptions.firstStep() : new PromptBuildOptions(step, false, false);
            PromptBundle prompt = PromptBuilder.build(
                    mode, workspace, SandboxTier.READ_ONLY, "unknown", history, null, readSchemas, opts);
            var turnOpt = model.nextTurn(new ModelTurnContext(history, prompt));
            if (turnOpt.isEmpty()) {
                break;
            }
            ModelTurn turn = turnOpt.get();
            if (turn.usage() != null) {
                tokensUsed += turn.usage().inputTokens() + turn.usage().outputTokens();
            }
            if (turn.isMessage()) {
                String report = formatReport(turn.messageText(), files, maxTokensBudget);
                ctx.emit("explore.completed", Map.of("files", files.size(), "tool_calls", toolCalls, "tokens_used", tokensUsed));
                return new ExploreReport(report, List.copyOf(files), toolCalls);
            }
            appendAssistantTools(history, turn);
            for (ToolCallIntent call : turn.toolCalls()) {
                if (!READ_TOOLS.contains(call.name())
                        || ToolSideEffects.forTool(call.name()) != SideEffect.READ) {
                    history.add(toolResult(call, "error", "explore agent is read-only"));
                    continue;
                }
                toolCalls++;
                ToolResult result;
                try {
                    result = tools.execute(call.id(), call.name(), call.arguments());
                } catch (IllegalArgumentException e) {
                    result = ToolExecutor.invalidArgs(call.id(), call.name(), e.getMessage());
                }
                collectFiles(call, result, files);
                history.add(
                        toolResult(
                                call,
                                result.status(),
                                ToolContentBudget.apply(result.content(), call.name(), 2_000)));
            }
        }
        ctx.emit(
                "explore.completed",
                Map.of("files", files.size(), "tool_calls", toolCalls, "status", "max_steps", "tokens_used", tokensUsed));
        return new ExploreReport(formatReport("Explore reached step limit.", files, maxTokensBudget), List.copyOf(files), toolCalls);
    }

    private static String schemaName(Map<String, Object> schema) {
        Object fn = schema.get("function");
        if (fn instanceof Map<?, ?> map) {
            return String.valueOf(map.get("name"));
        }
        return String.valueOf(schema.get("name"));
    }

    private static void collectFiles(ToolCallIntent call, ToolResult result, Set<String> files) {
        if (!"ok".equals(result.status()) || result.content() == null) {
            return;
        }
        for (String line : result.content().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.contains(".java") || trimmed.contains(".md") || trimmed.contains(".xml")) {
                int idx = trimmed.indexOf(':');
                String candidate = idx > 0 ? trimmed.substring(0, idx).trim() : trimmed;
                if (candidate.contains("/") && candidate.indexOf(' ') < 0) {
                    files.add(candidate.replace('\\', '/'));
                }
            }
        }
        Object path = call.arguments() == null ? null : call.arguments().get("path");
        if (path != null && !String.valueOf(path).isBlank()) {
            files.add(String.valueOf(path).replace('\\', '/'));
        }
    }

    private static String formatReport(String modelText, Set<String> files, int maxTokensBudget) {
        StringBuilder sb = new StringBuilder("<explore_report>\n");
        if (modelText != null && !modelText.isBlank()) {
            sb.append(ContextCompactor.truncateContent(modelText.trim(), Math.min(2_400, maxTokensBudget * 3)));
            sb.append('\n');
        }
        if (!files.isEmpty()) {
            sb.append("\nfiles_seen:\n");
            int count = 0;
            for (String f : files) {
                if (count++ >= 40) {
                    sb.append("- … ").append(files.size() - 40).append(" more\n");
                    break;
                }
                sb.append("- ").append(f).append('\n');
            }
        }
        sb.append("</explore_report>");
        String report = sb.toString().trim();
        int lineCount = report.split("\n", -1).length;
        if (lineCount > 120) {
            return ContextCompactor.truncateContent(report, 3_600) + "\n[explore report truncated to token budget]";
        }
        return report;
    }

    private static ExploreReport empty() {
        return new ExploreReport("", List.of(), 0);
    }

    private static Map<String, Object> toolResult(ToolCallIntent call, String status, String content) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", "tool");
        item.put("tool_call_id", call.id());
        item.put("name", call.name());
        item.put("status", status);
        item.put("content", content);
        return item;
    }

    private static void appendAssistantTools(List<Map<String, Object>> history, ModelTurn turn) {
        history.add(turn.toHistoryMessage());
    }
}
