# 受控 Tool Calling 代码审查 Agent

## 1. 阶段目标

阶段 7 在阶段 6 固定审查流水线之外增加一条可比较的 Agent 路径：大模型负责选择调查步骤，Java 负责参数校验、权限边界、工具执行、审计和最终证据校验。

```text
POST /api/projects/{projectId}/ai-reviews/agent
  → 客户端提交预期 reviewTaskId、revision 与 attemptKey
  → 模型解析前预检：确认它们仍指向当前最近成功 Diff
  → prepare 短事务二次校验并固定 Diff、静态分析和 revision
  → Agent 模型选择只读 Tool
  → Java 白名单执行并回填 tool result
  → 合并 searchCode 返回的 Chunk 证据
  → 结构化审查模型生成 Finding
  → Java 校验证据归属、枚举、范围和重复项
  → 短事务保存任务、Finding、Token 与工具链
```

固定接口 `POST /api/projects/{projectId}/ai-reviews` 保留，后续使用相同缺陷集比较固定检索与 Agent 多步取证的成本和效果。

Agent 请求体与固定流水线使用同一契约：

```json
{
  "reviewTaskId": "2084116785588308000",
  "revision": "0123456789abcdef0123456789abcdef01234567",
  "attemptKey": "123e4567-e89b-42d3-a456-426614174000"
}
```

`reviewTaskId` 是 `BIGINT`，前端和 JSON 中以字符串传输；`revision` 必须是 40 位小写 Git SHA；`attemptKey` 是每次用户操作新生成的小写 UUID v4。服务端不会自动改用更新的 Diff。参数格式错误返回 HTTP 400；预检或 `prepare` 发现 ID/revision 漂移时返回 HTTP 409、业务码 `40900`，并保证不创建 `ai_review_task` 或 `ai_invocation_log`。预检位于模型注册表访问之前，二次校验用于关闭预检与任务创建之间的竞态窗口。

## 2. 当前工具白名单

| Tool | 输入 | 读取内容 | 为什么只读 |
| --- | --- | --- | --- |
| `getDiffCoverage` | 无 | 当前固定 Diff 的文件覆盖与映射符号 | Agent 不得切换审查版本 |
| `getStaticAnalysis` | 无 | 当前固定静态分析结果 | 不在高成本请求中隐式重跑工具 |
| `searchCode` | `query`、可选 `maxResults` | 当前项目固定 revision 的混合检索 Chunk | 项目和 revision 由 Java 注入，模型不能指定 |
| `analyzeProjectStructure` | 无 | 文件、包、Chunk 类型的有界摘要 | 不向模型一次发送整个仓库 |

第一版故意没有 `runStaticAnalysis`、`analyzeGitDiff` 等会触发任务的 Tool。审查前置步骤由用户显式完成，Agent 只读取固定结果，避免一次请求隐藏多个状态变化和失败来源。

## 3. Function Calling 协议

当前直接实现 DashScope 的 OpenAI 兼容协议，不额外引入 Agent 框架：

1. 应用发送消息和 JSON Schema 工具定义。
2. 模型返回 `tool_calls`，包含工具名和 JSON 参数。
3. Java 校验并执行工具。
4. 应用使用同一个 `tool_call_id` 追加 `role=tool` 的执行结果。
5. 再次调用模型，直到模型停止调用工具或达到安全上限。

协议由 Spring AI 统一适配 OpenAI 兼容接口；模型 Tool Call 只负责提出调用意图。Java 显式控制循环，便于展示和测试权限、参数、预算、超时与审计边界。

## 4. 安全和稳定性边界

- 工具注册表是唯一白名单；未知工具会记为失败并把脱敏错误回传模型。
- 模型只能提供业务参数，`projectId/revision/taskId` 来自服务端固定上下文。
- 参数必须是合法 JSON 对象，工具再执行字段白名单、类型、长度和数量校验。
- 每个工具在虚拟线程执行并有超时；外部调用和模型调用不处于数据库长事务。
- 默认最多调用 6 次；相同工具与参数最多重复 2 次，防止循环耗费额度。
- 单次工具输出、累计证据 Chunk 和 Token 预算都有上限。
- 至少一次 `searchCode` 必须成功并返回真实 Chunk，否则任务失败，不允许无证据生成最终 Finding。
- 仓库内容始终是不可信数据，源码注释或 README 中的指令不能改变系统规则。
- 模型不能执行 Shell、SQL、访问数据库、发起网络请求、修改源码或提交 Git。

