# Anvil vs Cursor：体感差距再分析（提升计划 2.0）

> 承接 [`对比cursor进行提升.md`](./对比cursor进行提升.md)（Phase 1～5 已落地）。  
> 本文回答：**工具链补齐后，为什么 Anvil 仍感觉不如 Cursor「编码强、改得细、理解深」？** 以及 **下一步怎么追**。

---

## 1. 结论（一句话）

Cursor 的强 = **强模型 × 语义级代码理解 × IDE 原生闭环 × 低摩擦多轮编辑 × 可量化的质量回路**。  
Anvil 已完成 **Harness MVP + 工具盘对齐（grep/patch/index/verify）**，但在 **理解深度、编辑粒度、反馈密度、真实 LLM 评测** 上仍差一个数量级——体感的「不精细、不理解」主要来自 **索引与上下文质量**，而非单纯「少几个工具」。

---

## 2. 用户体感的三维差距

| 维度 | Cursor 体感 | Anvil 当前体感 | 典型表现 |
|------|-------------|----------------|----------|
| **理解能力** | 「知道项目结构、能找到对文件」 | 「经常 grep 大海捞针、跨模块漏改」 | 改 A 模块却动错 B；找不到接口实现 |
| **精细度** | 「改几行、diff 小、可逐块 Accept」 | 「仍整文件 write 或 patch 对不准」 | `search_replace` 唯一匹配失败；大文件 diff 噪声 |
| **编码能力** | 「改完能编译、测试绿、自己修错」 | 「改了但不确定、verify 常关、错误要人盯」 | 编译错多轮才修；thread memory 偶发 400 |

---

## 3. 已补齐 vs 仍落后（2026-08 现状）

### 3.1 已对齐 Cursor 基础盘（见提升.md §11）

- 工具：`grep` / `search_replace` / `apply_patch` / `codebase.search` / `symbols.search` / `diagnostics.collect` / `git.*`
- 策略：patch 自动放行、Yolo writes、`plan.update` 不卡审批
- 上下文：open files、focus、selection、RunAnchors、SQLite thread memory
- UI：多文件 Review Diff、Terminal 镜像、Trace 导出
- 编排：READ 工具并行、VerifyPass（可选）、Skills、Benchmarks（scripted）

### 3.2 仍明显弱于 Cursor 的能力

| 能力 | Cursor | Anvil 现状 | 对体感的权重 |
|------|--------|------------|--------------|
| **语义检索** | embedding + rerank，`@codebase` | 路径 trigram + 字面 grep 组合 | ★★★★★ |
| **LSP / 类型** | 定义、引用、诊断、重构 | 无；Java 仅 regex 符号索引 | ★★★★★ |
| **编辑模型** | 多 hunk、模糊匹配、Apply 队列 | 精确字符串 / 单文件 unified diff | ★★★★☆ |
| **IDE 共编** | 可编辑编辑器、LSP 红线实时回灌 | JavaFX 只读编辑器 | ★★★★☆ |
| **@ 引用** | `@file` `@folder` `@docs` | 无；靠 openFiles 被动注入 | ★★★★☆ |
| **验证默认** | 常自动 lint/test | `verify.auto-after-write: false` | ★★★☆☆ |
| **子 Agent** | Explore / Review / Test 分工 | 单 LoopEngine 顺序步进 | ★★★☆☆ |
| **模型路由** | 任务选 mini/strong/reasoner | 用户手动选 deepseek-chat | ★★★☆☆ |
| **真实评测** | 内部 dogfood + 线上指标 | Benchmark 几乎全 `ScriptedModel` | ★★★☆☆ |
| **Prompt/Rules** | 多层 Rules + 项目记忆 | `AGENTS.md` + 短 `<tool_guidance>` | ★★★☆☆ |

---

## 4. 根因分析（为什么「感觉」仍差）

### 4.1 理解能力：索引是「搜字面」，不是「懂代码」

**现状**（`CodebaseSearchTool`）：

- 路径包含 query → 加分  
- trigram → 加分  
- 符号名 substring → 加分  
- 内容 = `GrepTool` **整词 quote 匹配**（非语义）

**后果**：

- 用户说「优化 Workbench 连接逻辑」→ 搜不到 `AnvilClient` / `connect()`  unless 字面出现  
- 跨模块重构（interface + 3 impl）→ 模型要多步 `grep`/`read`，步数膨胀、易漏文件  
- Cursor 的 `@codebase` 能召回 **语义相关** 片段，Anvil 仍靠 **关键词撞大运**

**Java 特有问题**：

- `symbols.search` 是浅层 regex，**无继承、实现类、调用链**  
- 无 import 解析、无 Maven 模块依赖图 → 「精细理解项目」做不到

