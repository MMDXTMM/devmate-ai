# DevMate AI API 契约治理

## 1. 契约来源

DevMate AI 使用 Springdoc 从 Controller、DTO、Bean Validation 和 OpenAPI 注解生成契约：

- JSON：`GET /v3/api-docs`
- 调试页面：`GET /swagger-ui.html`

OpenAPI 描述请求和响应边界；Service 仍是业务规则、权限、状态流转和数据隔离的权威实现。

## 2. 全局约定

- 业务接口统一返回 `ApiResponse<T>`；成功业务码为 `0`。
- 分页统一返回 `PageResponse<T>`，页码从 `1` 开始，`size` 最大为 `100`。
- HTTP 与业务错误保持一致：400 参数、401 未认证、403 无权、404 不存在、409 冲突、500 内部错误。
- 数据库 `BIGINT/Long` ID 在 JSON 和前端类型中统一为 `string`，避免 JavaScript 精度丢失。
- 受保护接口使用 `bearerAuth` JWT 安全方案；前端不得把登录保护当成后端授权的替代品。
- Entity 不进入请求或响应；创建、更新、查询和响应使用独立 DTO。

## 3. 契约变更流程

每个 Java + Vue 纵向闭环按以下顺序执行：

1. 明确角色、目标、前置条件、正常流程、失败路径、权限和非目标。
2. 定义请求、响应、校验、错误、幂等或并发语义。
3. 实现后端并生成 `/v3/api-docs`。
4. 使用契约测试检查关键路径、状态、字段类型、枚举、必填项和认证声明。
5. 再调整 `frontend/src/types` 与 `frontend/src/services`，覆盖加载、成功、空数据和失败状态。
6. 运行后端、前端、生产构建与差异检查，记录未验证边界。

以下变化视为可能破坏兼容：删除或重命名字段、修改字段类型、把可选输入改为必填、收窄枚举、修改路径或方法、改变认证和错误语义。此类变化必须先说明影响，再修改前端。

## 4. 当前覆盖范围

业务接口均已纳入契约治理，并按业务职责分组：

- 认证、模型连接与健康检查。
- 项目管理、源码导入、源码结构与业务地图。
- 向量索引、Hybrid RAG 检索与检索评测。
- Git Diff、审查上下文、静态分析、AI 审查、反馈与审查评测。
- 一键代码审查工作流、AI 项目理解报告与项目多轮对话。
- 多语言项目的AI追问、版本化确认、后台源码生成、文件预览与ZIP下载。

每个操作必须有中文业务摘要；受保护的 `/api/**` 操作统一声明 `bearerAuth`、401 和 403。注册、登录和健康检查保持公开。全局契约门禁会验证这些规则，并将 Java `Long` 类型的 `id/*Id/*Ids` 发布为 JSON 字符串，与 Jackson 和 Vue 的真实约定保持一致。

其中项目管理与一键审查还包含更细的端点级业务响应说明。

项目管理 CRUD：

- `POST /api/projects`
- `GET /api/projects`
- `GET /api/projects/{projectId}`
- `PUT /api/projects/{projectId}`
- `DELETE /api/projects/{projectId}`

一键代码审查工作流：

- `POST /api/projects/{projectId}/review-workflows`
- `GET /api/projects/{projectId}/review-workflows/latest`

创建接口同步执行 `SOURCE_IMPORT → DIFF → STATIC_ANALYSIS → EMBEDDING → AGENT_REVIEW → COMPLETED`。同一 `attemptKey` 幂等返回原运行；同项目已有不同键任务运行时返回 409。任一阶段失败后停止，并返回 `FAILED`、失败阶段、脱敏原因和恢复建议。最近运行不存在时返回 404。`attemptKey` 必须为小写 UUID v4。

契约测试固定验证 JWT、安全响应、全部操作摘要、字符串 ID、敏感 API Key 只写、幂等键格式、项目创建状态，以及审查工作流的状态、阶段和主要错误响应。

当前 Vue 仍使用集中式手写 API Client。全量核心契约稳定前不同时维护手写和生成两套客户端；后续会先固定 OpenAPI 快照和生成脚本，再迁移调用方。

## 5. 环境与安全

本地默认开启文档。生产或公开部署设置：

```bash
export DEVMATE_OPENAPI_ENABLED=false
```

Swagger UI 只用于查看和调试契约，不证明接口已经通过授权、并发、事务或真实数据库验收。API Key、JWT、私有仓库地址和真实源码不得写入示例、测试快照或提交记录。


## 账户模型配置兼容扩展（2026-11-12）

