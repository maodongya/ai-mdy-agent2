# Anvil vs Cursor：Token 经济性设计与提升计划 3.0

> 承接 [`对比cursor进行提升2.md`](./对比cursor进行提升2.md)（Phase 6～10 已落地或进行中）。  
> 本文回答：**为什么 Anvil 同样任务比 Cursor 更耗 token？** 以及 **如何在不大损成功率的前提下，把 token 成本压到 Cursor 同级或更低**。

---

## 1. 结论（一句话）

Cursor 的 token 效率 = **稳定可缓存前缀 × 小步工具输出 × 语义检索减 read × 模型路由 × 激进 compaction**。  
Anvil Phase 10 后能力对齐了，但 **每步重复注入大段 Prompt、Explore 双 Loop、工具结果偏长、无 read 去重、extended/complex 预算过大**，导致 **同等任务 input token 常为 Cursor 的 1.5～3 倍**。

---

## 2. Token 成本公式（对照）

```text
Run 总 token ≈
  Σ_step ( system + tools_schema + developer_prefix + history + tool_results )
  + output_tokens
  − cached_prompt_tokens   ← Cursor 优化重点，Anvil 几乎未用
```

| 因子 | Cursor 典型做法 | Anvil 现状 | 对 token 影响 |
|------|-----------------|------------|---------------|
| **Prompt 前缀** | 稳定 system/tools，利于 provider cache | 每步 `PromptBuilder` 拼 5～7 条 developer 块 | ★★★★★ |
| **工具 schema** | 按 Mode 裁剪 + 稳定排序 | `ToolCatalog.builtinSchemas` 全量每步发送 | ★★★★☆ |
| **检索 vs 通读** | `@codebase` 召回片段 | 多步 grep + 整段 `fs.read` | ★★★★★ |
| **Explore** | 后台/独立预算，摘要极短 | `ExploreAgent` 6 步 + 报告注入主 history | ★★★★☆ |
| **Compaction** | 较早触发、摘要结构化 | threshold 120k～280k，偏晚 | ★★★★☆ |
| **Tool 输出上限** | 按工具类型分级截断 | 统一 `maxToolContentChars` 8k～24k | ★★★☆☆ |
| **模型路由** | 探索 mini / 编辑 strong | routing 已接但 explore/edit/plan 同 chat | ★★★☆☆ |
| **跨 Run 记忆** | 项目索引 + 短摘要 | SQLite thread memory 回放全 history | ★★★☆☆ |
| **Verify 回灌** | 结构化错误片段 | Maven 输出 tail 可达数千字符 | ★★★☆☆ |

---

## 3. Anvil 当前 Token 热点（代码级）

### 3.1 每步重复注入 Developer 前缀

`PromptBuilder.build()` 在 **每个 Loop 步** 被调用，且每次向 `input` 追加：

- `AGENTS.md` 全文（`InstructionLoader`）
- `<environment>`、`<mode_instructions>`、`<anti_patterns>`、`<tool_examples>`、`<tool_guidance>`

见 `anvil-core/.../PromptBuilder.java` 与 `LoopEngine` 每步 `build_prompt`。

**问题**：这些内容步间不变，却与 **变化的 history** 混在同一请求体；DeepSeek/OpenAI 的 **prompt cache** 难以命中（前缀不稳定或重复计费）。

**Cursor 对标**：Codex 风格 **instructions + tools 固定**，仅 **input 尾部** 追加新消息（利于 cache）。

---

### 3.2 Explore 子 Agent = 隐性双倍计费

`application.yml` 默认：

```yaml
loop:
  explore-sub-agent: true
  explore-max-steps: 6
```

`ExploreAgent.run()` 在 **主 Loop 之前** 最多 6 次模型调用，每步：

- 完整 `PromptBuilder.build()` + READ 工具 schema
- 工具结果 truncate 至 **4000 字符** 仍写入 explore 内部 history
- 最终 `<explore_report>` **整段注入主 Agent history**（`LoopEngine.seedHistory` 前）

**问题**：简单单文件 bugfix 也会多 6×(prefix + tools + 若干 read) 的 input token。

---

### 3.3 Context 预算偏大、Compaction 偏晚

