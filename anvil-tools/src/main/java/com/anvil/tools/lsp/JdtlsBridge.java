package com.anvil.tools.lsp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Lazy jdtls subprocess bridge with index fallback (Phase 9.2). */
public final class JdtlsBridge implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<Path, JdtlsBridge> INSTANCES = new ConcurrentHashMap<>();

    private final Path workspaceRoot;
    private LspJsonRpc rpc;
    private int docVersion;
    private boolean initialized;

    private JdtlsBridge(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    public static JdtlsBridge forWorkspace(Path workspaceRoot) {
        Path key = workspaceRoot.toAbsolutePath().normalize();
        return INSTANCES.computeIfAbsent(key, JdtlsBridge::new);
    }

    public Optional<LspLocation> definition(String relativePath, int line, int column) {
        if (!relativePath.endsWith(".java")) {
            return Optional.empty();
        }
        try {
            ensureReady();
            if (rpc != null) {
                syncDocument(relativePath);
                String uri = toUri(relativePath);
                ObjectNode params = JSON.createObjectNode();
                params.put("uri", uri);
                ObjectNode pos = LspJsonRpc.position(line - 1, Math.max(0, column - 1));
                params.set("position", pos);
                JsonNode result = rpc.request(rpc.nextId(), "textDocument/definition", params, 8000);
                List<LspLocation> locs = LspJsonRpc.parseLocations(result, workspaceRoot);
                if (!locs.isEmpty()) {
                    return Optional.of(locs.getFirst());
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return SymbolNavigation.definition(workspaceRoot, relativePath, line, column);
    }

    public List<LspLocation> references(String relativePath, int line, int column) {
        if (!relativePath.endsWith(".java")) {
            return List.of();
        }
        try {
            ensureReady();
            if (rpc != null) {
                syncDocument(relativePath);
                String uri = toUri(relativePath);
                ObjectNode params = JSON.createObjectNode();
                params.put("uri", uri);
                params.set("position", LspJsonRpc.position(line - 1, Math.max(0, column - 1)));
                params.put("context", JSON.createObjectNode().put("includeDeclaration", true));
                JsonNode result = rpc.request(rpc.nextId(), "textDocument/references", params, 12000);
                List<LspLocation> locs = LspJsonRpc.parseLocations(result, workspaceRoot);
                if (!locs.isEmpty()) {
                    return locs;
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return SymbolNavigation.references(workspaceRoot, relativePath, line, column);
    }

    private void ensureReady() throws IOException {
        if (initialized) {
            return;
        }
        Process process = tryStartJdtls();
        if (process != null) {
            rpc = new LspJsonRpc(process);
            initialize();
        }
        initialized = true;
    }

    private void initialize() throws IOException {
        ObjectNode init = JSON.createObjectNode();
        ObjectNode caps = JSON.createObjectNode();
        ObjectNode text = JSON.createObjectNode();
        ObjectNode sync = JSON.createObjectNode();
        sync.put("dynamicRegistration", false);
        sync.put("openClose", true);
        sync.put("change", 1);
        text.set("synchronization", sync);
        caps.set("textDocument", text);
        init.set("capabilities", caps);
        ObjectNode root = JSON.createObjectNode();
        root.put("uri", toUri("."));
        root.put("name", workspaceRoot.getFileName().toString());
        init.set("rootUri", root.get("uri"));
        init.set("rootPath", JSON.valueToTree(workspaceRoot.toString()));
        init.put("processId", (int) ProcessHandle.current().pid());
        rpc.request(rpc.nextId(), "initialize", init, 15000);
        rpc.notify("initialized", JSON.createObjectNode());
    }

    private void syncDocument(String relativePath) throws IOException {
        Path abs = workspaceRoot.resolve(relativePath).normalize();
        if (!Files.isRegularFile(abs)) {
            return;
        }
        String uri = toUri(relativePath);
        String text = Files.readString(abs);
        docVersion++;
        ObjectNode params = JSON.createObjectNode();
        params.set("textDocument", LspJsonRpc.textDocumentItem(uri, "java", docVersion, text));
        rpc.notify("textDocument/didOpen", params);
    }

    private Process tryStartJdtls() {
        String home = System.getenv("JDTLS_HOME");
        if (home == null || home.isBlank()) {
            return null;
        }
        try {
            Path launcherDir = Path.of(home, "plugins");
            if (!Files.isDirectory(launcherDir)) {
                return null;
            }
            Path launcher = Files.list(launcherDir)
                    .filter(p -> p.getFileName().toString().startsWith("org.eclipse.equinox.launcher_"))
                    .findFirst()
                    .orElse(null);
            if (launcher == null) {
                return null;
            }
            Path config = Path.of(home, "config_mac");
            if (!Files.isDirectory(config)) {
                config = Path.of(home, "config_linux");
            }
            if (!Files.isDirectory(config)) {
                config = Path.of(home, "config_win");
            }
            Path dataDir = Files.createTempDirectory("anvil-jdtls-");
            ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    "-Declipse.application=org.eclipse.jdt.ls.core.id1",
                    "-Dosgi.bundles.defaultStartLevel=4",
                    "-Declipse.product=org.eclipse.jdt.ls.core.product",
                    "-noverify",
                    "-Xmx512M",
                    "-jar",
                    launcher.toString(),
                    "-configuration",
                    config.toString(),
                    "-data",
                    dataDir.toString());
            pb.redirectErrorStream(true);
            return pb.start();
        } catch (Exception e) {
            return null;
        }
    }

    private String toUri(String relativePath) {
        Path abs = "."
                .equals(relativePath)
                ? workspaceRoot
                : workspaceRoot.resolve(relativePath).normalize();
        return abs.toUri().toString();
    }

    @Override
    public void close() {
        if (rpc != null) {
            rpc.close();
            rpc = null;
        }
        initialized = false;
        INSTANCES.remove(workspaceRoot);
    }
}