PUT /api/model-connections 保留 provider/model/apiKey，新增可选 baseUrl（最多 500 字符）和 protocol（仅 CHAT_COMPLETIONS）。COMPATIBLE 可提交服务端信任名单中的规范化 API 根地址和符合名称规则的自定义模型 ID；官方地址及官方模型选择仍受原限制。省略新字段保持账户已保存地址，旧数据回退默认地址；更换地址必须重填 Key。错误地址、协议或缺 Key 返回 400，不更改原活动连接。成功仍返回 200 ApiResponse<ModelProviderResponse[]>。

GET /api/model-connections 新增 protocol、allowedBaseUrls；baseUrl 为账户保存地址，models 包含当前自定义模型及建议值。API Key 为只写入字段，不出现在任何响应。页面入口提醒只读此接口，不会以“已配置”冒充真实连通性；测试接口仍由用户显式触发。


## 项目阅读与证据兼容扩展（2026-11-13）

- `GET /api/projects/{projectId}/sources/chunks/{chunkId}?offset=0`：只读同项目已导入Chunk，HTTP200返回`SourceSymbolDetailResponse`；项目成员校验有效，跨项目Chunk404、未登录401、越权项目403。offset为UTF-16字符偏移，负数、越界或落在低代理项边界400；每次至多16000字符，不拆代理对。新增`codeOffset/nextOffset`，null表示该Chunk已读完。响应含固定revision和真实起止行，客户端必须核对版本。原symbol详情接口保持兼容。
- `GET /api/projects/{projectId}/agent-conversations/{conversationId}`：读取自己的会话元数据，不调用模型，不要求当前模型可连接；成员及会话归属校验，其他用户403、删除后404。可恢复最近50条列表之外的会话地址。
- 导览`content.flows[].transitions`为兼容可缺字段：`fromStep`一开始、`toStep`可null（结束）、`condition/result/conclusionStatus/evidenceIds`。最多20条，校验步骤范围及证据，不接受UNKNOWN边。旧JSON不重写，缺失字段显示未记录分支。
- `BusinessFeatureDetailResponse.unresolvedCalls`列出静态链断点，旧数据/前端可缺。返回的静态链不代表运行调用图。
- 会话`evidence_json`从旧字符串ID数组兼容扩展为`{chunkId,startLine,endLine,characters}`指针数组，不复制源码；历史回读仍支持旧格式，按真实Chunk回填并约束范围。导览同样仅保存引用和取样位置。
- 页面Hash保存项目、revision、模块/流程、会话及筛选，不含Token或源码；服务端权限不会因URL恢复而跳过。检索版本更新为`lexical-graph-v2`，原有已保存审查/评测仍保留当时版本。

运行时OpenAPI3.1.0确认新增路径、字符串ID和offset参数；权限、offset边界、兼容与真实MySQL验收见体验矩阵。


## 多语言项目生成（2026-11-13）

- GET /api/generation-sessions：当前账户最近20条会话，按updated_at/id倒序；GET /{sessionId}恢复完整最新方案。只读不调用模型。
- POST /api/generation-sessions：requirement必填、最多2000字符；真实AI返回第一轮选择题，成功201。没有账户模型时409，模型失败给可读错误，不降级为规则冒充AI。
- POST /{sessionId}/clarifications：沿用answers，新增可选字符串versionId。现代页面仅提交本轮新问题（每轮最多3题）；后端合并历史答案、保留全部问题。旧版本、处理中、超出8轮返回409；非法问题/选项/推荐来源返回400。网络调用前用THINKING状态防并发，失败回CLARIFYING并记录lastError，保存成功才增加版本。
- GenerationSpecResponse新增stage和readyForGeneration；阶段为BUSINESS/LANGUAGE/TECHNOLOGY/DETAILS/REVIEW，旧数据LEGACY。全部阶段完成且必答已完成才能confirmations；确认版本固定。会话新增THINKING状态与lastError，旧草案仍可读取和确认。
- POST /{sessionId}/runs：字符串versionId与16～80位字母数字下划线短横线attemptKey，返回202 ApiResponse<GenerationRunResponse>。同确认版本成功/进行中复用；失败同attemptKey复用失败记录，新attemptKey才显式重试。状态RUNNING/SUCCEEDED/FAILED/CANCELLED。超时或重启中断在10分钟后查询时转失败；取消后的新attemptKey可显式重试。
- GET /{sessionId}/runs/latest：尚未生成时data=null，轮询只读。GET /{sessionId}/runs/{runId}/files?path=...只返回成功任务清单内文件，错误文件/任务404、未完成409。GET /.../download返回认证的application/zip附件，Cache-Control:no-store；错误仍为统一JSON响应。
- 初次生成结果verification固定STRUCTURE_ONLY，展示“未执行编译测试”，不等同后续独立版本的运行验证。模型无法选择Shell/SQL/任意目录；最大60个模型文件、180000字符，源码写入workspace/generated独立目录，不返回服务器绝对路径。模型Key不进入任务或文件。


