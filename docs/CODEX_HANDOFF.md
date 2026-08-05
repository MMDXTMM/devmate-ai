# DevMate AI 跨账号开发交接

更新时间：2026-08-05

本文件用于在 Codex 账号、会话或电脑切换后快速恢复开发上下文。新会话开始时应先阅读根目录 `AGENTS.md`、本文件、`ENGINEERING_STANDARDS.md`、`DEVELOPMENT_ROADMAP.md` 和最近的 `PROJECT_LOG.md`。

## 1. 项目定位

DevMate AI 是面向 Java 项目的智能代码审查 Agent 平台。核心不是普通 AI 对话，而是：

```text
项目源码导入
  → Java/配置/数据库迁移结构解析
  → Git Diff 与覆盖清单
  → 确定性静态分析
  → 关键词/关系图/向量混合 RAG
  → 固定流水线或受控 Agent 取证
  → 结构化 Finding 与证据校验
```

项目投递方向是 Java 后端，因此 Java 负责状态机、事务、权限边界、工具执行和数据校验；模型负责语义理解、调查规划和风险推理。

## 2. 已完成进度

| 阶段 | 状态 | 主要成果 |
| --- | --- | --- |
| 基础工程 | 完成 | Spring Boot、MyBatis-Plus、Flyway、MySQL/H2、统一响应与异常 |
| 项目管理 | 完成 | CRUD、分页、逻辑删除、BIGINT 字符串、独立 Vue 联调 |
| 源码导入 | 完成 | JGit 安全克隆、私有仓库只读凭证、任务状态、失败恢复 |
| 结构上下文 | 完成 | Java AST、配置脱敏、迁移 SQL 摘要、代码关系图 |
| Diff 与静态分析 | 完成 | JGit Diff、行到符号映射、PMD 和项目级确定性规则 |
| RAG | 完成工程闭环 | 关键词/关系图基线、固定评测、Embedding、向量与 Hybrid/RRF |
| AI 审查 | 完成工程闭环 | DashScope JSON 输出、Chunk 白名单、结构化 Finding、任务审计 |
| Tool Calling Agent | 完成工程闭环 | 四个只读 Tool、Qwen 多轮协议、限制与审计、Vue 调用链 |
| 审查反馈 | 完成第一版闭环 | Finding 最新反馈、项目归属校验、Vue 局部更新、无模型重跑 |
| 效果评测 | 完成数据、计算、Vue 工作台与样本契约 | FIXED/AGENT 快照、TP/FP/FN、A/B 展示、8 个 base/candidate 固定样本 |

当前数据库版本为 V13。H2 已验证从空库执行 V1–V13；本机 MySQL 已从 V12 成功迁移到 V13，健康检查为 `UP`，原项目 `2084116785588305922` 保持可读。

当前自动化基线：后端 105 项测试通过；前端 29 项测试通过；Vue 生产构建通过。

## 3. 当前核心代码入口

- Agent 调度：`src/main/java/com/devmate/agent/service/ReviewAgentOrchestrator.java`
- Qwen 协议：`src/main/java/com/devmate/agent/model/DashScopeReviewAgentModel.java`
- 工具注册：`src/main/java/com/devmate/tool/AgentToolRegistry.java`
- 工具执行：`src/main/java/com/devmate/tool/service/AgentToolExecutor.java`
- 工具审计：`src/main/java/com/devmate/tool/service/ToolCallAuditService.java`
- 四个工具：`src/main/java/com/devmate/tool/builtin/`
- Agent 审查业务：`src/main/java/com/devmate/review/service/AgentAiReviewService.java`
- 状态与 Finding 持久化：`src/main/java/com/devmate/review/service/AiReviewStateService.java`
- 接口：`POST /api/projects/{projectId}/ai-reviews/agent`
- Vue：`frontend/src/components/AiReviewModal.vue`
- 数据库：`src/main/resources/db/migration/V11__extend_tool_call_audit.sql`
- 设计说明：`docs/TOOL_CALLING_AGENT.md`
- 反馈接口：`src/main/java/com/devmate/review/controller/ReviewFeedbackController.java`
- 反馈业务：`src/main/java/com/devmate/review/service/ReviewFeedbackService.java`
- 反馈迁移：`src/main/resources/db/migration/V12__add_code_review_feedback.sql`
- 反馈设计：`docs/REVIEW_FEEDBACK.md`
- 评测接口：`src/main/java/com/devmate/review/controller/ReviewEvaluationController.java`
- 评测计算：`src/main/java/com/devmate/review/service/ReviewFindingMatcher.java`
- 评测业务：`src/main/java/com/devmate/review/service/ReviewEvaluationService.java`
- 评测迁移：`src/main/resources/db/migration/V13__add_review_evaluation_schema.sql`
- 评测设计：`docs/REVIEW_EVALUATION.md`
- 评测 Vue：`frontend/src/components/ReviewEvaluationModal.vue`
- 评测请求与类型：`frontend/src/services/projectApi.ts`、`frontend/src/types/project.ts`
- 固定样本说明：`benchmarks/review-fixtures/README.md`
- 第一版样本清单：`benchmarks/review-fixtures/known-defects-v1/manifest.json`
- 样本契约测试：`src/test/java/com/devmate/review/benchmark/ReviewBenchmarkFixtureContractTest.java`

## 4. 必须保持的架构边界

