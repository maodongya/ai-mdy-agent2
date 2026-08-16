package com.anvil.ui.client;

import com.anvil.protocol.Event;
import com.anvil.protocol.ProtocolJson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** HTTP + SSE client for Anvil App Server. */
public final class AnvilClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

    private final URI baseUrl;
    private final HttpClient http;

    public AnvilClient(String baseUrl) {
        this.baseUrl = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Map<String, Object> health() throws Exception {
        return getMap("/api/health");
    }

    public Map<String, Object> createThread(String cwd) throws Exception {
        return postMap("/v1/threads", Map.of("cwd", cwd));
    }

    public Map<String, Object> startRun(
            String threadId, String mode, String model, String message, String profile, List<String> openFiles, String focusFile)
            throws Exception {
        return startRun(threadId, mode, model, message, profile, openFiles, focusFile, null, null, null);
    }

    public Map<String, Object> startRun(
            String threadId,
            String mode,
            String model,
            String message,
            String profile,
            List<String> openFiles,
            String focusFile,
            Integer selectionStartLine,
            Integer selectionEndLine,
            String selectionText,
            boolean autoApproveWrites)
            throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("mode", mode);
        body.put("model", model);
        body.put("message", message);
        if (profile != null && !profile.isBlank()) {
            body.put("profile", profile);
        }
        if (openFiles != null && !openFiles.isEmpty()) {
            body.put("openFiles", openFiles);
        }
        if (focusFile != null && !focusFile.isBlank()) {
            body.put("focusFile", focusFile);
        }
        if (selectionText != null && !selectionText.isBlank()) {
            body.put("selectionText", selectionText);
            if (selectionStartLine != null) {
                body.put("selectionStartLine", selectionStartLine);
            }
            if (selectionEndLine != null) {
                body.put("selectionEndLine", selectionEndLine);
            }
        }
        if (autoApproveWrites) {
            body.put("autoApproveWrites", true);
        }
        return postMap("/v1/threads/" + enc(threadId) + "/runs", body);
    }

    public Map<String, Object> startRun(
            String threadId,
            String mode,
            String model,
            String message,
            String profile,
            List<String> openFiles,
            String focusFile,
            Integer selectionStartLine,
            Integer selectionEndLine,
            String selectionText)
            throws Exception {
        return startRun(
                threadId,
                mode,
                model,
                message,
                profile,
                openFiles,
                focusFile,
                selectionStartLine,
                selectionEndLine,
                selectionText,
                false);
    }

    public Map<String, Object> startRun(String threadId, String mode, String model, String message, String profile)
            throws Exception {
        return startRun(threadId, mode, model, message, profile, List.of(), null);
    }

    public Map<String, Object> startRun(String threadId, String mode, String model, String message) throws Exception {
        return startRun(threadId, mode, model, message, null);
    }

    public void respondApproval(String approvalId, String decision) throws Exception {
        postMap("/v1/approvals/" + enc(approvalId) + "/respond", Map.of("decision", decision));
    }

    public void cancelRun(String runId) throws Exception {
        postMap("/v1/runs/" + enc(runId) + "/cancel", Map.of());
    }

    public Map<String, Object> workspaceTree(String threadId) throws Exception {
        return getMap("/v1/workspace/tree?thread_id=" + encQuery(threadId));
    }

    public Map<String, Object> workspaceFile(String threadId, String path) throws Exception {
        return getMap("/v1/workspace/file?thread_id=" + encQuery(threadId) + "&path=" + encQuery(path));
    }

    public Thread streamEvents(String runId, int fromSeq, Consumer<Event> onEvent, Runnable onComplete) {
        Thread t = new Thread(
                () -> {
                    try {
                        String url = baseUrl + "/v1/runs/" + enc(runId) + "/events?from_seq=" + fromSeq;
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("Accept", "text/event-stream")
                                .timeout(REQUEST_TIMEOUT)
                                .GET()
                                .build();
                        HttpResponse<java.io.InputStream> response =
                                http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                        if (response.statusCode() >= 400) {
                            throw new IllegalStateException("SSE HTTP " + response.statusCode());
                        }
                        parseSse(response.body(), onEvent);
                        onComplete.run();
                    } catch (Exception e) {
                        if (Thread.currentThread().isInterrupted()) {
                            onEvent.accept(cancelledEvent(runId));
                        } else {
                            onEvent.accept(errorEvent(runId, e.getMessage()));
                        }
                        onComplete.run();
                    }
                },
                "anvil-sse-" + runId);
        t.setDaemon(true);
        t.start();
        return t;
    }

    static void parseSse(java.io.InputStream input, Consumer<Event> onEvent) throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            while (!Thread.currentThread().isInterrupted()) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                Event event = ProtocolJson.fromJson(data, Event.class);
                onEvent.accept(event);
                if (isTerminal(event.type())) {
                    return;
                }
            }
        }
    }

    private static boolean isTerminal(String type) {
        return "run.completed".equals(type) || "run.failed".equals(type) || "run.cancelled".equals(type);
    }

    private static Event cancelledEvent(String runId) {
        return new Event(
                "1.0",
                "thr_ui",
                runId,
                0,
                "run.cancelled",
                java.time.Instant.now().toString(),
                Map.of("reason", "interrupted"));
    }

    private static Event errorEvent(String runId, String message) {
        return new Event(
                "1.0",
                "thr_ui",
                runId,
                0,
                "run.failed",
                java.time.Instant.now().toString(),
                Map.of("error", Map.of("message", message)));
    }

    private Map<String, Object> getMap(String path) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder().uri(baseUrl.resolve(path)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return ProtocolJson.mapFromJson(response.body());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postMap(String path, Map<String, ?> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(baseUrl.resolve(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(ProtocolJson.toJson(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return ProtocolJson.mapFromJson(response.body());
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> nodes(Map<String, Object> treeResponse) {
        Object nodes = treeResponse.get("nodes");
        if (nodes instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }
}
