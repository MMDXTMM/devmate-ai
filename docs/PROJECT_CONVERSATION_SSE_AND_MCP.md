# 多轮项目 Agent、SSE 与 MCP 设计

## 1. 目标与分阶段边界

DevMate 要同时服务两类使用者：

- 浏览器用户通过“向项目提问”进行有上下文的中文项目理解；
- 外部 AI 客户端未来通过 MCP 调用 DevMate 的只读项目证据能力。

两个协议不能混为一谈：浏览器回答流使用业务 SSE；MCP 使用 Spring AI MCP Server 的 Streamable HTTP。后者是独立的工具协议，不复用页面的 `messages/stream` 接口，也不采用已经被 Streamable HTTP 替代的旧 MCP SSE transport。

当前闭环已经完成“多轮对话 + 每轮 Hybrid RAG + Spring AI 流式调用 + 浏览器 SSE”。MCP Server 仍是下一闭环，当前文档不得把计划写成已实现能力。

## 2. 当前已实现链路

```text
登录用户选择已解析项目
  → 创建固定 revision/provider/model/promptVersion 的对话
  → 提交问题与 UUID v4 attemptKey
  → 校验项目成员和对话归属
  → 对当前问题执行 Hybrid RAG
  → 短事务写入 USER/ASSISTANT(RUNNING)
  → 独立线程调用 Spring AI ChatClient.stream()
  → SSE 输出 message/evidence/token/done/failed
  → 短事务保存最终回答或可读失败
  → 下一轮携带最近已完成消息并重新检索证据
```

模型只负责基于消息历史和本轮证据生成中文回答。Java 服务负责权限、revision 固定、证据检索、消息顺序、并发控制、幂等、超时、持久化和错误脱敏。

## 3. HTTP 契约

普通接口继续使用统一响应：

- `POST /api/projects/{projectId}/agent-conversations`
- `GET /api/projects/{projectId}/agent-conversations`
- `GET /api/projects/{projectId}/agent-conversations/{conversationId}/messages`

流式接口为：

- `POST /api/projects/{projectId}/agent-conversations/{conversationId}/messages/stream`
- `Content-Type: text/event-stream`
- 请求包含 `question` 和小写 UUID v4 `attemptKey`

事件含义：

- `message`：服务端已创建回答消息并返回字符串 ID、模型和状态；
- `evidence`：本轮命中的文件、符号、行号和有限代码片段；
- `token`：模型增量文本；
- `done`：已持久化的完整回答；
- `failed`：可读失败，不包含 SQL、Key、Prompt 或模型原始响应。

浏览器使用 `fetch` 读取 SSE，而不是 `EventSource`，因为该请求需要 POST JSON 和账户 JWT。用户关闭弹窗会中止浏览器读取，但服务端任务仍会完成持久化，避免消息永久停在 `RUNNING`。

## 4. 状态、幂等与失败

- 对话固定项目 revision 和模型快照；源码版本变化后必须新建对话。
- `(conversation_id, attempt_key)` 防止网络重试重复调用模型；相同 key 对应不同问题返回冲突。
- `running_key=conversation_id` 的唯一约束保证一个对话同一时刻只有一个生成任务。
- 会话行锁串行分配 `sequence_no`，数据库唯一键作为最终一致性保护。
- 模型调用和 SSE 发送不在数据库事务内；只有开始、完成和失败状态使用短事务。
- 模型 429、网络错误、空回答、超长回答和任务队列拒绝均保存或返回可读状态。
- 只持久化证据 Chunk ID；历史回读时由 Java 重新校验并回填真实路径、行号和代码片段。

## 5. 下一闭环：只读 MCP Server

下一闭环采用 Spring AI WebMVC MCP Streamable HTTP Server，第一版只暴露四个受控只读工具：

1. `get_project_overview`：返回项目版本、技术栈和业务地图摘要；
2. `search_project_code`：在固定项目/revision 内执行 Hybrid RAG；
3. `get_symbol_context`：按合法 Chunk/Symbol 返回有限源码上下文；
4. `get_review_findings`：读取已保存的静态或 AI 审查结论。

MCP 参数不能决定账户、任意文件路径、数据库、Shell 或外部 URL。服务端必须从认证上下文固定用户身份，校验项目成员关系，并对输出做字符预算、源码裁剪和审计。第一版不提供写文件、运行命令、修改代码、提交 Git 或创建付费审查任务的 MCP Tool。

MCP 闭环的验收标准是：受信任客户端能发现四个工具；有权限项目可查询；越权项目、伪造 revision、非法 Chunk 和超预算请求被拒绝；所有工具均有正常、失败和权限测试。

