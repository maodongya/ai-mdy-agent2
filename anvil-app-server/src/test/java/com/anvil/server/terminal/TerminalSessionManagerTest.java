package com.anvil.server.terminal;

import com.anvil.protocol.ThreadStatus;
import com.anvil.server.store.ThreadRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalSessionManagerTest {

    @TempDir
    Path workspace;

    @Test
    void execEchoProducesOutputAndJobDone() throws Exception {
        TerminalSessionManager manager = new TerminalSessionManager();
        ReflectionTestUtils.setField(manager, "enabled", true);
        ReflectionTestUtils.setField(manager, "maxSessionsPerThread", 3);
        ReflectionTestUtils.setField(manager, "maxOutputEvents", 2000);
        ReflectionTestUtils.setField(manager, "jobTimeoutMs", 30_000L);

        ThreadRecord thread = new ThreadRecord("thr_1", workspace, ThreadStatus.ACTIVE, Instant.now());
        TerminalSessionManager.TerminalSession session = manager.create(thread, "bash");

        manager.exec(session.id(), "echo hi", 10_000);

        long deadline = System.currentTimeMillis() + 10_000;
        boolean done = false;
        while (System.currentTimeMillis() < deadline) {
            List<com.anvil.protocol.TerminalEvent> events = manager.eventsSince(session.id(), 0);
            done = events.stream().anyMatch(e -> "terminal.job_done".equals(e.type()));
            if (done) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        assertTrue(done, "expected job_done event");

        List<com.anvil.protocol.TerminalEvent> events = manager.eventsSince(session.id(), 0);
        assertTrue(events.stream().anyMatch(e -> "terminal.output".equals(e.type())));
        assertTrue(events.stream().anyMatch(e -> "terminal.job_start".equals(e.type())));
        assertEquals("IDLE", session.statusId());
    }

    @Test
    void rejectsBusyTerminal() {
        TerminalSessionManager manager = new TerminalSessionManager();
        ReflectionTestUtils.setField(manager, "enabled", true);
        ReflectionTestUtils.setField(manager, "jobTimeoutMs", 300_000L);

        ThreadRecord thread = new ThreadRecord("thr_1", workspace, ThreadStatus.ACTIVE, Instant.now());
        TerminalSessionManager.TerminalSession session = manager.create(thread, "bash");
        session.status = "RUNNING";

        assertThrows(IllegalStateException.class, () -> manager.exec(session.id(), "echo x", 0));
    }

    @Test
    void rejectsPathEscapeInCommand() {
        TerminalSessionManager manager = new TerminalSessionManager();
        ReflectionTestUtils.setField(manager, "enabled", true);
        ReflectionTestUtils.setField(manager, "jobTimeoutMs", 30_000L);

        ThreadRecord thread = new ThreadRecord("thr_1", workspace, ThreadStatus.ACTIVE, Instant.now());
        TerminalSessionManager.TerminalSession session = manager.create(thread, "bash");

        assertThrows(IllegalArgumentException.class, () -> manager.exec(session.id(), "cat /etc/passwd", 0));
    }
}
