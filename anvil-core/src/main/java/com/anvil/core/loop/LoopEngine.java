package com.anvil.core.loop;

import com.anvil.core.compact.ContextBudget;
import com.anvil.core.compact.ContextCompactor;
import com.anvil.core.compact.MessageHistorySanitizer;
import com.anvil.core.instructions.PlanLoader;
import com.anvil.core.instructions.SkillLoader;
import com.anvil.core.model.ModelProvider;
import com.anvil.core.model.ModelTurn;
import com.anvil.core.model.ModelTurnContext;
import com.anvil.core.model.ModelUsage;
import com.anvil.core.model.ToolCallIntent;
import com.anvil.core.policy.Decision;
import com.anvil.core.policy.PolicyEngine;
import com.anvil.core.policy.PolicyInput;
import com.anvil.core.prompt.PromptBuilder;
import com.anvil.core.prompt.PromptBundle;
import com.anvil.core.tools.ToolExecutor;
import com.anvil.protocol.ApprovalDecision;
import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.Event;
import com.anvil.protocol.ProtocolJson;
import com.anvil.protocol.RunStatus;
import com.anvil.protocol.SideEffect;
import com.anvil.protocol.ToolResult;
import com.anvil.tools.EditSummary;
import com.anvil.tools.PlanTool;
import com.anvil.tools.TextDiff;
import com.anvil.tools.ToolSideEffects;
import com.anvil.tools.index.IndexService;
import com.anvil.sandbox.PathGuard;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class LoopEngine {

    private LoopEngine() {}

    public static LoopResult run(RunRequest request, ModelProvider model, ApprovalGate approvalGate) {
        return run(request, model, approvalGate, LoopOptions.defaults(request.mode()), null);
    }

    public static LoopResult run(
            RunRequest request,
            ModelProvider model,
            ApprovalGate approvalGate,
            java.util.function.Consumer<Event> eventSink) {
        return run(request, model, approvalGate, LoopOptions.defaults(request.mode()), eventSink);
    }

    public static LoopResult run(
            RunRequest request,
            ModelProvider model,
            ApprovalGate approvalGate,
            LoopOptions options,
            java.util.function.Consumer<Event> eventSink) {
        return run(request, model, approvalGate, options, List.of(), eventSink);
    }

    public static LoopResult run(
            RunRequest request,
            ModelProvider model,
            ApprovalGate approvalGate,
            LoopOptions options,
            List<Map<String, Object>> priorHistory,
            java.util.function.Consumer<Event> eventSink) {
        RunContext ctx = new RunContext(request.threadId(), request.runId(), eventSink);
        ActiveRunTracker.track(ctx);
        try {
            return runLoop(request, model, approvalGate, options, priorHistory, ctx);
        } finally {
            ActiveRunTracker.untrack(request.runId());
        }
    }

    private static LoopResult runLoop(
            RunRequest request,
            ModelProvider model,
            ApprovalGate approvalGate,
            LoopOptions options,
            List<Map<String, Object>> priorHistory,
            RunContext ctx) {
        ToolExecutor tools = new ToolExecutor(request.workspaceRoot(), request.shellTimeoutMs(), options.mcpBridge());
        ContextBudget budget = options.contextBudget();

        seedHistory(ctx, request, priorHistory, budget, options.runProfile());

        ctx.emit(
                "run.started",
                Map.of(
                        "run_id", request.runId(),
                        "mode", request.mode().wireValue(),
                        "model", request.model(),
                        "profile", options.runProfile().name().toLowerCase(),
                        "max_steps", request.maxSteps(),
                        "context_budget", budget.compactThresholdTokens()));

        for (int step = 0; step < request.maxSteps(); step++) {
            if (ctx.isCancelled()) {
                ctx.emit("run.cancelled", Map.of("reason", "user"));
                return result(ctx, RunStatus.CANCELLED);
            }

            ContextCompactor.Result compacted = ContextCompactor.compact(ctx.history(), budget, ctx.anchors());
            if (compacted.compacted()) {
                ctx.replaceHistory(compacted.messages());
                ctx.emit(
                        "context.compacted",
                        Map.of(
                                "before_tokens", compacted.beforeTokens(),
                                "after_tokens", compacted.afterTokens(),
                                "keep_recent", budget.keepRecentMessages()));
            }

            PromptBundle prompt = PromptBuilder.build(
                    request.mode(),
                    request.workspaceRoot(),
                    options.sandboxTier(),
                    options.gitBranch(),
                    ctx.history(),
                    null,
                    options.toolSchemas());

            int contextMessages = ctx.history().size();
            int contextTokens = ContextCompactor.estimateTokens(ctx.history());
            ctx.emit(
                    "step.started",
                    Map.of(
                            "step", step + 1,
                            "context_messages", contextMessages,
                            "context_tokens_estimate", contextTokens,
                            "tools_available", options.toolSchemas().size()));

            int stepNumber = step + 1;
            String messageId = "msg_" + stepNumber;
            var turnOpt = model.nextTurn(new ModelTurnContext(
                    ctx.history(),
                    prompt,
                    delta -> ctx.emit(
                            "message.delta",
                            Map.of("message_id", messageId, "step", stepNumber, "delta", delta))));
            if (turnOpt.isEmpty()) {
                ctx.emit("run.failed", Map.of("error", errorMap(ErrorCodes.MODEL_BAD_RESPONSE, "model returned no turn")));
                return result(ctx, RunStatus.FAILED);
            }

            ModelTurn turn = turnOpt.get();
            emitModelCompleted(ctx, stepNumber, turn);
            if (turn.isMessage()) {
                ctx.emit(
                        "message.completed",
                        Map.of("role", "assistant", "message_id", messageId, "text", turn.messageText()));
                ctx.emit("run.completed", completedPayload(ctx, RunStatus.SUCCEEDED.wireValue()));
                return result(ctx, RunStatus.SUCCEEDED);
            }

            ctx.incrementToolCalls(turn.toolCalls().size());

            appendAssistantToolCalls(ctx, turn.toolCalls());
            executeToolTurn(request, model, approvalGate, options, tools, budget, ctx, turn.toolCalls());
        }

        ctx.emit("run.failed", Map.of("error", errorMap(ErrorCodes.BUDGET_EXCEEDED, "max steps exceeded")));
        return result(ctx, RunStatus.FAILED);
    }

    private static void seedHistory(
            RunContext ctx,
            RunRequest request,
            List<Map<String, Object>> priorHistory,
            ContextBudget budget,
            RunProfile profile) {
        List<Map<String, Object>> prior = priorHistory == null ? List.of() : MessageHistorySanitizer.sanitize(priorHistory);
        prior.forEach(ctx::appendHistory);
        if (profile == RunProfile.COMPLEX) {
            ctx.appendHistory(Map.of(
                    "role",
                    "developer",
                    "content",
                    """
                    Complex task mode: break work into phases, use plan.update for multi-step plans,
                    prefer grep/codebase.search and fs.glob before many fs.read calls, and verify each phase before continuing.
                    """.trim()));
        }
        PlanLoader.loadPlan(request.workspaceRoot()).ifPresent(plan -> ctx.appendHistory(Map.of(
                "role",
                "developer",
                "content",
                "<active_plan path=\"" + PlanTool.PLAN_PATH + "\">\n" + plan + "\n</active_plan>")));
        if (request.editorContext() != null && !request.editorContext().isBlank()) {
            ctx.appendHistory(Map.of("role", "developer", "content", request.editorContext()));
        }
        String skills = SkillLoader.loadForRun(request.workspaceRoot(), request.userMessage());
        if (!skills.isBlank()) {
            ctx.appendHistory(Map.of("role", "developer", "content", skills));
        }
        ctx.appendHistory(Map.of("role", "user", "content", request.userMessage()));
        if (!prior.isEmpty()) {
            ctx.emit(
                    "thread.memory.loaded",
                    Map.of("messages", prior.size(), "tokens_estimate", ContextCompactor.estimateTokens(prior)));
        }
    }

    private static LoopResult result(RunContext ctx, RunStatus status) {
        return new LoopResult(ctx.events(), status, ctx.history());
    }

    private static void appendAssistantToolCalls(RunContext ctx, List<ToolCallIntent> calls) {
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        for (ToolCallIntent call : calls) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", call.name());
            function.put(
                    "arguments",
                    ProtocolJson.toJson(call.arguments() == null ? Map.of() : call.arguments()));
            Map<String, Object> tc = new LinkedHashMap<>();
            tc.put("id", call.id());
            tc.put("type", "function");
            tc.put("function", function);
            toolCalls.add(tc);
        }
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", "");
        assistant.put("tool_calls", toolCalls);
        ctx.appendHistory(assistant);
    }

    private static void recordToolFailure(RunContext ctx, ToolCallIntent call, String code, String message, ContextBudget budget) {
        ctx.emit(
                "tool.failed",
                Map.of("tool_call_id", call.id(), "error", errorMap(code, message)));
        appendToolHistory(ctx, call, "error", "", code, message, budget);
    }

    private static void recordToolOutcome(RunContext ctx, ToolCallIntent call, ToolResult result, ContextBudget budget) {
        if ("ok".equals(result.status())) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tool_call_id", result.toolCallId());
            if (result.truncated()) {
                payload.put("truncated", true);
            }
            if (result.content() != null && !result.content().isBlank()) {
                payload.put("preview", previewToolOutput(result.content()));
            }
            if (result.artifactRef() != null) {
                payload.put("artifact_ref", result.artifactRef());
            }
            ctx.emit("tool.completed", payload);
        } else {
            ErrorInfo error = result.error() != null
                    ? result.error()
                    : ErrorInfo.of(ErrorCodes.TOOL_FAILED, "tool " + result.status(), false);
            ctx.emit(
                    "tool.failed",
                    Map.of("tool_call_id", result.toolCallId(), "error", errorMap(error)));
            appendToolHistory(ctx, call, result.status(), result.content(), error.code(), error.message(), budget);
            return;
        }
        appendToolHistory(ctx, call, result.status(), result.content(), null, null, budget);
    }

    private static void appendToolHistory(
            RunContext ctx,
            ToolCallIntent call,
            String status,
            String content,
            String errorCode,
            String errorMessage,
            ContextBudget budget) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", "tool");
        item.put("tool_call_id", call.id());
        item.put("name", call.name());
        item.put("status", status);
        item.put("content", ContextCompactor.truncateContent(content, budget.maxToolContentChars()));
        if (errorCode != null) {
            item.put("error", errorMap(errorCode, errorMessage == null ? "" : errorMessage));
        }
        ctx.appendHistory(item);
    }

    private static Map<String, Object> completedPayload(RunContext ctx, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("usage", ctx.usageSummary());
        return payload;
    }

    private static void emitModelCompleted(RunContext ctx, int step, ModelTurn turn) {
        ModelUsage usage = turn.usage();
        ctx.recordModelUsage(usage);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", step);
        if (turn.isMessage()) {
            payload.put("kind", "message");
        } else if (turn.hasToolCalls()) {
            payload.put("kind", "tool_calls");
            payload.put("tool_count", turn.toolCalls().size());
        }
        if (usage != null) {
            payload.put("input_tokens", usage.inputTokens());
            payload.put("output_tokens", usage.outputTokens());
            if (usage.cachedTokens() != null) {
                payload.put("cached_tokens", usage.cachedTokens());
            }
            payload.put("latency_ms", usage.latencyMs());
        }
        payload.put("usage_total", ctx.usageSummary());
        ctx.emit("model.completed", payload);
    }

    private static String previewToolOutput(String content) {
        String oneLine = content.replace('\n', ' ').trim();
        return oneLine.length() <= 160 ? oneLine : oneLine.substring(0, 157) + "...";
    }

    private static Map<String, Object> errorMap(ErrorInfo error) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", error.code());
        map.put("message", error.message());
        map.put("retryable", error.retryable());
        return map;
    }

    private static Map<String, Object> errorMap(String code, String message) {
        return errorMap(ErrorInfo.of(code, message, false));
    }

    private static String readPreviousContent(ToolExecutor tools, ToolCallIntent call) {
        if (!VerifyPass.isWriteTool(call.name()) || call.arguments() == null) {
            return "";
        }
        Object pathObj = call.arguments().get("path");
        if (pathObj == null) {
            return "";
        }
        try {
            Path abs = PathGuard.assertInsideWorkspace(tools.fsTools().workspaceRoot(), String.valueOf(pathObj));
            if (Files.isRegularFile(abs)) {
                return Files.readString(abs);
            }
        } catch (Exception ignored) {
            // new file or denied
        }
        return "";
    }

    private static void executeToolTurn(
            RunRequest request,
            ModelProvider model,
            ApprovalGate approvalGate,
            LoopOptions options,
            ToolExecutor tools,
            ContextBudget budget,
            RunContext ctx,
            List<ToolCallIntent> calls) {
        List<ToolCallIntent> approved = new ArrayList<>();
        for (ToolCallIntent call : calls) {
            if (ctx.isCancelled()) {
                return;
            }
            if (!resolveAndApprove(request, approvalGate, options, ctx, budget, call)) {
                continue;
            }
            approved.add(call);
        }

        int index = 0;
        while (index < approved.size()) {
            if (ctx.isCancelled()) {
                return;
            }
            ToolCallIntent first = approved.get(index);
            SideEffect effect = ToolSideEffects.forTool(first.name());
            Decision decision = PolicyEngine.evaluate(new PolicyInput(
                    request.mode(),
                    first.name(),
                    effect,
                    ToolExecutor.previewFor(first.name(), first.arguments()),
                    ctx.sessionAllows(),
                    options.autoApprovePatchTools(),
                    options.autoApproveWrites()));

            if (options.parallelReadTools()
                    && ParallelToolRunner.canParallelize(first.name(), decision, true)) {
                int end = index;
                while (end < approved.size()) {
                    ToolCallIntent c = approved.get(end);
                    SideEffect e = ToolSideEffects.forTool(c.name());
                    Decision d = PolicyEngine.evaluate(new PolicyInput(
                            request.mode(),
                            c.name(),
                            e,
                            ToolExecutor.previewFor(c.name(), c.arguments()),
                            ctx.sessionAllows(),
                            options.autoApprovePatchTools(),
                            options.autoApproveWrites()));
                    if (!ParallelToolRunner.canParallelize(c.name(), d, true)) {
                        break;
                    }
                    end++;
                }
                List<ToolCallIntent> batch = approved.subList(index, end);
                if (batch.size() > 1) {
                    ctx.emit("tools.parallel.started", Map.of("count", batch.size()));
                }
                for (ToolCallIntent call : batch) {
                    ctx.emit("tool.started", Map.of("tool_call_id", call.id(), "name", call.name()));
                }
                List<ToolResult> results = ParallelToolRunner.runBatch(tools, batch);
                for (int i = 0; i < batch.size(); i++) {
                    finishToolCall(request, options, tools, budget, ctx, batch.get(i), results.get(i), "");
                }
                if (batch.size() > 1) {
                    ctx.emit("tools.parallel.completed", Map.of("count", batch.size()));
                }
                index = end;
            } else {
                ToolCallIntent call = approved.get(index);
                ctx.emit("tool.started", Map.of("tool_call_id", call.id(), "name", call.name()));
                String previousContent = readPreviousContent(tools, call);
                ToolResult result;
                try {
                    result = tools.execute(call.id(), call.name(), call.arguments());
                } catch (IllegalArgumentException e) {
                    result = ToolExecutor.invalidArgs(call.id(), call.name(), e.getMessage());
                }
                finishToolCall(request, options, tools, budget, ctx, call, result, previousContent);
                index++;
            }
        }
    }

    private static boolean resolveAndApprove(
            RunRequest request,
            ApprovalGate approvalGate,
            LoopOptions options,
            RunContext ctx,
            ContextBudget budget,
            ToolCallIntent call) {
        ctx.emit(
                "tool.planned",
                Map.of("tool_call_id", call.id(), "name", call.name(), "arguments", call.arguments()));

        SideEffect sideEffect = ToolSideEffects.forTool(call.name());
        Map<String, Object> preview = ToolExecutor.previewFor(call.name(), call.arguments());
        Decision decision = PolicyEngine.evaluate(new PolicyInput(
                request.mode(), call.name(), sideEffect, preview, ctx.sessionAllows(), options.autoApprovePatchTools(), options.autoApproveWrites()));

        if (decision.type() == Decision.Type.DENY) {
            recordToolFailure(ctx, call, decision.code(), decision.message(), budget);
            return false;
        }

        if (decision.type() == Decision.Type.APPROVE) {
            String approvalId = "appr_" + call.id();
            ctx.emit(
                    "approval.required",
                    Map.of(
                            "approval_id", approvalId,
                            "tool", call.name(),
                            "risk", sideEffect.wireValue(),
                            "preview", preview));

            ApprovalDecision approvalDecision;
            try {
                approvalDecision = approvalGate
                        .waitForApproval(approvalId, preview, request.approvalTimeoutMs())
                        .get(request.approvalTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                ctx.emit("approval.resolved", Map.of("approval_id", approvalId, "decision", "timeout"));
                recordToolFailure(ctx, call, ErrorCodes.APPROVAL_TIMEOUT, "approval timeout", budget);
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ctx.cancel();
                return false;
            } catch (ExecutionException e) {
                recordToolFailure(
                        ctx,
                        call,
                        ErrorCodes.INTERNAL,
                        e.getCause() == null ? e.getMessage() : e.getCause().getMessage(),
                        budget);
                return false;
            }

            ctx.emit("approval.resolved", Map.of("approval_id", approvalId, "decision", approvalDecision.wireValue()));

            if (approvalDecision == ApprovalDecision.DENY || approvalDecision == ApprovalDecision.ALWAYS_DENY) {
                recordToolFailure(ctx, call, ErrorCodes.APPROVAL_DENIED, "approval denied", budget);
                return false;
            }
            if (approvalDecision == ApprovalDecision.ALLOW_SESSION) {
                ctx.sessionAllows().add(call.name());
            }
        }
        return true;
    }

    private static void finishToolCall(
            RunRequest request,
            LoopOptions options,
            ToolExecutor tools,
            ContextBudget budget,
            RunContext ctx,
            ToolCallIntent call,
            ToolResult result,
            String previousContent) {
        recordToolOutcome(ctx, call, result, budget);
        trackToolAnchors(ctx, call, result);
        if ("ok".equals(result.status()) && VerifyPass.isWriteTool(call.name())) {
            IndexService.invalidate(request.workspaceRoot());
            emitEditSummary(ctx, call, previousContent, tools);
            VerifyPass.maybeRun(
                    ctx,
                    request.workspaceRoot(),
                    options.verifyConfig(),
                    call.name(),
                    call.arguments(),
                    budget,
                    request.shellTimeoutMs());
        }
    }

    private static void emitEditSummary(RunContext ctx, ToolCallIntent call, String previousContent, ToolExecutor tools) {
        EditSummary.Delta delta = VerifyPass.deltaFor(call.name(), call.arguments(), previousContent);
        if (delta == null) {
            return;
        }
        ctx.emit(
                "edit.summary",
                Map.of(
                        "path", delta.path(),
                        "lines_added", delta.linesAdded(),
                        "lines_removed", delta.linesRemoved(),
                        "tool", call.name()));

        String newContent = readCurrentContent(tools, delta.path());
        String diff = TextDiff.unified(previousContent, newContent, 3);
        Map<String, Object> previewPayload = new LinkedHashMap<>();
        previewPayload.put("path", delta.path());
        previewPayload.put("diff", diff);
        previewPayload.put("lines_added", delta.linesAdded());
        previewPayload.put("lines_removed", delta.linesRemoved());
        if (previousContent.length() <= 64_000) {
            previewPayload.put("previous_content", previousContent);
        }
        if (newContent.length() <= 64_000) {
            previewPayload.put("new_content", newContent);
        }
        ctx.emit("edit.preview", previewPayload);
    }

    private static String readCurrentContent(ToolExecutor tools, String relativePath) {
        try {
            Path abs = PathGuard.assertInsideWorkspace(tools.fsTools().workspaceRoot(), relativePath);
            if (Files.isRegularFile(abs)) {
                return Files.readString(abs);
            }
        } catch (Exception ignored) {
            // new or unreadable
        }
        return "";
    }

    private static void trackToolAnchors(RunContext ctx, ToolCallIntent call, ToolResult result) {
        if ("diagnostics.collect".equals(call.name()) && !"ok".equals(result.status())) {
            if (result.content() != null && !result.content().isBlank()) {
                ctx.anchors().recordFailure(result.content());
            }
        }
        if (!"ok".equals(result.status()) || call.arguments() == null) {
            return;
        }
        Object pathObj = call.arguments().get("path");
        if ("fs.read".equals(call.name()) && pathObj != null) {
            ctx.anchors().recordRead(String.valueOf(pathObj));
        }
        if (VerifyPass.isWriteTool(call.name()) && pathObj != null) {
            ctx.anchors().recordWrite(String.valueOf(pathObj));
        }
    }
}
