# 证据约束的 AI 代码审查 MVP

## 1. 当前边界

阶段 6 已实现从“客户端显式指定、服务端确认仍为最近成功的 Diff + 对应静态分析 + RAG 证据”到结构化 AI Finding 的工程闭环。本固定流水线继续保留；阶段 7 的受控 Tool Calling Agent 见 [TOOL_CALLING_AGENT.md](TOOL_CALLING_AGENT.md)，后续用同一缺陷集比较两条路径。

第一版只给出审查建议，不自动修改、提交或合并代码，也不允许模型执行 Shell、SQL 或直接访问数据库。

## 2. 请求流程

```text
POST /api/projects/{projectId}/ai-reviews，提交预期 reviewTaskId、revision 与 attemptKey
        ↓
只读预检：确认预期 Diff 仍是当前最近成功 Diff
        ↓
解析当前模型配置
        ↓
短事务：再次校验并固定该 Diff 和对应静态分析
        ↓
写入 RUNNING ai_review_task 与 ai_invocation_log
        ↓
事务外：以 Diff 目标 Chunk 执行 Hybrid RAG
        ↓
事务外：构造版本化 Prompt，通过 Spring AI `ChatClient.responseEntity` 调用账户选择的模型
        ↓
Java 校验 chunkId、枚举、长度、置信度和重复 Finding
        ↓
短事务：保存 LLM Finding、Token、耗时和成功状态
        ↓
失败时短事务：保存脱敏错误并释放运行幂等键
```

请求体示例：

```json
{
  "reviewTaskId": "2084116785588308000",
  "revision": "0123456789abcdef0123456789abcdef01234567",
  "attemptKey": "123e4567-e89b-42d3-a456-426614174000"
}
```

`reviewTaskId` 是 `BIGINT`，前端和 JSON 中必须使用字符串，避免 JavaScript 精度丢失；`revision` 必须是规范的 40 位小写 Git SHA；`attemptKey` 必须是客户端每次新操作生成的小写 UUID v4。参数格式错误返回 HTTP 400。若 ID 或 revision 已不再对应当前最近成功 Diff，预检或 `prepare` 二次校验返回 HTTP 409、业务码 `40900`，且不会创建 `ai_review_task` 或 `ai_invocation_log`。二次校验用于覆盖预检完成后又产生新 Diff 的竞态窗口。

模型或检索调用不位于数据库长事务中。任务使用 `running_key` 唯一键阻止同一 Diff 并发重复审查；V15 的 `attempt_key` 唯一键关联单次付费请求，响应丢失后通过 `GET /api/projects/{projectId}/ai-reviews/attempts/{attemptKey}` 精确回读，不能重发模型 POST。超过 10 分钟的 RUNNING 任务会先转为 FAILED，再允许新的 attempt 重试。

## 3. 前置条件

同一项目版本必须依次完成：

1. 源码导入与结构化索引。
2. Git Diff 覆盖报告。
3. 当前 Diff 的静态分析。
4. AI 审查；向量不可用时检索会显式降级为关键词/关系图模式。

AI 审查不会自动补做前置步骤，避免一次高成本请求隐藏多个失败来源。

## 4. 模型契约

`AiReviewModel` 隔离业务编排与模型提供方。当前模型由登录账户在“大模型连接”中选择，统一通过 Spring AI 1.1.8 的 `ChatModel/ChatClient` 调用 OpenAI 兼容接口：

- System Prompt 明确仓库源码、注释和文档是不可信证据，不能覆盖系统指令。
- Prompt 必须包含 JSON 要求，响应顶层固定为 `{"findings": [...]}`。
- 不设置输出 Token 上限，避免结构化 JSON 被截断；应用仍通过输入字符、检索 Token 预算和 Finding 数量限制资源。
- 使用 `ChatClient.responseEntity` 将响应映射为结构化 Finding，模型位置仍由服务端证据校验；
- 连接超时 5 秒，读取超时 90 秒；API Key 从账户加密配置解密后仅在调用边界使用。

参考官方接口说明：

