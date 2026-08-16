package com.anvil.cli.commands;

import com.anvil.cli.AnvilCli;
import com.anvil.cli.client.AnvilClient;
import com.fasterxml.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 运行命令组：管理与运行实例相关的操作。
 *
 * <p>子命令：{@code run start}（启动运行）、{@code run attach}（订阅运行事件流）。
 */
@Command(name = "run", description = "Run operations", subcommands = {RunStartCommand.class, RunAttachCommand.class})
public class RunCommand implements Callable<Integer> {

    /** 父命令行入口，用于获取服务器地址等全局选项。 */
    @ParentCommand
    private AnvilCli parent;

    /**
     * 获取服务器基础地址。
     *
     * @return 服务器 URL
     */
    String serverUrl() {
        return parent.serverOptions.server;
    }

    /**
     * 无参数调用时仅打印帮助信息。
     *
     * @return 退出码 0
     */
    @Override
    public Integer call() {
        parent.printHelpIfRequested();
        return 0;
    }
}

/**
 * 启动子命令：在指定线程上发起一次 Agent 运行。
 */
@Command(name = "start", description = "Start a run on a thread")
class RunStartCommand implements Callable<Integer> {

    /** 所属的运行命令组。 */
    @ParentCommand
    private RunCommand runCommand;

    /** 目标线程标识。 */
    @Option(names = "--thread", required = true, description = "Thread id")
    String thread;

    /** 发送给模型的首条用户消息。 */
    @Option(names = {"-m", "--message"}, required = true, description = "User message")
    String message;

    /** 运行模式：ask | plan | agent | debug。 */
    @Option(names = "--mode", defaultValue = "agent", description = "Mode: ask|plan|agent|debug")
    String mode;

    /** 使用的模型标识。 */
    @Option(names = "--model", defaultValue = "scripted:read-add", description = "Model id")
    String model;

    /** 启动后是否立即进入事件流订阅模式。 */
    @Option(names = "--attach", description = "Attach to event stream after start")
    boolean attach;

    /**
     * 执行启动操作；若指定 {@code --attach}，随后打印运行事件流。
     *
     * @return 退出码 0
     */
    @Override
    public Integer call() throws Exception {
        AnvilClient client = new AnvilClient(runCommand.serverUrl());
        Map<String, Object> result = client.startRun(thread, mode, model, message);
        System.out.println(result.get("run_id") + " " + result.get("status"));
        if (attach) {
            RunAttachCommand.printEvents(client, String.valueOf(result.get("run_id")), 0, true);
        }
        return 0;
    }
}

/**
 * 订阅子命令：通过 SSE 长连接实时打印运行事件。
 */
@Command(name = "attach", description = "Attach to a run event stream (SSE)")
class RunAttachCommand implements Callable<Integer> {

    /** 所属的运行命令组。 */
    @ParentCommand
    private RunCommand runCommand;

    /** 运行实例标识。 */
    @Option(names = "--run", required = true, description = "Run id")
    String run;

    /** 从指定事件序号开始增量拉取。 */
    @Option(names = "--from-seq", defaultValue = "0", description = "Resume from event seq")
    int fromSeq;

    /** 以原始 JSON 打印事件（而非可读格式）。 */
    @Option(names = "--json", description = "Print raw JSON events")
    boolean json;

    /**
     * 执行订阅并打印事件。
     *
     * @return 退出码 0
     */
    @Override
    public Integer call() throws Exception {
        AnvilClient client = new AnvilClient(runCommand.serverUrl());
        printEvents(client, run, fromSeq, json);
        return 0;
    }

    /**
     * 订阅运行事件流并逐条打印，直到连接结束。
     *
     * <p>非 JSON 模式汇总关键事件——审批请求、消息增量/完成以及
     * 运行的终态（完成/失败/取消）。</p>
     *
     * @param client  Anvil 服务器客户端
     * @param runId   运行实例标识
     * @param fromSeq 起始事件序号
     * @param json    是否以原始 JSON 打印
     */
    static void printEvents(AnvilClient client, String runId, int fromSeq, boolean json) throws Exception {
        AtomicReference<String> pendingApproval = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean sawMessageDelta = new java.util.concurrent.atomic.AtomicBoolean();
        client.attachRun(runId, fromSeq, event -> {
            if (json) {
                System.out.println(event.toString());
            } else {
                String type = event.path("type").asText();
                System.out.println("[" + event.path("seq").asInt() + "] " + type);
                if ("approval.required".equals(type)) {
                    pendingApproval.set(event.path("payload").path("approval_id").asText());
                    System.out.println("  approval required: " + pendingApproval.get());
                }
                if ("message.delta".equals(type)) {
                    sawMessageDelta.set(true);
                    System.out.print(event.path("payload").path("delta").asText(""));
                }
                if ("message.completed".equals(type)) {
                    if (sawMessageDelta.get()) {
                        System.out.println();
                    } else {
                        System.out.println("  " + event.path("payload").path("text").asText(""));
                    }
                }
            }
            String type = event.path("type").asText();
            if ("run.completed".equals(type) || "run.failed".equals(type) || "run.cancelled".equals(type)) {
                // 服务器在发送终态事件后结束事件流
            }
        });
    }
}