---

### 4.2 精细度：编辑工具对「真实脏代码」脆弱

| 问题 | 说明 |
|------|------|
| `search_replace` 必须 **精确** old_string | 多一行空格、换行符 CRLF 即失败；Cursor 有 fuzzy / 多候选 |
| `apply_patch` **单文件** | 跨文件原子修改需多次 tool call，中间态可能编译失败 |
| `fs.write` 仍被模型滥用 | 虽有 Prompt 限制 & Yolo，大文件整写仍偶发（上下文截断、参数超长） |
| Diff Review 无 **hunk 级 Accept** | 只能整文件 Accept/Reject，不如 Cursor 逐块 Apply |
| 无 **merge 冲突** 处理 | 并发改同一文件时无三方合并 |

**体感**：Cursor「改一行是一行」；Anvil「改一次是一刀，经常切歪」。

---

### 4.3 编码能力：闭环不完整、默认偏保守

1. **Verify 默认关闭**（`application.yml`：`auto-after-write: false`）  
   - 设计文档写「改 Java 必 test」，运行时 **不自动跑** → 模型容易「改完就宣称完成」

2. **诊断未强制回灌 Loop**  
   - `diagnostics.collect` 存在，但 **WRITE 后不会自动触发**（除非 verify 开且失败 inject）  
   - Cursor：保存/编辑后 IDE 诊断 **自动进入下一轮**

3. **编辑器只读**  
   - 用户手改与 Agent 改 **不同步**；无 LSP 补全辅助人类校正  
   - Agent 看不到用户 **手改后的 buffer**，只能读磁盘（可能 stale）

4. **Thread memory 质量**  
   - 已修复 orphan `tool` 消息（`MessageHistorySanitizer`），但 **compaction 摘要仍丢细节**  
   - 长会话后模型「忘记」已读文件内容，重复 read 或重复改

5. **Benchmark 与真实脱节**  
   - `fixtures/benchmarks/` 全走 **ScriptedModel**，不测 DeepSeek/OpenAI 真实推理  
   - 「感觉变强/变弱」**无线上 KPI**，优化方向易偏

---

### 4.4 模型与 Prompt：不是同一条起跑线

| 项 | Cursor | Anvil |
|----|--------|-------|
| 默认模型 | 云端强模型 + 路由 | dev 默认 `scripted`；UI 常用 `deepseek-chat` |
| System 体量 | 长 Rules + 工具策略 + 项目记忆 | ~15 行 instructions + `<tool_guidance>` 一段 |
| 工具 JSON | 严格名 + 描述丰富 | 已有，但 **无 few-shot 示例** |
| 坏 tool call 修复 | 重试 / 纠错 | 直接 `tool.failed`，靠下一步浪费 step |

**注意**：即使用 **同一 DeepSeek 模型**，Harness 差距仍会导致成功率差 2～3 倍——**不是换模型 alone 能解决的**。

---

### 4.5 交互摩擦：步数预算被「非编码」消耗

仍存在的摩擦（相对 Cursor Agent 默认）：

- `shell.exec` **仍要审批**（合理但慢）  
- 复杂任务 **无 Planner 子 Run**（COMPLEX 仅一条 developer hint）  
- **无 Background Agent** 做 explore，主 Agent 边搜边改  
- Approval 弹窗排队（已优化 UX，但仍打断心流）  
- SSE/UI 偶发问题（历史上有 hang、RunTraceRow 脏构建等）→ 信任感下降  

---

## 5. Cursor 能力公式（对照用）

```text
Cursor 编码体验 =
  Model(strong, routed)
× Retrieval(semantic index + LSP symbols)
× Edit(patch-first, fuzzy, multi-hunk, apply queue)
× Context(open files + @refs + diagnostics + git)
× Loop(verify → fix → verify, low approval friction)
× Observability(user trusts diff + tests)
```

Anvil 当前：

```text
Anvil ≈
  Model(user-picked, single)
× Retrieval(literal index + grep)          ← 最大短板
× Edit(exact replace / single-file patch) ← 第二短板
× Context(open files + anchors + memory)  ← 中等
× Loop(verify optional, approval on exec) ← 保守
× Observability(Trace 强, IDE 弱)         ← UI 只读
```

---

## 6. 优化方案 2.0（Phase 6～10）

> 原则：**先提升「理解与改对」，再追求「像 Composer 一样大任务」**；每项可 Trace 验收。

---

### Phase 6：理解力 — 从「搜字面」到「搜语义」（3～4 周）