## 生成项目版本、验证与任务（2026-11-14）

以下路径均以 `/api/generation-sessions/{sessionId}` 为前缀，先校验父会话账户归属；跨账户403、父子不匹配404、状态冲突409，响应ID均为字符串。

| 方法与路径 | 输入、结果与行为 |
| --- | --- |
| POST /runs/{runId}/revisions | 幂等建立成功原版快照；GET同路径列出最近50版 |
| GET /runs/{runId}/selection | 当前选用版本；POST /runs/{runId}/selection/{revisionId}选用或恢复同run成功版 |
| GET /revisions/{revisionId} | 指定版本状态与文件清单，不返回服务器绝对路径 |
| GET /revisions/{revisionId}/files?path= | 读取成功版本清单内文本；/download为认证ZIP |
| POST /revisions/{revisionId}/verifications | `{attemptKey,repairLimit}`，repairLimit为0～2；202异步验证，同键幂等；/verifications/latest读取结果 |
| POST /revisions/{revisionId}/change-plans | `{request,attemptKey}`，request最多2000字符；202只创建计划，/change-plans/latest恢复 |
| GET /change-plans/{planId} | PLANNING/PLANNED/GENERATING/SUCCEEDED/FAILED/CANCELLED |
| POST /change-plans/{planId}/confirmations | 显式确认生成独立新版，重复确认不多调用 |
| GET /revisions/{revisionId}/comparison?beforeId= | 同run成功版文件新增/修改/删除；/diff?beforeId=&path=提供前后文本 |
| POST /revisions/{revisionId}/learning-guides | `{attemptKey}`，202异步分析；GET /learning-guides/latest只读缓存 |
| GET /revisions/{revisionId}/runtime-guide | 确定性文件识别，无模型调用 |
| POST /revisions/{revisionId}/error-help | `{error,attemptKey}`，error最多8000字符，常见凭据先脱敏；GET /error-help/latest回读 |
| GET /tasks/latest?kind=&referenceId= | 当前业务任务的真实阶段/时间/模型次数/Token；kind为GENERATE/VERIFY/PLAN/CHANGE/GUIDE/ERROR_HELP |
| POST /tasks/{taskId}/cancellations | 显式取消；已完成任务返回终态，排队取消不调用模型；发布与取消串行化 |

验证状态为RUNNING/PASSED/FAILED/BLOCKED/CANCELLED，结果包括固定profile/command、exitCode、durationMs、截断output与testsExecuted。无测试/无依赖环境不能显示测试通过；Java只编译。AI修复最多2次，BLOCKED不修复，不能删改原测试、依赖、数据或增删文件。

导览与报错任务为RUNNING/SUCCEEDED/FAILED/CANCELLED，原始导览按版本保存；新的attemptKey明确新尝试，查询不自动付费。Token只有模型返回完整用量时提供数值，否则usageComplete=false、字段省略；不提供猜测费用。取消与失败不自动切换选用版本。

运行时OpenAPI已核对上述29个生成相关路径、31个操作与新增响应字符串ID。正常、失败、幂等、取消晚响应和账号隔离见五步增强回归记录。


## 删除账户模型连接（2026-11-16）

`DELETE /api/model-connections/{provider}` 返回200 `ApiResponse<ModelProviderResponse[]>`。provider大小写规范化后接受DEEPSEEK/DASHSCOPE/OPENAI/COMPATIBLE，仅按认证账户+provider删除；调用方不能提交其他用户ID。未知provider400、未登录401；不存在或重复删除均幂等返回当前列表。

删除后该项configured=false且active=false；删除非活动项保留其他连接，删除活动项不自动启用替代模型。再次新增需提供Key，已保存的其他连接仍可按原PUT空Key方式显式启用。地址、模型和加密Key从当前账户连接记录移除，历史项目、聊天与任务快照保留；不调用模型、不撤销服务商Key。接口继续只返回配置元数据，不回显任何密钥。原GET/PUT/POST test保持兼容，无迁移。

### 2026-11-17：聊天按需取证兼容扩展

项目聊天SSE增加可选`context`事件，含`actions[{tool,status,evidenceCount}]`、`warnings:string[]`、`memoryUsed:boolean`。完成/历史消息增加可选同名对象，不改变原字段或URL；旧客户端可忽略。工具执行只支持search_code/read_code/related_code，账号、项目、revision从会话固定；非法或越权读取被拒绝，不返回目标内容。工具参数/完整输出不写入审计，过程展示为宿主摘要。达到预算或取证失败时带覆盖提示继续回答，最终模型失败仍走原FAILED协议。详情见CONTEXT_RESEARCH_IMPROVEMENTS.md。