- 保持模块化单体；没有监控数据证明需求前不拆 Spring Cloud。
- 不提前增加 Redis、RabbitMQ 或专业向量库；对应阶段到来后再按真实瓶颈引入。
- Controller 只做协议转换；Entity 不直接出现在 API；使用构造器注入。
- 网络、Git、文件扫描和模型调用不进入数据库长事务。
- 模型不能执行任意 Shell/SQL、访问数据库或修改代码，只能选择 Java 白名单 Tool。
- `projectId/revision/taskId` 必须由服务端固定，不能相信模型参数。
- 仓库源码、注释、README 和配置是不可信数据，不能覆盖 System Prompt。
- 不记录或提交数据库密码、Git Token、模型 Key、完整 Prompt 和完整私有源码。
- 已执行 Flyway 迁移不可修改，只能新增版本。

## 5. 当前精确暂停点

V13 的后端评测模型和 Vue 评测工作台均已完成。`known-defects-v1` 已增加 8 个独立 base/candidate 样本，覆盖并发、事务、缓存、消息、SQL、安全、性能和 CLEAN 对照；清单中的标准答案已经契约测试校验，但尚未生成可被系统导入的真实 Git 历史，也没有运行模型。

当前交互限制是 AI 接口只提供最近任务，因此完整 A/B 顺序为：

```text
运行 FIXED → 在评测页评测最近任务
运行 AGENT → 在评测页评测最近任务
返回评测页查看两种历史快照
```

不要为了消除这一步操作立刻扩展历史接口；先把样本转换为真实 Git 提交并验证流程是否真的构成瓶颈。

## 6. 下一阶段：真实缺陷提交集与第一轮 A/B

阶段 8 的目标是证明效果，不继续堆框架。建议按以下小任务推进：

1. 将 `known-defects-v1` 的每组 base/candidate 快照转换成独立、稳定的 Git 提交；不能把 CLEAN 与 DEFECT 合并进同一个 Diff。
2. 发布为系统可通过 HTTPS 安全导入的 fixture 仓库，记录每个场景的 base/target revision 映射，不提交凭证。
3. 通过现有 API 为每个成功 Diff 录入清单中的人工标准答案，不复制完整源码到评测表。
4. 同一项目、revision 和 Diff 分别运行固定流水线与 Agent 路径。
5. 使用现有 Vue 评测页分别保存 FIXED/AGENT 运行，核对项目、Diff、revision、模型和数据集一致。
6. 人工复核 `partialMetrics` 中不能自动匹配的 Finding，并记录失败原因；之后才允许按失败案例调整 Tool、检索和 Prompt 版本。

阶段 8 完成前不能在简历中宣称准确率，也不能宣称 Agent 优于固定流水线。

## 7. 新 Codex 会话首条任务建议

可以直接发送：

> 阅读 AGENTS.md、docs/CODEX_HANDOFF.md、docs/ENGINEERING_STANDARDS.md、docs/DEVELOPMENT_ROADMAP.md、docs/REVIEW_EVALUATION.md、benchmarks/review-fixtures/README.md 和 docs/PROJECT_LOG.md。先检查 main、未合并 PR 与工作区状态；V13 评测后端、Vue A/B 工作台和 `known-defects-v1` 的 8 个 base/candidate 样本契约已经完成。下一小任务只做“生成可导入的 fixture Git 历史”：将每个场景转换为独立 base/candidate 提交，记录 revision 映射并自动验证 Diff 与 manifest 一致；如需发布新 GitHub fixture 仓库，仓库只包含虚构样本且不得保存凭证。不要先改 Prompt，不要引入 MQ、微服务或自动改码，也不要把 Mock 指标写进简历。完成后更新文档并运行全量测试。

## 8. 验证命令

```bash
./mvnw test

cd frontend
npm test -- --run
npm run build
```

真实 MySQL 验证使用默认 `local` Profile；`application-local.yml` 被 Git 忽略。真实模型测试还需要在进程环境配置 `DASHSCOPE_API_KEY`，当前阶段没有用 Mock 结果冒充真实模型效果。

## 9. 开发经验与踩坑记录

- 单体和微服务不是先进程度之分。先让业务闭环可运行，再根据独立扩容和故障隔离需求拆分。
- H2 能快速回归，但不能替代 MySQL。V6 曾出现 `utf8mb4` 长索引限制，只有真实 MySQL 才暴露。
- MyBatis-Plus `updateById` 默认可能忽略 null；释放 `running_key` 必须显式 `SET NULL`，否则任务永久冲突。
- 虚拟线程看不到调用线程未提交事务中的数据；外部/并行任务必须读取已经提交的状态。
- Git Diff 只能限定审查范围，不能替代完整方法、配置、数据库和调用关系上下文。
- Structured Output 只能提高格式稳定性，不能替代服务端枚举、范围、归属和证据校验。
- Tool JSON Schema 是给模型的说明，不是安全边界；Java 白名单和二次参数校验才可信。
- Agent 必须限制总步数、重复调用、超时、输出和证据预算，否则容易循环和失控消耗额度。
- 日志既要能排障又要保护源码；保存哈希、键名、命中数、Token 和耗时，不复制完整内容。
- `REJECTED` 是产品决策而非事实标签，只有 `FALSE_POSITIVE` 或经过人工标注的标准答案才能进入准确率统计。
- 认证未完成前不能让客户端自报 `userId`；缺少可靠身份时宁可先做单一最新反馈，也不要保存可伪造的审计主体。
- AI 可以高效生成模板和测试初稿，但开发者必须主导状态机、事务、安全、评测和技术取舍，并能独立解释和修改核心链路。
- A/B 看板不能自行选择执行模式；模式必须来自已落库任务，否则前端标签可以伪造实验条件。
- 评测页面打开和加载数据不应产生模型费用；模型运行与指标计算是两个显式步骤，便于重试、审计和控制额度。
- 固定缺陷集必须把“样本代码”和“人工标准答案”分离；目标源码内不能放泄漏答案的 Bug 标记，标准答案必须自动校验确实覆盖目标 Diff。