| ID | 任务 | 说明 | 验收 |
|----|------|------|------|
| 6.1 | **Embedding 索引** | 文件切块（函数/类级）→ 向量库（本地 sqlite-vec / 内网 API）；`codebase.search` 增加 semantic 通道 | 「连接逻辑」能召回 `AnvilClient.connect` |
| 6.2 | **Java 符号图升级** | 解析 `implements`/`extends`、构造器、@Override；`symbols.search` 返回 **implementors** | interface 改动能列出全部 impl 路径 |
| 6.3 | **Maven 模块图** | 解析 `pom.xml` 依赖；Run 注入 `<module_graph>` | 跨模块 refactor 步数 −30% |
| 6.4 | **UI @ 引用** | Prompt 框支持 `@path` / `@folder`；解析后注入 developer 消息 | 用户指哪打哪，无需手动 open tab |
| 6.5 | **Query 扩展** | codebase.search 前 LLM 或规则生成 3～5 个 grep 变体（同义词、类名 camel/snake） | Recall@10 提升（基准集） |

**KPI**：`cross-module-refactor` 任务（真实 DeepSeek）找全文件 ≤8 步，成功率 ≥70%。

---

### Phase 7：精细度 — 编辑像 Cursor Apply（2～3 周）

| ID | 任务 | 说明 | 验收 |
|----|------|------|------|
| 7.1 | **`search_replace` fuzzy** | 空白归一、Levenshtein 窗口、返回「最接近匹配」候选 | 空格/缩进差不再一次失败 |
| 7.2 | **`apply_patch` 多文件** | 支持 unified diff 多 `---/+++`；原子 apply + 全失败 rollback | 一次 patch 改 3 文件 |
| 7.3 | **Edit 预览 hunk 级** | Review Diff 按 hunk Accept/Reject；非整文件 | 对标 Cursor partial apply |
| 7.4 | **禁止大文件 write 硬约束** | Tool 层：>300 行 `fs.write` 直接拒绝 + 提示用 patch | Trace 中大文件 write = 0 |
| 7.5 | **`edit.plan` 工具** | 输出 `{path, hunks[]}` 计划，用户确认后批量 apply | 复杂重构先 plan 后执行 |

---

### Phase 8：编码闭环 — 默认「改完必验」（2 周）

| ID | 任务 | 说明 | 验收 |
|----|------|------|------|
| 8.1 | **Verify 分 profile 默认** | Agent+extended：`auto-after-write: true`；standard 保持 false | 改 Java 后 Trace 必有 `verify.*` |
| 8.2 | **WRITE → diagnostics 链** | patch/write 成功后 **自动** `diagnostics.collect(compile)`（轻量） | 编译错 1 步内回灌 |
| 8.3 | **Verify 失败 → 强制 fix 步** | verify.failed 后注入 developer：「必须修复后再 complete」 | 模型不能 ignore 红字 |
| 8.4 | **Benchmark 真实 LLM 套件** | `benchmark-live.json` + `DEEPSEEK_API_KEY` CI nightly | 6 任务成功率趋势可见 |
| 8.5 | **Golden trace 对比** | 保存 Cursor/Anvil 同任务 Trace diff 工具 | 回归可量化 |

配置建议（dev profile）：

```yaml
anvil:
  verify:
    auto-after-write: true      # Agent 模式默认开
    auto-compile-after-write: true  # 新项：轻量 compile
    command-template: "mvn -q test -pl {module} -am"
    timeout-ms: 90000
```

---

### Phase 9：IDE 共编与 LSP（3～5 周）

| ID | 任务 | 说明 | 验收 |
|----|------|------|------|
| 9.1 | **可编辑 CodeEditor** | Workbench 编辑器可改 + 保存；dirty 状态同步 Run | 人机共编同一 buffer |
| 9.2 | **jdtls 桥** | 子进程 LSP：definition、references、diagnostics | 「跳转到定义」可用 |
| 9.3 | **诊断流式回灌** | 文件 save 后 diagnostics → 自动追加 thread（可选） | 类 Cursor Problems 面板 |
| 9.4 | **Model routing** | 探索步 mini、编辑步 chat、复杂 plan reasoner | 成本降、质量升 |

---

### Phase 10：编排升级 — 大任务像 Composer（中长期）

| ID | 任务 | 说明 |
|----|------|------|
| 10.1 | **Explore 子 Agent** | 只读 grep/search/read，输出「文件清单 + 摘要」给主 Agent |
| 10.2 | **Planner Run** | COMPLEX profile 先 `plan.update` + 结构化 steps，再逐步执行 |
| 10.3 | **Parallel write 队列** | 无依赖文件的 patch 并行（带 lock） |
| 10.4 | **MCP 启用** | junit report、github PR、checkstyle |
| 10.5 | **Prompt 2.0** | 分 mode 长指令 + 工具 few-shot + 反模式清单（禁止 shell grep 等） |