| Profile | compact 阈值 | keep recent | tool 内容上限 |
|---------|-------------|-------------|---------------|
| standard | 120k | 12 | 8k |
| extended | 200k | 24 | 16k |
| complex | 280k | 32 | 24k |

Agent 默认 **extended**（100 步）。阈值 200k 才 compact，意味着 **长 Run 前段大量 tool 原文** 长期留在 history。

Token 估算用 `chars/4`（`ContextCompactor.estimateTokens`），**无 tiktoken**，易低估 → 实际更易触顶 provider 限制。

---

### 3.4 工具结果与重复 Read

- `fs.read` 无 **内容 hash 去重**：同一文件多步读取，history 中重复大段代码。
- `grep` / `codebase.search` 返回多行命中，常 **超过任务所需** 仍全量进 history。
- `VerifyPass` / `DiagnosticsPass` 失败时 **inject 完整 tool 消息**（`maxToolContentChars` 上限内尽量满）。

---

### 3.5 Editor / Harness 上下文膨胀

`RunRequest.formatHarnessContext()` 每 Run 注入：

- 全部 `open_files` 列表
- `selection` 全文
- 每个 dirty buffer 最多 **8000 字符**（`truncateBuffer`）
- `MavenModuleGraph` + `@` 引用路径

Workbench 多 Tab 打开时，**单次 Run 前缀可达数万字符**。

---

### 3.6 Thread Memory 跨 Run 回放

`ThreadMemoryStore` 持久化上轮 **完整 messages**（经 compaction 预算裁剪后仍可能很大），下轮 Run **全量 replay** 到 `seedHistory`。

Cursor 更倾向：**索引/摘要 + 当前任务焦点**，而非聊天全文续写。

---

### 3.7 模型与路由

`application.yml`：

```yaml
routing:
  explore: deepseek:deepseek-chat
  edit: deepseek:deepseek-chat
  plan: deepseek:deepseek-chat
```

Explore、Plan、主 Loop **同价模型**；无「cheap read-only / expensive write」分层。  
`cached_tokens` 已在 `ModelUsage` 上报，但 **无产品策略** 去最大化 cache 命中。

---

### 3.8 Phase 10 能力 vs Token 权衡

| 能力 | 价值 | Token 代价 |
|------|------|------------|
| Explore 子 Agent | 理解力 ↑ | 固定 +6 步前缀成本 |
| Prompt 2.0 长指令 | 成功率 ↑ | 每步 +1～2k tokens |
| Planner COMPLEX | 大任务 ↑ | plan.update + 多步 developer 块 |
| 并行 read | 延迟 ↓ | 同一步多个大 tool result 同时进 history |
| MCP 工具 schema | 扩展性 ↑ | tools 列表变长 |

**结论**：Phase 10 提升质量的同时 **系统性抬高 token**；需 Phase 11 专门做 **经济性** 对冲。

---

## 4. Cursor 的 Token 策略（可借鉴）

1. **Stable prefix + cache-friendly layout**  
   system / tools 不变，对话与 tool 结果只在尾部增长。

2. **Retrieval-first**  
   `@codebase` 返回 **相关片段**，减少整文件 read 次数与体积。

3. **Small tool envelopes**  
   大输出写磁盘/artifact，context 只留 path + 摘要 + 行号指针。

4. **Mode-aware tool sets**  
   Ask 模式不带 write/exec schema，减少 tools JSON 体积。

5. **Background explore**  
   探索与主 Agent **预算隔离**；给主 Agent 的是 **短清单** 而非探索全过程 history。

6. **Model routing**  
   分类/搜索用轻模型，编辑/推理用强模型（单价与 context 窗口不同）。

7. **Aggressive summarization**  
   旧 turn 压成结构化摘要（改了哪些文件、关键结论），非原文堆叠。

---

## 5. 设计原则（Anvil Token 1.0）

1. **Cache-first assembly**：稳定块在前、变化块在后；步间字节级一致。  
2. **Pay for precision**：默认检索片段，按需 expand read。  
3. **One copy rule**：同文件同 hash 内容在 history 只保留一份引用。  
4. **Budget isolation**：Explore / Plan / Main 各自 token 上限，超则摘要不扩写。  
5. **Measure before optimize**：每 Run 上报 `input/output/cached/step_avg`；Regression 看 **token/成功任务**。  
6. **Profile 默认 lean**：dev 默认 standard + explore off；extended 需显式选。  
7. **Quality floor**：压缩/截断不得破坏 tool_call 配对（已有 `MessageHistorySanitizer` 继续遵守）。