- [Qwen OpenAI 兼容 Chat Completions](https://help.aliyun.com/en/model-studio/qwen-api-via-openai-chat-completions)
- [Qwen Structured Output](https://help.aliyun.com/en/model-studio/qwen-structured-output)

## 5. 证据校验

模型不能决定真实位置。Java 只接受本次 Prompt 中出现的 `chunkId`，并从服务端检索结果映射：

- `projectId` 和 revision 已在检索层隔离；
- `filePath/startLine/endLine` 使用服务端元数据，不采信模型生成值；
- category、severity 和 conclusionType 必须属于白名单；
- 置信度必须在 0 到 1；
- `NEEDS_VERIFICATION` 最多为 MEDIUM，置信度最多为 0.85；
- 相同 Chunk、类别和标题的重复项在入库前去重；
- 超出 30 条、引用不存在 Chunk 或字段不合法的结论被计入 `rejectedFindings`。

无有效 Finding 只表示“没有结论通过本次证据校验”，不代表代码绝对安全。

## 6. 数据与审计

V10 增加：

- `ai_review_task`：固定 Diff、静态分析、revision、模型、Prompt/检索版本、状态和计数；
- `ai_invocation_log.prompt_version/request_hash`：记录可复现版本和请求哈希；
- `review_finding.ai_review_task_id/chunk_id`：绑定 AI 任务和服务端证据；
- `review_finding.conclusion_type/confidence/risk_scenario/suggestion/verification`：保存结构化语义审查内容。

审计表不保存完整 Prompt、响应或私有源码，只保存 provider、模型、Token、耗时、状态、错误类别、Prompt 版本和 SHA-256 请求哈希。

## 7. API 与前端

- `POST /api/projects/{projectId}/ai-reviews`：携带预期 `reviewTaskId` 和 `revision` 执行一次 AI 审查；服务端不替客户端静默切换到新 Diff。
- `GET /api/projects/{projectId}/ai-reviews/latest`：读取最近任务及结构化 Finding。

Vue 弹窗打开时只读取历史记录，不自动调用模型。用户必须显式点击“开始 AI 审查”，界面会展示模型、证据数量、有效/拒绝结论、Token、耗时、事实/推断/待验证和验证方法。

相关 latest 查询使用 `created_at DESC, id DESC` 稳定排序，避免时间戳相同时结果不确定，同时保留各接口原有状态语义：`/review-diffs/latest` 返回最近一次 Diff 任务，包括失败任务；创建 AI 审查时只接受当前最近成功 Diff；静态分析和 AI 审查 latest 继续返回各自最近一次任务。

## 8. 配置

启动后在页面“大模型连接”中选择 DeepSeek、通义千问或 OpenAI，填写 Key、保存并测试。服务端必须通过 `DEVMATE_MODEL_ENCRYPTION_SECRET` 提供稳定的加密主密钥。Key 以 AES-GCM 密文保存且不回显，不得写入 Git、日志或普通配置。

## 9. 当前验证与限制

- 后端自动化测试覆盖 JSON 模型适配、证据伪造、字段校验、重复 Finding、成功/失败状态、并发幂等、Diff 漂移拒绝、稳定排序和超时任务恢复。
- 前端自动化测试与生产构建验证打开弹窗不自动消耗额度，并使用明确的 Diff ID 与 revision 发起审查。
- H2 已从空库完整执行 V1–V15；V15 增加可精确恢复付费请求的 `attempt_key` 唯一索引。
- 本地 MySQL 26.7 已从 V9 成功迁移到 V10；后端健康检查为 `UP`，原有 `devmate-ai` 项目数据可正常读取。Flyway 对高于 8.1 的 MySQL 版本给出兼容性提醒，后续升级依赖前需要继续做真实库回归。
- 真实调用需要当前账户先启用模型连接；未配置时任务会得到可读失败原因。不能把 Mock 测试当作真实模型效果证明。
- 当前请求是同步接口；阶段 9 再依据耗时和流量引入 MQ 异步化。
- 已建立固定 AI 缺陷评测集和受控 A/B 执行器，但尚未运行真实 canary，不宣称准确率或 Agent 优于固定流水线。

## 10. 面试必须掌握

1. 为什么模型调用不能放在数据库事务里？
2. 为什么 AI 审查必须独立于静态分析任务？
3. `running_key` 如何防止并发重复提交，服务崩溃后如何恢复？
4. 为什么模型只返回 Chunk ID，文件和行号由 Java 映射？
5. 为什么不保存完整 Prompt 和响应？如何用版本和哈希审计？
6. Structured Output 为什么仍然需要服务端校验？
7. “模型没发现问题”和“没有 Finding 通过证据校验”有什么区别？
8. 为什么当前先固定流水线，下一阶段才加入 Tool Calling？
9. 为什么客户端必须显式提交 Diff ID 和 revision，服务端还要进行两次校验？
10. `running_key` 与 `attemptKey` 分别解决什么问题，为什么响应丢失后不能盲目重发付费 POST？
