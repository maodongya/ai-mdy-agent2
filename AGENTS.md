# Anvil Agent — this repo

Codex-aligned coding agent harness (Java). Swift Workbench lives in `anvil-workbench/`.

- **Validate:** `mvn -q validate`
- **Test:** `mvn clean test`（大改后务必 clean，避免 stale class）
- **Verify:** `bash scripts/verify.sh`
- **Run server:** `bash scripts/run-server.sh`（勿用根目录 `spring-boot:run`，会在 parent 上报错）
- **Run server + UI:** `bash scripts/start-ui.sh`
- **UI only:** `bash scripts/run-ui.sh`
- **Health:** `GET http://127.0.0.1:7788/api/health`
- **Sample workspace:** `fixtures/repos/sample-lib`
- **Protocol:** v1.0 (see `智能体a/02-产品规格/02-核心API与协议.md`)

## Modules

| Module | Role |
|--------|------|
| anvil-protocol | DTOs, events, error codes |
| anvil-sandbox | Path guard, process sandbox |
| anvil-tools | fs, shell, plan |
| anvil-core | Harness loop (no Spring) |
| anvil-app-server | Spring Boot HTTP+SSE |
| anvil-ui | JavaFX desktop Workbench |
| anvil-cli | picocli client |

## Task progress

- [x] J0 Maven scaffold
- [x] J1 protocol models (Event, Run, Thread, ErrorCodes, Usage, ToolResult)
- [x] J2 PathGuard
- [x] J3 FsTools + ShellTool (basic)
- [x] J4 PolicyEngine
- [x] J5 PromptBuilder + InstructionLoader
- [x] J6 LoopEngine + ScriptedModel
- [x] J7 PlanTool
- [x] J8 App Server REST + SSE (in-memory store, scripted runs)
- [x] J9 JSON-RPC stdio
- [x] J10 picocli CLI
- [x] S0–S7 Swift Workbench (deprecated — replaced by Java Web UI)
- [x] JavaFX Workbench (`anvil-ui` — thin client via REST+SSE)
- [x] J11 OpenAI ModelProvider
- [x] DeepSeek model provider (`deepseek:` prefix, OpenAI-compatible API)
- [x] J12 ContextCompactor + McpBridge
- [x] J13 Orchestrator + golden fixtures + CI