---

## 6. Phase 11：Token 经济性（建议 4～6 周）

> 原则：**先减重复与前缀，再减工具体积，最后调 profile 默认值**。每项可 Trace / metrics 验收。

### 11.1 Prompt 分层与 Cache 友好装配（P0，1 周）

| ID | 任务 | 说明 | 验收 |
|----|------|------|------|
| 11.1.1 | **StablePrefix 拆分** | `PromptBundle` 拆为 `stableInstructions` + `stableTools` + `volatileInput`；provider 请求体按此顺序组装 | 连续两步 stable 段 SHA256 一致 |
| 11.1.2 | **Developer 块步内去重** | `anti_patterns` / `tool_examples` / `mode_instructions` 仅 **Run 首步** 或 **compact 后首步** 注入；后续步省略 | 第 2 步起 input 字符 −30% |
| 11.1.3 | **AGENTS.md 摘要层** | 超过 2k 字符时 Run 启动生成 `<agents_summary>` 缓存；全文按需 `@` 引用 | AGENTS 超 10k 时首步 prefix ≤3k |
| 11.1.4 | **Cache 指标面板** | UI/Trace 展示 `cached_tokens / input_tokens` 比率 | 每 Run 可见 cache hit % |

**涉及模块**：`PromptBuilder.java`、`OpenAiModelProvider.java`、`AgentEventFormatter.java`

---

### 11.2 工具 Schema 与输出预算（P0，1 周）

| ID | 任务 | 说明 | 验收 |
|----|------|------|------|
| 11.2.1 | **Mode 裁剪 tools** | Ask：无 write/exec；Plan：无 exec；Agent：全量 | Ask mode tools JSON −40% |
| 11.2.2 | **分级 tool 截断** | read/search：2k；grep：4k；verify/diagnostics：1k 摘要 + artifact_ref | 单 tool 消息超 4k 占比 <5% |
| 11.2.3 | **Artifact 外置** | 超大 stdout/文件内容写 `.anvil/artifacts/{run}/{id}.txt`；history 只留 ref | 10k+ 字符不出现在 messages |
| 11.2.4 | **Structured verify 摘要** | `DiagnosticParser` 只 inject ERROR 行 + 文件路径，非 raw tail | verify 失败 inject <800 chars |

**涉及模块**：`ToolCatalog.java`、`ContextCompactor.java`、`VerifyPass.java`、`ProcessRunner` 输出路径

---

### 11.3 Read 去重与检索优先（P1，1～2 周）

| ID | 任务 | 说明 | 验收 |
|----|------|------|------|
| 11.3.1 | **ReadCache（Run 级）** | `fs.read` 成功结果按 `(path, offset, limit, mtime)` 缓存；history 重复 read 改为 `content_ref` | 同文件 3 次 read，history 仅 1 份正文 |
| 11.3.2 | **codebase.search 默认 snippet** | 返回 top-k **行区间** 而非整段文件；prompt 引导「先 search 再 read 区间」 | 探索步平均 read 体积 −50% |
| 11.3.3 | **@引用懒加载** | `@path` 默认只注入 path + 首 40 行；模型 `fs.read` 按需展开 | `@大文件` 不再一次性 8k buffer |

**涉及模块**：`ToolExecutor.java`、`CodebaseSearchTool.java`、`RunRequest.formatHarnessContext`

---

### 11.4 Explore / Planner 预算隔离（P1，1 周）

| ID | 任务 | 说明 | 验收 |
|----|------|------|------|
| 11.4.1 | **Explore 按需启动** | 仅 COMPLEX profile 或 userMessage 含多文件/重构关键词时启用；否则 `explore-sub-agent: false` 默认 | 单文件 patch Run 无 explore.* 事件 |
| 11.4.2 | **Explore token 上限** | 独立 `explore-max-tokens`（如 8k output 等价）；超限返回 **文件清单 only** | explore 报告 ≤120 行 |
| 11.4.3 | **Explore 内部 compact** | 子 Agent 每 2 步 compact 一次，不积累长 history | explore 6 步 input 不线性增长 |
| 11.4.4 | **Plan 不重复注入** | `plan.update` 成功后，developer plan 块替换为 **≤500 字摘要** | plan 文件 200 行时 history 增量 ≤500 字 |

