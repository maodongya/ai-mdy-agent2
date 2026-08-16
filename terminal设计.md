# Anvil Terminal 功能设计

参照 Cursor 的 Terminal 交互体验，为 Anvil 增加一个集成的终端（Terminal）面板，用于在 UI 中执行 shell 命令、查看输出、多标签并发工作，并与智能体（agent）共享工作区。

---

## 1. 目标与范围

### 目标
1. 在 Anvil UI 底部提供可展开/折叠的 **Terminal 面板**。
2. 支持 **多终端标签页**（Tab），每个标签是一个独立的 shell 会话。
3. 支持 **命令编辑框**：输入命令并回车执行，输出实时回显（ANSII 颜色/ANSI 控制码剥离为纯文本）。
4. 支持 **终止运行中的命令**（Ctrl+C / Stop）。
5. 终端工作目录为当前线程的 **workspace**。
6. 命令执行**不阻塞 UI**，输出按行流式追加。
7. 与智能体交互：agent 的 `shell.exec` 工具输出可在终端面板中**镜像/回显**，便于人类观察。

### 非目标（v1）
- 不实现 pseudo-TTY（交互式 TUI 如 `vim`、`htop`），命令以 **非交互式** 方式运行。
- 不做远程/SSH 终端。
- 不做终端配色自定义（仅暗色/浅色随主题切换）。

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Anvil UI (JavaFX)                     │
│  ┌────────────┐  ┌────────────────────────────────────────┐ │
│  │ Explorer   │  │  Center Tabs                           │ │
│  │            │  │   ┌────────┐  ┌──────────┐  ┌───────┐  │ │
│  │            │  │   │  Code  │  │Review Diff│  │ Terminal│ │
│  └────────────┘  └────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
         │                      │
         │  HTTP/REST           │  SSE / WebSocket
         ▼                      ▼
┌─────────────────────────  App Server (Spring Boot)  ─────────────────────────┐
│  TerminalController (/v1/terminal)     TerminalSessionManager                │
│   ├─ POST /sessions    新建会话       ├─ map id→SessionRecord                │
│   ├─ POST /sessions/{id}/exec 执行命令 ├─ ProcessBuilder 包装                │
│   ├─ POST /sessions/{id}/stop 终止     ├─ 文件描述符输出捕获 → 事件流          │
│   └─ GET  /sessions/{id}/events       └─ 合法命令白名单（可选）               │
│                                                                              │
│  Agent shell.exec → 事件流（tool.completed）→ Terminal 镜像                    │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 协议/API 设计

### 3.1 新建终端会话

```
POST /v1/terminal/sessions
Body: { "thread_id": "thr_x", "title": "bash" }
Resp: { "session_id": "term_1", "cwd": "/path/to/workspace", "status": "READY" }
```

- `thread_id` 决定终端的工作目录（该线程的 workspace root）。
- 一个线程可开多个终端标签。

### 3.2 执行命令

```
POST /v1/terminal/sessions/{id}/exec
Body: { "command": "mvn clean test", "timeout_ms": 300000 }
Resp: { "session_id": "term_1", "accepted": true, "job_id": "job_9" }
```

- 命令在 `cwd`（workspace）下通过非交互式 shell 执行。
- 命令执行期间该终端为 `RUNNING` 状态；重复 exec 会排队或报错（v1 队列长度=1）。

### 3.3 终止命令

```
POST /v1/terminal/sessions/{id}/stop
Resp: { "session_id": "term_1", "status": "IDLE" }
```

- 强制终止当前运行中的进程组（`destroyForcibly`）。

### 3.4 事件流（SSE）

```
GET /v1/terminal/sessions/{id}/events?from_seq=0   （text/event-stream）
```

事件类型（参考现有 `Event` 结构扩展一个 `TerminalEvent`）：

