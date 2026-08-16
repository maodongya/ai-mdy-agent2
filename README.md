# Anvil — Codex-aligned Coding Agent (Java + Swift)

Java **Harness** + **Spring Boot App Server** (REST + SSE) + **JavaFX Workbench** + **picocli CLI**.

Design spec: [`../智能体a/`](../智能体a/)

## Requirements

- JDK 21+
- Maven 3.9+
- JavaFX（随 `anvil-ui` 模块依赖自动拉取）

## Quick start

```bash
cd ai-mdy-agent2
# 一键：后端 + JavaFX 界面（支持多窗口，⌘N 新建）
bash scripts/start-ui.sh

# 或分步启动：
bash scripts/run-server.sh          # 后端（另开终端）
bash scripts/run-ui.sh                # JavaFX 桌面界面

# 勿在根目录直接运行（会在 parent 模块失败）：
#   mvn -pl anvil-app-server -am spring-boot:run   ❌
#   mvn -pl anvil-ui -am javafx:run                ❌
```

## Project layout

```text
ai-mdy-agent2/
├── pom.xml                 # parent
├── anvil-protocol/
├── anvil-sandbox/
├── anvil-tools/
├── anvil-core/
├── anvil-app-server/
├── anvil-cli/
├── anvil-ui/               # JavaFX desktop Workbench
├── anvil-workbench/        # Legacy Swift client (deprecated)
├── fixtures/
└── config/
```

## CLI

Build and run via exec plugin, or after `mvn -pl anvil-cli -am package`:

```bash
# Protocol version
mvn -q -pl anvil-cli exec:java -Dexec.mainClass=com.anvil.cli.AnvilCli -- --protocol-version

# Start HTTP App Server (port 7788)
mvn -q -pl anvil-cli exec:java -Dexec.mainClass=com.anvil.cli.AnvilCli -- serve --port 7788

# JSON-RPC stdio mode (Codex-style, no HTTP)
mvn -q -pl anvil-cli exec:java -Dexec.mainClass=com.anvil.cli.AnvilCli -- serve --stdio

# Client commands (server must be running)
mvn -q -pl anvil-cli exec:java -Dexec.mainClass=com.anvil.cli.AnvilCli -- \
  thread create --cwd fixtures/repos/sample-lib

mvn -q -pl anvil-cli exec:java -Dexec.mainClass=com.anvil.cli.AnvilCli -- \
  run start --thread thr_1 -m "read Add.java" --mode ask --model scripted:read-add --attach

mvn -q -pl anvil-cli exec:java -Dexec.mainClass=com.anvil.cli.AnvilCli -- \
  run attach --run run_1

mvn -q -pl anvil-cli exec:java -Dexec.mainClass=com.anvil.cli.AnvilCli -- \
  approval respond --id appr_tc_write --decision allow_once
```

Global option: `--server http://127.0.0.1:7788` (default).

## OpenAI E2E

Requires `OPENAI_API_KEY`. Uses `gpt-4o-mini` by default (`ANVIL_E2E_MODEL` to override).

```bash
export OPENAI_API_KEY=sk-...
bash scripts/openai-e2e.sh

# or core only
mvn -pl anvil-core -am -Dtest=OpenAiE2ETest test
```

## DeepSeek E2E

DeepSeek uses an OpenAI-compatible API. Model id prefix: `deepseek:` (e.g. `deepseek:deepseek-chat`, `deepseek:deepseek-reasoner`).

Requires `DEEPSEEK_API_KEY`. Default model: `deepseek-chat` (`ANVIL_E2E_MODEL` to override).

Local key file (gitignored):

```bash
# .env.local — already created if you configured locally
set -a && source .env.local && set +a
bash scripts/deepseek-e2e.sh
```

```bash
export DEEPSEEK_API_KEY=sk-...
bash scripts/deepseek-e2e.sh

# CLI example
mvn -q -pl anvil-cli exec:java -Dexec.mainClass=com.anvil.cli.AnvilCli -- \
  run start --thread thr_1 -m "read Add.java" --mode ask --model deepseek:deepseek-chat --attach
```

Config (`application.yml`):

```yaml
anvil:
  deepseek:
    base-url: https://api.deepseek.com/v1
    api-key-env: DEEPSEEK_API_KEY
    model-id: deepseek-chat
```

## Golden protocol fixtures

`fixtures/protocol/*.golden.json` — event type sequences validated by `GoldenEventTest`.