**配置建议**：

```yaml
anvil:
  loop:
    explore-sub-agent: false          # 默认关，COMPLEX 或显式开启
    explore-max-steps: 4              # 6 → 4
    explore-max-tokens-budget: 8000
  context:
    default-profile: standard         # Agent 默认 standard 非 extended
```

---

### 11.5 Compaction 与 Thread Memory（P1，1 周）

| ID | 任务 | 说明 | 验收 |
|----|------|------|------|
| 11.5.1 | **更早 compact** | standard 阈值 60k→40k；extended 100k→60k | 20 步 Run 触发 ≥1 次 compact |
| 11.5.2 | **结构化摘要** | `summarizeDropped` 输出：`files_read[]`、`files_changed[]`、`failures[]`、bullet 结论 | 摘要可解析，非自由文本堆砌 |
| 11.5.3 | **Thread memory 摘要层** | 跨 Run 只存 **摘要 + 最近 6 条** + anchor 文件列表 | 新 Run 不再 replay 50+ 条旧消息 |
| 11.5.4 | **tiktoken 估算（可选）** | jtokkit 估算 cl100k；超阈值提前 compact | 估算误差 <15% vs 账单 |

**涉及模块**：`ContextCompactor.java`、`ContextBudget.java`、`ThreadMemoryStore.java`

---

### 11.6 模型路由与步级策略（P2，1 周）

| ID | 任务 | 说明 | 验收 |
|----|------|------|------|
| 11.6.1 | **Cheap explore 模型** | `routing.explore: deepseek-chat` → `deepseek-chat` 或更小规格；Plan 可用 reasoner **仅 COMPLEX** | explore 步单价 −30%+ |
| 11.6.2 | **无 tool 步压缩** | 模型返回纯文本且无 verify 挂起时，下一步 **省略 tools**（仅 message 续写） | 收尾 summary 步 tools=0 |
| 11.6.3 | **Max steps 按 profile 收紧** | extended 100→60；complex 200→100，配 token 熔断 | 异常 Run token 有硬顶 |

```yaml
anvil:
  model:
    routing:
      explore: deepseek:deepseek-chat    # 后续可换更小模型 ID
      edit: deepseek:deepseek-chat
      plan: deepseek:deepseek-chat       # COMPLEX only → reasoner
  loop:
    token-budget-per-run: 500000         # 估算 input+output 超限则 graceful fail
```

---

### 11.7 Editor 上下文瘦身（P2，3 天）

| ID | 任务 | 说明 | 验收 |
|----|------|------|------|
| 11.7.1 | **open_files 上限** | 最多列 10 个 path；其余折叠为 `...+N more` | 20 tab 时不爆 prefix |
| 11.7.2 | **unsaved buffer 默认关** | 仅 focus 文件注入 unsaved；其余 on-demand | dirty 10 文件时不注入 80k |
| 11.7.3 | **selection 上限** | selection ≤120 行，超出截断 + 提示 read | 大段选中不整段注入 |

---

## 7. 推荐实施顺序

```text
Week 1   11.1 Prompt 分层 + 11.2.1 Mode 裁剪 tools     → 立刻 −25～35% input/step
Week 2   11.2 工具截断 + artifact + 11.4 Explore 默认关  → 简单任务 −40% 总 token
Week 3   11.3 Read 去重 + search snippet                  → 探索型 −30% read 体积
Week 4   11.5 Compaction + thread 摘要 + 11.7 editor 瘦身
Week 5+  11.6 路由 + token 硬预算 + tiktoken
```

### Quick Wins（本周可排期）

1. **`explore-sub-agent: false` 为默认**（COMPLEX 再开）  
2. **Agent 默认 profile → standard**（UI 仍可选 extended）  
3. **第 2 步起省略重复的 `anti_patterns` / `tool_examples`**  
4. **Ask 模式裁剪 write tools schema**  
5. **Trace 增加每步 `input_tokens` 与累计曲线**  