| type | payload |
|------|---------|
| `terminal.output`  | `{ seq, session_id, lines: ["..."] }` |
| `terminal.job_start` | `{ seq, session_id, command }` |
| `terminal.job_done`  | `{ seq, session_id, exit_code, duration_ms }` |
| `terminal.error`     | `{ seq, session_id, message }` |
| `terminal.status`    | `{ seq, session_id, status: "IDLE"/"RUNNING" }` |

- 每个命令的输出按块（每 50ms 或每 200 行）作为一个 `terminal.output` 事件推送。
- 终端会保留最近 N 行（缓存到服务端内存，便于恢复）。

### 3.5 Agent 镜像（可选，v1 简化）

agent 的 `shell.exec` 工具输出（已在 `run` 事件流中以 `tool.completed` 呈现）会由 UI 端直接 **自动追加** 到默认终端标签，无需额外服务端 API。

---

## 4. 服务端组件设计

### 4.1 `TerminalSessionManager`（App Server / anvil-app-server）

- 持有 `ConcurrentHashMap<String, TerminalSession>`。
- `create(threadId)`：解析 workspace root，创建 `TerminalSession`。
- `exec(sessionId, command, timeout)`：提交到虚拟线程池执行。
- `stop(sessionId)`：终止进程。
- `events(sessionId, fromSeq, consumer)`：长轮询/SSE 推送 `TerminalEvent`。

### 4.2 `TerminalSession`

```java
class TerminalSession {
    String id;
    Path cwd;
    String title;
    volatile Status status;        // IDLE / RUNNING
    Process process;               // 当前运行进程（null 表示 IDLE）
    Long currentJobId;
    Deque<TerminalEvent> eventLog; // 内存环形缓冲（最多 2000 条）
    int seq;                       // 自增序号
}
```

- 复用 `ProcessBuilder("/bin/sh", "-c", command)`（同 `anvil-tools/ShellTool`）。
- 捕获 stdout+stderr（`redirectErrorStream(true)`），后台线程按行读入并生成 `terminal.output`。
- 记录 exit code / 时长。

### 4.3 `TerminalController`（api 层）

- `@RestController @RequestMapping("/v1/terminal")`。
- 依赖注入 `TerminalSessionManager`、`RunService`（取 thread workspace root）。
- SSE 事件流复用现有 `SseEmitter`/响应流模式（参考 `RunController` 的 events）。

### 4.4 安全与限制

- 沿用服务端 `SandboxTier`：会话仅允许在 workspace 内运行（`PathGuard` 校验 cwd，若命令含绝对路径越界则拒绝）。
- 输出长度截断：单命令最大输出 256 KB（复用 `ShellTool.MAX_OUTPUT_CHARS`）。
- 超时：默认继承 `anvil.sandbox.shell-timeout-ms`（默认 30000ms），可被命令显式覆盖。
- 并发限制：每个线程同时最多 3 个终端会话（可配置）。

---

## 5. UI / JavaFX 组件设计（anvil-ui）

### 5.1 `TerminalPanel`（底部，`SplitPane` 底部区域）

- 顶部 Tab 栏：每个终端一个 Tab（`+` 新建、每个 Tab 可关闭）。
- 每个 Tab 内容：
  - **输出区**：只读 `ListView<String>`（自动滚动到底），流式行追加。
  - **命令输入行**：`TextField` + 回车执行 + `⚠停止` 按钮（当前 RUNNING 时显示）。
- 底部横条：状态（IDLE/RUNNING）、当前目录、最近 exit code。
- 提供 `Ctrl+` 快捷键展开/折叠面板。

### 5.2 `TerminalClient`（桥接 AnvilClient）

```java
class TerminalClient {
    String createSession(threadId, title);
    void exec(sessionId, command);
    void stop(sessionId);
    void stream(sessionId, fromSeq, consumer); // 复用 SSE 解析
    void mirrorShellExec(String commandOutput); // agent 输出镜像
}
```

### 5.3 键盘快捷键（WorkbenchView）

| 快捷键 | 动作 |
|--------|------|
| `Ctrl+`  | 展开/折叠 Terminal 面板 |
| `Ctrl+Shift+5` | 聚焦命令输入框 |
| `Ctrl+Tab` | 切换终端 Tab |