## 5. 审计与隐私

V11 扩展 `tool_call_log`：

- `tool_call_id`：关联模型 tool call 与回填结果；同一模型调用内唯一。
- `step_no`：恢复工具执行顺序。
- `arguments_hash`：比较重复调用，不保存完整参数。
- `arguments_summary`：只保存参数键和字符数。
- `result_summary`：只保存模式、命中数、Token 等摘要。
- `status/latency_ms/error_code/error_message`：统计成功率和排障。

数据库不保存完整查询文本、源码 Tool 输出、完整 Prompt 或模型原始回答。接口只返回脱敏工具调用链。

## 6. 状态和失败路径

- Diff 漂移：在模型解析前或 `prepare` 二次校验时拒绝，返回 409，不创建 AI 任务或调用日志。
- 正常：准备任务 → 多轮工具执行 → 最终模型 → 证据校验 → `SUCCEEDED`。
- 模型不检索代码：任务和调用日志进入 `FAILED`，释放 `running_key`。
- 未知工具/参数错误/工具超时：该步骤写入失败审计；模型可以在总步数内纠正。
- 重复循环/超过步数：整个 Agent 任务失败并释放幂等键。
- 最终模型伪造 Chunk：该 Finding 被拒绝，不写入 `review_finding`。
- 进程中断：沿用阶段 6 的超时 RUNNING 回收机制。
- 客户端响应丢失：不重发付费 POST，使用 V15 唯一 `attemptKey` 精确读取本次任务；并发同配置任务不会被误认。

相关 latest 查询统一使用 `created_at DESC, id DESC` 作为稳定顺序，但不改变接口原有状态语义：`/review-diffs/latest` 仍返回最近一次任意状态的 Diff，便于展示失败；Agent 创建流程内部只接受当前最近成功 Diff；静态分析和 AI 审查 latest 仍返回各自最近一次任务。

## 7. 配置

`application.yml` 的 `devmate.review-agent` 提供 Prompt 版本、调用上限、重复上限、证据预算、输出上限和工具超时。模型来自当前账户启用的“大模型连接”。Spring AI 负责工具协议与消息转换，Java 编排器关闭框架内部自动执行，继续负责权限、参数、超时、循环上限和审计。

## 8. 测试和当前限制

自动化测试覆盖：

- Spring AI 工具定义请求、tool call 解析、工具结果回传、最终消息和账户模型绑定。
- Agent 成功检索、工具链审计、Token 汇总与结构化 Finding。
- Agent 没有代码证据时失败关闭。
- 旧 Diff ID、错误 revision 和预检后二次漂移在模型调用前返回 409，且 AI 任务与调用日志零落库。
- 同时间戳任务按较大 ID 稳定选择，同时保留 FAILED 状态的可见性。
- 重复 `attemptKey` 返回 409，按 attempt 精确回读只返回该项目和该请求对应的任务。
- 原固定流水线回归、数据库 V11 迁移、Vue 显式触发与工具链展示。

当前没有配置真实 DashScope Key，因此只完成了 Mock 协议和工程闭环验证，不能宣称已量化真实 Agent 效果。阶段 8 将使用固定缺陷集比较固定流水线与 Agent 的命中、漏报、误报、耗时和 Token。

## 9. 面试必须会解释

1. Function Calling 为什么不是“模型直接执行 Java 方法”？
2. 为什么 Tool Schema 不能替代服务端参数校验？
3. 为什么项目和 revision 不能作为模型参数？
4. 如何防止 Agent 循环调用并无限消耗 Token？
5. 为什么工具调用日志不保存完整参数和输出？
6. 为什么必须先获得 `searchCode` Chunk 才允许最终审查？
7. 固定流水线和 Agent 路径各有什么优缺点，如何用数据比较？
8. 为什么模型解析前预检后，`prepare` 中还必须再次验证 Diff？