---

## 7. 推荐实施顺序（2026 Q3～Q4）

```text
立即（1～2 周）     Phase 8 部分 + 7.4 + 6.4(@引用)     → 体感「改完有反馈、少整文件写」
短期（1 月）        Phase 7 全部 + Phase 6.2/6.3        → 体感「改得准、找得全」
中期（2 月）        Phase 6.1 embedding + Phase 9.1/9.2  → 体感「理解项目」
长期                Phase 10                              → 大任务接近 Composer
```

### Quick Wins（本周可排期）

1. **Dev 默认开启 verify**（或 UI 勾选「Auto verify after edit」默认 true）  
2. **`fs.write` 行数硬限制**（Tool 层拒绝）  
3. **Prompt 增加反模式**：列举 Trace 中常见失败（shell grep、整文件 write、不 verify）  
4. **Benchmark 增加 1 条 DeepSeek live**（`patch-add` 真实跑）  
5. **Workbench `@file` 解析**（从 prompt 提取路径注入）  

---

## 8. 成功指标（2.0 KPI）

| 指标 | 当前（估） | Phase 7+8 后 | Phase 6+9 后 |
|------|-----------|--------------|--------------|
| 单文件 bugfix（DeepSeek live） | ~55% | ≥75% | ≥85% |
| 跨 3 文件 refactor 成功率 | ~30% | ≥50% | ≥70% |
| 平均 `search_replace` 失败重试次数 | 2.5 | ≤1.2 | ≤0.8 |
| 每 Run `fs.write` >300 行次数 | 常见 | ≤1 | 0 |
| 改 Java 后自动 verify 覆盖率 | ~0%（默认关） | ≥80% Run | ≥95% |
| codebase.search 有效召回（人工评） | ~40% | ~55% | ≥75% |
| 用户主观「接近 Cursor」评分（1～5） | ~2 | ~3 | ≥4 |

---

## 9. 风险与原则

1. **Embedding 成本**：优先本地小模型 / 缓存 chunk，避免每步 re-embed 全库。  
2. **LSP 重量**：jdtls 内存大，按 workspace 懒启动、单实例。  
3. **Verify 误开**：全仓 `mvn test` 仍禁止；坚持 `-pl {module}`。  
4. **协议稳定**：新能力走现有 Event，不破坏 CLI/UI。  
5. **先测再吹**：每个 Phase 必须补 **live benchmark**，避免 scripted 自嗨。

---

## 10. 附录：与提升.md 的关系

| 文档 | 定位 |
|------|------|
| `对比cursor进行提升.md` | Phase 1～5 差距分析 + **已完成**清单 |
| `对比cursor进行提升2.md`（本文） | Phase 1～5 **之后**仍存在的体感差距 + Phase 6～10 路线 |

### 关键代码索引（2.0 相关）

| 模块 | 路径 | Phase |
|------|------|-------|
| 字面搜索 | `anvil-tools/.../CodebaseSearchTool.java` | 6 替换/增强 |
| 符号索引 | `anvil-tools/.../index/IndexBuilder.java` | 6.2 |
| 编辑 | `anvil-tools/.../EditTools.java` | 7 |
| Diff UI | `anvil-ui/.../DiffReviewPanel.java` | 7.3 |
| Verify | `anvil-core/.../VerifyPass.java` | 8 |
| Prompt | `anvil-core/.../PromptBuilder.java` | 8.3 / 10.5 |
| Benchmark | `anvil-core/.../benchmark/` | 8.4 |
| Memory | `MessageHistorySanitizer.java` | 已修，持续观测 |

---

## 11. 总结

> **Anvil 不是模型笨，而是「看不懂、改不细、验不准」的 Harness 差距在 Phase 5 之后仍然主导体感。**

- **看不懂** → 缺语义索引 + LSP + @引用（Phase 6、9）  
- **改不细** → 缺 fuzzy patch + hunk apply + 大文件硬拒（Phase 7）  
- **验不准** → verify/diagnostics 默认关、benchmark 缺 live（Phase 8）  

按 **8 → 7 → 6 → 9 → 10** 推进，可在保留 Anvil **协议化、可审计、可内网部署** 优势的同时，把日常编码体验拉到 Cursor 的 **70%～85%**；剩余差距主要在 **云端模型路由、产品 polish、生态 MCP**，需中长期投入。

---

*文档版本：Anvil Gap Analysis 2.0 · 2026-08-16*
