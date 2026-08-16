# Anvil Workbench (Swift)

macOS SwiftUI client for the Anvil App Server. **Thin surface only** — no local tool execution.

## Requirements

- macOS 14+
- Xcode 15+ / Swift 5.9
- Anvil App Server running at `http://127.0.0.1:7788`

## Quick start

```bash
# Terminal 1 — Java App Server
cd ../
mvn -pl anvil-app-server -am spring-boot:run

# Terminal 2 — Workbench
open AnvilWorkbench.xcodeproj
# Run (⌘R), set Workspace path to absolute path of fixtures/repos/sample-lib
```

## Layout

| Area | View | API |
|------|------|-----|
| Sidebar | `FileTreeView` | `GET /v1/workspace/tree` |
| Editor | `EditorView` | `GET /v1/workspace/file` (read-only) |
| Console | `AgentConsoleView` | SSE `GET /v1/runs/{id}/events` |
| Approval | `ApprovalSheet` | `POST /v1/approvals/{id}/respond` |

## Visual spec

- Background `#12141a` (graphite)
- Accent `#3D9CF0` (steel blue)
- Monospaced typography via `SF Mono` system design
- Dark mode enforced; no system purple tint

## E2E: write with approval

1. Set workspace to `fixtures/repos/sample-lib` (absolute path)
2. Mode **Agent**, model `scripted:write-add`
3. Prompt: `write demo file`
4. Approve **Allow Once** in sheet
5. Editor refreshes `AnvilDemo.txt`

## Tests

```bash
xcodebuild -scheme AnvilWorkbench -destination 'platform=macOS' test
```

Unit tests decode protocol `Event` JSON aligned with Java `anvil-protocol`.

## Task progress

- [x] S0 Xcode scaffold
- [x] S1 AnvilClient + SSE
- [x] S2 AnvilTheme
- [x] S3 WorkbenchView layout
- [x] S4 FileTree + Editor
- [x] S5 AgentConsole + Run
- [x] S6 ApprovalSheet
- [x] S7 Settings + health check
