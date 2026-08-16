package com.anvil.server.terminal;

import com.anvil.protocol.TerminalEvent;
import com.anvil.sandbox.PathEscapeException;
import com.anvil.sandbox.PathGuard;
import com.anvil.server.store.ThreadRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 终端会话管理器（M1）。
 *
 * <p>维护多终端会话（每个线程最多 {@code maxSessionsPerThread} 个），
 * 提供新建会话、执行命令、终止命令、事件流订阅能力。</p>
 */
@Service
public class TerminalSessionManager {

    /** 会话实例。 */
    public static final class TerminalSession {
        final String id;
        final String threadId;
        final Path cwd;
        final String title;
        final CopyOnWriteArrayList<TerminalEvent> eventLog = new CopyOnWriteArrayList<>();
        final AtomicLong seq = new AtomicLong(0);
        volatile String status = "IDLE";
        volatile Process process;
        volatile String currentJobId;

        TerminalSession(String id, String threadId, Path cwd, String title) {
            this.id = id;
            this.threadId = threadId;
            this.cwd = cwd;
            this.title = title;
            emitStatus("IDLE");
        }

        void emit(String type, String sessionId, Map<String, Object> payload) {
            eventLog.add(new TerminalEvent(type, sessionId, seq.incrementAndGet(), payload));
        }

        void emitStatus(String status) {
            this.status = status;
            emit("terminal.status", id, Map.of("status", status));
        }

        public String id() {
            return id;
        }

        public Path cwd() {
            return cwd;
        }

        public String statusId() {
            return status;
        }
    }

    /** 单命令最大输出字符数（与 ShellTool 一致）。 */
    private static final int MAX_OUTPUT_CHARS = 256_000;
    private static final Pattern ABS_PATH = Pattern.compile("(?:^|\\s)(/[^\\s;|&]+)");

    private final ConcurrentHashMap<String, TerminalSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicLong sessionSeq = new AtomicLong(0);
    private final AtomicLong jobSeq = new AtomicLong(0);

    @Value("${anvil.terminal.enabled:true}")
    private boolean enabled;

    @Value("${anvil.terminal.max-sessions-per-thread:3}")
    private int maxSessionsPerThread;

    @Value("${anvil.terminal.max-output-lines:2000}")
    private int maxOutputEvents;

    @Value("${anvil.terminal.job-timeout-ms:300000}")
    private long jobTimeoutMs;

    public boolean enabled() {
        return enabled;
    }

    /**
     * 为指定线程创建终端会话。
     *
     * @param thread  线程记录（提供工作目录）
     * @param title   会话标题
     * @return 新建的会话（若超过每线程上限则丢弃最旧会话）
     */
    public TerminalSession create(ThreadRecord thread, String title) {
        if (!enabled) {
            throw new IllegalStateException("terminal is disabled");
        }
        Path cwd = PathGuard.assertInsideWorkspace(thread.workspaceRoot(), ".");
        long count = sessions.values().stream().filter(s -> s.threadId.equals(thread.threadId())).count();
        if (count >= maxSessionsPerThread) {
            sessions.values().stream()
                    .filter(s -> s.threadId.equals(thread.threadId()))
                    .min((a, b) -> Long.compare(a.seq.get(), b.seq.get()))
                    .ifPresent(s -> sessions.remove(s.id));
        }
        String id = "term_" + sessionSeq.incrementAndGet();
        TerminalSession session = new TerminalSession(id, thread.threadId(), cwd, title);
        sessions.put(id, session);
        return session;
    }

