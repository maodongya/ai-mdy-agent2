package com.anvil.ui;

import com.anvil.protocol.Event;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiRunEventPumpTest {

    @BeforeAll
    static void initFx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void coalescesDeltaEventsBeforeDrain() throws Exception {
        RecordingListener listener = new RecordingListener();
        UiRunEventPump pump = new UiRunEventPump(listener);
        runOnFxAndWait(() -> {
            pump.start();
            pump.submit(delta("a"));
            pump.submit(delta("b"));
            pump.submit(delta("c"));
            pump.stop();
        });
        assertEquals(List.of("abc"), listener.deltas);
    }

    @Test
    void preservesNonDeltaEventOrderAfterFlush() throws Exception {
        RecordingListener listener = new RecordingListener();
        UiRunEventPump pump = new UiRunEventPump(listener);
        runOnFxAndWait(() -> {
            pump.start();
            pump.submit(event("step.started", 1));
            pump.submit(delta("x"));
            pump.submit(event("tool.planned", 2));
            pump.stop();
        });
        assertEquals(List.of("step.started", "tool.planned"), listener.types);
        assertEquals(List.of("x"), listener.deltas);
        assertTrue(listener.frameEnds > 0);
    }

    @Test
    void defersModalApprovalEventsToNextFxPulse() throws Exception {
        RecordingListener listener = new RecordingListener();
        UiRunEventPump pump = new UiRunEventPump(listener);
        runOnFxAndWait(() -> {
            pump.start();
            pump.submit(event("approval.required", 1));
            pump.stop();
        });
        runOnFxAndWait(() -> {});
        assertEquals(List.of("approval.required"), listener.types);
    }

    private static void runOnFxAndWait(Runnable action) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
    }

    private static Event delta(String text) {
        return new Event("1.0", "t", "r", 0, "message.delta", "ts", Map.of("delta", text));
    }

    private static Event event(String type, int seq) {
        return new Event("1.0", "t", "r", seq, type, "ts", Map.of());
    }

    private static final class RecordingListener implements UiRunEventPump.Listener {
        private final List<String> deltas = new ArrayList<>();
        private final List<String> types = new ArrayList<>();
        private int frameEnds;

        @Override
        public void onDeltaBatch(String text) {
            deltas.add(text);
        }

        @Override
        public void onEvent(Event event) {
            types.add(event.type());
        }

        @Override
        public void onFrameEnd() {
            frameEnds++;
        }
    }
}
