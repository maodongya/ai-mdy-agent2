# 计划：Anvil Terminal 功能（实现阶段）

## 目标
参照 Cursor 的 Terminal 体验，为 Anvil 增加集成终端面板。设计文档见 `terminal设计.md`。

## 完成进度
- [x] **M1 服务端**：`TerminalSessionManager`（会话、进程、事件缓冲）+ `TerminalController`（REST + SSE），`/v1/terminal/sessions`、`exec`、`stop`、`/events`。
- [x] **M2 UI 面板**：`TerminalPanel`（多 Tab + 命令输入 + Stop + 自动滚动）+ `TerminalClient`（HTTP+SSE）。
- [x] **M3 集成**：`SessionTerminalPanel` 接入底部布局（VBox：mainSplit + terminalPanel），`connect()` 时按服务器地址创建客户端、建立 thread_id 后自动创建首个终端 Tab；快捷键 `⌘⇧5` 聚焦命令输入框。
- [x] **M4 Agent 镜像**：`handleEvent` 捕获 `tool.completed`（shell.exec）输出镜像到终端（`mirrorShellExec`）。
- [ ] **M5 打磨**：ANSI 剥离、命令历史、主题适配、`Ctrl+` 面板折叠。

## 排查记录
- **界面未显示 Terminal 窗口原因**：`WorkbenchView.buildUi()` 中仅声明了 `terminalClient` 字段并预留 `terminalPanel`，但从未创建/组装到布局（`root.setCenter(mainSplit)` 未接入面板），且 `connect()` 未初始化 Terminal 客户端。已补上布局接入与连接初始化。
- SSE `events` 原实现：IDLE 且无新事件时即 `complete()`（连接断开），导致后续命令输出收不到。已改为会话删除才结束，IDLE 持续心跳。

## 验证
- `mvn -q -DskipTests compile` → EXIT=0。
- `mvn -q -pl anvil-ui -am test -Dtest='!DeepSeekAppServerE2ETest'` → EXIT=0。

## 下一步（M5）
1. Terminal panel 中加入 ANSI 控制序列剥离（复用 `ConsolePanel` 清洗逻辑）。
2. `Ctrl+` 快捷键展开/折叠底部终端面板。
3. 命令历史（上下方向键恢复）。
4. 主题变量应用到终端（`.console-list` 等已随主题变量生效）。