    public Optional<TerminalSession> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * 返回自 {code fromSeq} 之后的会话事件快照（增量）。用于 SSE 轮询。
     */
    public List<TerminalEvent> eventsSince(String sessionId, long fromSeq) {
        TerminalSession s = sessions.get(sessionId);
        if (s == null) {
            return List.of();
        }
        List<TerminalEvent> result = new ArrayList<>();
        for (TerminalEvent e : s.eventLog) {
            if (e.seq() > fromSeq) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * 在会话中执行命令（非交互式）。
     *
     * @param sessionId  会话 ID
     * @param command    命令
     * @param timeoutMs  显式超时（毫秒），<=0 时用默认 {@code jobTimeoutMs}
     * @return job_id
     */
    public String exec(String sessionId, String command, long timeoutMs) {
        TerminalSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("terminal session not found: " + sessionId);
        }
        if (!"IDLE".equals(session.status)) {
            throw new IllegalStateException("terminal is busy: " + session.status);
        }
        validateCommand(session.cwd, command);
        long timeout = timeoutMs > 0 ? timeoutMs : jobTimeoutMs;
        String jobId = "job_" + jobSeq.incrementAndGet();
        session.currentJobId = jobId;

        executor.submit(() -> runCommand(session, sessionId, command, timeout, jobId));
        return jobId;
    }

    private void runCommand(TerminalSession session, String sessionId, String command, long timeout, String jobId) {
        session.emit("terminal.job_start", sessionId, Map.of("command", command, "job_id", jobId));
        session.emitStatus("RUNNING");
        long start = System.currentTimeMillis();
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
        pb.directory(session.cwd.toFile());
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            session.process = process;
            Thread reader = new Thread(() -> streamOutput(session, process), "term-out-" + sessionId);
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
            reader.join(200);
            if (!finished) {
                process.destroyForcibly();
                session.emit("terminal.job_done", sessionId, Map.of(
                        "job_id", jobId,
                        "exit_code", "TIMEOUT",
                        "duration_ms", System.currentTimeMillis() - start,
                        "timed_out", true));
            } else {
                session.emit("terminal.job_done", sessionId, Map.of(
                        "job_id", jobId,
                        "exit_code", process.exitValue(),
                        "duration_ms", System.currentTimeMillis() - start));
            }
            session.process = null;
            session.emitStatus("IDLE");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (session.process != null) {
                session.process.destroyForcibly();
            }
            session.process = null;
            session.emit("terminal.error", sessionId, Map.of("message", "interrupted", "job_id", jobId));
            session.emitStatus("IDLE");
        } catch (Exception e) {
            session.process = null;
            session.emit("terminal.error", sessionId, Map.of("message", String.valueOf(e.getMessage()), "job_id", jobId));
            session.emitStatus("IDLE");
        } finally {
            trimEvents(session);
        }
    }

    /** 终止会话当前运行的命令（强制）。 */
    public void stop(String sessionId) {
        TerminalSession s = sessions.get(sessionId);
        if (s != null && s.process != null) {
            s.process.destroyForcibly();
        }
    }

    /** 订阅函数式消费者（暂未使用于 REST，预留）。 */
    public void events(String sessionId, long fromSeq, java.util.function.Consumer<TerminalEvent> consumer) {
        for (TerminalEvent e : eventsSince(sessionId, fromSeq)) {
            consumer.accept(e);
        }
    }

    private static void validateCommand(Path cwd, String command) {
        Matcher matcher = ABS_PATH.matcher(command);
        while (matcher.find()) {
            String abs = matcher.group(1);
            try {
                PathGuard.assertInsideWorkspace(cwd, abs);
            } catch (PathEscapeException e) {
                throw new IllegalArgumentException("command path escapes workspace: " + abs);
            }
        }
    }

    private void trimEvents(TerminalSession session) {
        while (session.eventLog.size() > maxOutputEvents) {
            session.eventLog.remove(0);
        }
    }

    /** 后台读取进程输出，按块推送 terminal.output 事件。 */
    private void streamOutput(TerminalSession session, Process process) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> batch = new ArrayList<>();
            String line;
            int chars = 0;
            while ((line = reader.readLine()) != null) {
                batch.add(line);
                chars += line.length() + 1;
                if (batch.size() >= 200 || chars >= 8000) {
                    flushOutput(session, batch);
                    batch = new ArrayList<>();
                    chars = 0;
                }
            }
            if (!batch.isEmpty()) {
                flushOutput(session, batch);
            }
        } catch (Exception ignored) {
            // 进程被终止或 IO 中断：静默忽略
        }
    }

    private void flushOutput(TerminalSession session, List<String> batch) {
        String text = String.join("\n", batch);
        boolean truncated = false;
        if (text.length() > MAX_OUTPUT_CHARS) {
            text = text.substring(0, MAX_OUTPUT_CHARS) + "\n...[truncated]";
            truncated = true;
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("lines", List.of(text.split("\n", -1)));
        payload.put("truncated", truncated);
        session.emit("terminal.output", session.id, payload);
        trimEvents(session);
    }
}
