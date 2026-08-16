package com.anvil.ui;

import com.anvil.protocol.Event;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;

import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Batches SSE events onto the JavaFX thread at a fixed frame rate (~30fps). Avoids flooding
 * {@link Platform#runLater} and keeps streaming updates off expensive controls like ListView.
 */
final class UiRunEventPump {

    interface Listener {
        void onDeltaBatch(String text);

        void onEvent(Event event);

        void onFrameEnd();
    }

    private static final long FRAME_NS = 33_000_000L;
    private static final int MAX_EVENTS_PER_FRAME = 12;
    private static final Set<String> MODAL_EVENT_TYPES = Set.of("approval.required");

    private final Listener listener;
    private final ConcurrentLinkedQueue<Event> queue = new ConcurrentLinkedQueue<>();
    private final StringBuilder deltaBuffer = new StringBuilder();
    private final AtomicBoolean active = new AtomicBoolean();
    private final AnimationTimer timer =
            new AnimationTimer() {
                private long lastPulseNs;

                @Override
                public void handle(long now) {
                    if (!active.get()) {
                        return;
                    }
                    if (now - lastPulseNs < FRAME_NS) {
                        return;
                    }
                    lastPulseNs = now;
                    drainFrame(false);
                }
            };

    UiRunEventPump(Listener listener) {
        this.listener = listener;
    }

    void start() {
        if (active.compareAndSet(false, true)) {
            runOnFxThread(timer::start);
        }
    }

    void stop() {
        if (active.compareAndSet(true, false)) {
            runOnFxThread(() -> {
                timer.stop();
                drainAllPending();
            });
        }
    }

    void submit(Event event) {
        if ("message.delta".equals(event.type())) {
            appendDelta(event);
            return;
        }
        queue.offer(event);
    }

    private void appendDelta(Event event) {
        Object delta = event.payload().get("delta");
        if (delta == null) {
            return;
        }
        String piece = String.valueOf(delta);
        if (piece.isEmpty()) {
            return;
        }
        synchronized (deltaBuffer) {
            deltaBuffer.append(piece);
        }
    }

    private void drainFrame(boolean drainAll) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> drainFrame(drainAll));
            return;
        }
        emitDeltas();
        int limit = drainAll ? Integer.MAX_VALUE : MAX_EVENTS_PER_FRAME;
        int count = 0;
        Event event;
        while (count < limit && (event = queue.poll()) != null) {
            deliver(event);
            count++;
        }
        listener.onFrameEnd();
    }

    /** Modal dialogs must never run inside {@link AnimationTimer#handle}. */
    private void deliver(Event event) {
        Runnable dispatch = () -> {
            try {
                listener.onEvent(event);
            } catch (Throwable t) {
                System.err.println("event handler failed [" + event.type() + "]: " + t);
                t.printStackTrace(System.err);
            }
        };
        if (MODAL_EVENT_TYPES.contains(event.type())) {
            Platform.runLater(dispatch);
        } else {
            dispatch.run();
        }
    }

    private void drainAllPending() {
        while (hasPending()) {
            emitDeltas();
            Event event;
            while ((event = queue.poll()) != null) {
                deliver(event);
            }
            listener.onFrameEnd();
        }
        emitDeltas();
    }

    private void emitDeltas() {
        String batch;
        synchronized (deltaBuffer) {
            if (deltaBuffer.isEmpty()) {
                return;
            }
            batch = deltaBuffer.toString();
            deltaBuffer.setLength(0);
        }
        listener.onDeltaBatch(batch);
    }

    private boolean hasPending() {
        synchronized (deltaBuffer) {
            return !deltaBuffer.isEmpty() || !queue.isEmpty();
        }
    }

    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