### 5.4 ANSI 处理

- 服务端输出含 ANSI 颜色码，UI 端在写入前剥离控制序列（正则 `\x1b\[[0-9;]*m` 等），v1 纯文本显示。

---

## 6. 数据流示例

1. 用户点 `+` 新建终端 → `TerminalClient.createSession(threadId)`。
2. 输入 `mvn clean test` → `exec()` → 返回 `job_id` → 面板变 RUNNING。
3. SSE `terminal.output` 到达 → `ListView` 追加行 → 自动滚动。
4. `terminal.job_done` 到达 → 恢复 IDLE → 显示 exit code。
5. 点击 Stop → `stop()` → 服务端破坏进程 → 面板打上“已终止”。

agent 运行时：
1. agent 调用 `shell.exec` → 事件流 `tool.completed`。
2. UI 捕获该事件 → 自动追加到默认终端 tab（或以 `⚙ <command>` 前缀行显示在 Terminal 面板）。

---

## 7. 配置项（application.yml 扩展）

```yaml
anvil:
  terminal:
    enabled: true
    max-sessions-per-thread: 3
    max-output-lines: 2000        # 每个会话内存保留行数
    job-timeout-ms: 300000        # 单命令最大时长
    mirror-agent-shell: true      # 是否把 agent shell.exec 输出镜像到终端
```

---

## 8. 里程碑拆解

| 里程碑 | 内容 | 验收 |
|--------|------|------|
| **M1 服务端** | `TerminalSessionManager` + `TerminalController` + SSE | `curl` 新建会话、执行 `echo hi`、收到 `terminal.output` 与 `job_done` |
| **M2 UI 面板** | `TerminalPanel` + `TerminalClient` + 多 Tab + 命令输入 + Stop | 手动执行 `ls`、`mvn test`、超时长命令并 Stop |
| **M3 集成** | 面板加入主布局（底部 SplitPane）、`Ctrl+` 快捷键、快捷键聚焦输入框 | 不阻塞 UI，输出流式显示 |
| **M4 Agent 镜像** | 捕获 `tool.completed`（shell.exec）输出追加到终端 | agent 执行 `shell.exec` 时终端可见输出 |
| **M5 打磨** | ANSI 剥离、状态显示、exit code、主题适配（暗/浅/Monokai）、持久化最近命令历史 | 体验稳定，接近 Cursor |

---

## 9. 风险与备注

- **进程生命周期**：命令可能生成子进程，`destroyForcibly` 不保证杀死子孙进程；v1 接受此限制，文档注明。
- **非交互限制**：不支持需要 TTY 的程序（`vim`、`top`）。若后续需要，引入 `org.fusesource.jansi` / ANSI 终端转义环境变量或 pty 库。
- **并发输出安全**：SSE 推送与 exec 并发写事件环，用 `synchronized`/`Queue` 保证顺序。
- **路径安全**：沿用 `PathGuard`，拒绝越界 cwd 或含 `..` 逃逸的命令，纳入沙箱策略。
- **内存**：默认事件环保留 2000 行，避免长期运行 OOM。

---

## 10. 与现有模块的复用

| 现有代码 | 复用点 |
|----------|--------|
| `anvil-tools/ShellTool` | 命令构造、超时、输出截断逻辑（提取为共享静态工具） |
| `anvil-core/tools/PathGuard` | cwd/路径越界校验 |
| `anvil-app-server/api/*` | Controller 风格、SSE/SseEmitter 模式（参考 `RunController`） |
| `anvil-ui/client/AnvilClient` | HTTP 封装 + SSE 解析（`streamEvents`） |
| `anvil-ui/ConsolePanel` | 行流式追加、自动滚动、暗色样式参考 |
| `anvil-protocol/Event` | 扩展/新增 `TerminalEvent`（保持向后兼容） |

---

*协议版本：Anvil Protocol v1.0*