---

## 8. 成功指标（Token KPI）

| 指标 | 当前（估） | Phase 11 后目标 | 测量方式 |
|------|-----------|-----------------|----------|
| 单文件 patch Run 总 input tokens | ~80k～150k | ≤50k | Trace `usage` 累计 |
| 20 步 Agent Run 总 input tokens | ~300k～600k | ≤200k | 同上 |
| 每步平均 input tokens（步≥5） | ~15k～25k | ≤10k | step 级 metrics |
| `cached_tokens / input_tokens` | ~0～5% | ≥20%（DeepSeek/OpenAI） | provider usage |
| 重复 read 同文件（同 Run） | 常见 2～4 次 | ≤1 次全文 | tool 事件统计 |
| Explore 开启率（所有 Run） | ~100%（默认开） | ≤25% | `explore.started` 占比 |
| Token/成功任务（live benchmark） | 基线 T0 | −40% 且成功率不降 | benchmark-live + 账单 |

---

## 9. 风险与原则

1. **过度截断损成功率**：artifact 必须可 `fs.read` 按需拉回；摘要保留 path/行号。  
2. **Cache 依赖 provider**：DeepSeek cache 规则与 OpenAI 不同，需 **分 provider 测 hit 率**。  
3. **与 Phase 8 verify 冲突**：verify 摘要化后模型仍需足够错误信息 → 结构化 diagnostic 优先。  
4. **协议稳定**：新事件如 `context.artifact.stored` 可选，不破坏现有 UI。  
5. **用户可选「Quality over cost」**：UI 保留 extended + explore on + 全 buffer 注入开关。

---

## 10. 附录：与提升.md / 2.md 的关系

| 文档 | 定位 |
|------|------|
| `对比cursor进行提升.md` | Phase 1～5 工具与 Harness 对齐 |
| `对比cursor进行提升2.md` | Phase 6～10 理解力/精细度/闭环 |
| `对比cursor进行提升3.md`（本文） | **Phase 11 Token 经济性** — 在能力对齐后降本 |

### 关键代码索引（Token 相关）

| 模块 | 路径 | Phase 11 |
|------|------|----------|
| Prompt 装配 | `anvil-core/.../PromptBuilder.java` | 11.1 |
| 上下文压缩 | `anvil-core/.../ContextCompactor.java` | 11.5 |
| 预算 | `anvil-core/.../ContextBudget.java` | 11.5 |
| Explore | `anvil-core/.../ExploreAgent.java` | 11.4 |
| 主 Loop | `anvil-core/.../LoopEngine.java` | 11.1 / 11.5 |
| Editor 注入 | `anvil-core/.../RunRequest.java` | 11.7 |
| 工具执行 | `anvil-core/.../ToolExecutor.java` | 11.2 / 11.3 |
| Thread 记忆 | `anvil-app-server/.../ThreadMemoryStore.java` | 11.5 |
| 配置 | `application.yml` | 11.4 / 11.6 |
| Usage 上报 | `anvil-core/.../ModelUsage.java` | 11.1 / §8 KPI |

---

## 11. 总结

> **Anvil 费 token，不是因为「聊得多」，而是「每步重复付同样的前缀、探索付两次钱、工具结果太胖、compaction 太晚」。**

- **重复前缀** → StablePrefix + 步间去重 + cache 指标（11.1）  
- **双 Loop** → Explore 按需 + 预算隔离（11.4）  
- **工具太胖** → 分级截断 + artifact 外置（11.2）  
- **读太多** → ReadCache + search snippet（11.3）  
- **历史太长** → 更早 compact + thread 摘要（11.5）  

按 **11.1 → 11.2 → 11.4 → 11.3 → 11.5** 推进，预期 **同等 DeepSeek 任务 token −35%～45%**，成功率下降控制在 **≤3%**（以 live benchmark 为准）。Anvil 保留 **可审计 Trace、可内网部署** 优势的同时，运行成本可对齐 Cursor 量级。

---

*文档版本：Anvil Token Economy 3.0 · 2026-08-17*
