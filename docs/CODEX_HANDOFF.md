# DevMate AI 跨账号开发交接

更新时间：2026-08-06

本文件用于在 Codex 账号、会话或电脑切换后快速恢复开发上下文。它记录“现在做到哪里”，可直接复制的启动提示词统一放在 [跨账号启动提示词](ACCOUNT_HANDOFF_PROMPTS.md)。

## 0. 30 秒恢复卡

| 项目 | 当前值 |
| --- | --- |
| 本机仓库 | `/Users/dengxintong/Documents/devmate-ai` |
| 主仓库 | `https://github.com/MMDXTMM/devmate-ai` |
| 公开评测仓库 | `https://github.com/MMDXTMM/devmate-review-benchmark` |
| 权威分支 | 远端 `main` |
| 最近合并 | PR #15，`main@5e6ea09`；当前分支 `codex/live-benchmark-import-verification` |
| 数据库 | Flyway V13 历史已验收；本轮 MySQL 3306 未监听，H2 真实外部链路已验收 |
| 测试基线 | 后端 107 项；前端 29 项；Node 16 项；Vue 生产构建通过 |
| 精确暂停点 | 8 个真实项目已导入并完成 Diff：6 FULL、2 PARTIAL；未录标准答案、未调用模型 |
| 下一小任务 | 恢复 MySQL 后复跑 8 场景，再录入并复核 manifest 人工标准答案 |

### 状态可信度顺序

恢复上下文时按以下顺序判断，后者不能覆盖前者：

1. 远端 `main`、已合并 PR 和自动化测试结果。
2. 数据库 Flyway 版本与真实运行结果。
3. 本文件、相关设计文档和 `PROJECT_LOG.md`。
4. ChatGPT/Codex 历史对话与人工口述。

如果对话声称“已完成”，但仓库、测试或数据库没有证据，应视为未验证，不继续包装到 README 或简历。

### 新账号首次检查

新账号不要立即改代码，先完成：

```bash
pwd
git status -sb
git log -5 --oneline --decorate
git fetch origin
```

然后检查远端 `main`、未合并 PR、工作区修改和最近测试记录。若当前分支已合并，先同步 `main` 再创建 `codex/<task-name>` 分支；不得覆盖未识别的用户修改。

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
| 效果评测 | 完成数据、计算、Vue 工作台、样本、Git 历史与 H2 真实 Diff 验收 | 8 PASS、6 FULL、2 PARTIAL；标准答案落库和真实 A/B 待完成 |

当前数据库版本为 V13。H2 已验证从空库执行 V1–V13；本机 MySQL 此前已从 V12 成功迁移到 V13，并验证原项目 `2084116785588305922` 可读，但本轮 3306 未监听，因此 8 场景持久化复验仍待服务恢复后补做。

当前自动化基线：后端 107 项、前端 29 项、真实验收工具 16 项 Node 测试通过；Vue 生产构建通过。

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
- 确定性 revision：`benchmarks/review-fixtures/known-defects-v1/revisions.json`
- 样本契约测试：`src/test/java/com/devmate/review/benchmark/ReviewBenchmarkFixtureContractTest.java`
- Git 历史生成器：`src/test/java/com/devmate/review/benchmark/ReviewBenchmarkRepositoryBuilder.java`
- Git 历史测试：`src/test/java/com/devmate/review/benchmark/ReviewBenchmarkGitHistoryTest.java`
- 公开样本仓库：`https://github.com/MMDXTMM/devmate-review-benchmark`
- 真实导入验收：`benchmarks/review-fixtures/verify-live-imports.mjs`
- 验收工具测试：`benchmarks/review-fixtures/verify-live-imports.test.mjs`
- 本地运行报告：`target/benchmark-results/known-defects-v1-import-diff.json`（被 Git 忽略）

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

V13 的后端评测模型和 Vue 评测工作台均已完成。`known-defects-v1` 的 8 个 base/candidate 样本已发布，并使用 H2 `test` Profile 和真实公开 GitHub 仓库完成导入与默认 Diff：8 个 import/project/candidate revision 和 Diff base/target revision 均与清单一致，结果为 6 个 `FULL`、2 个 `PARTIAL`、0 个 `SKIPPED`。

`case-005` 是 TARGET 新增 import、`case-008` 是 BASE 删除 import 未进入当前 class/method Chunk。两者的业务方法和缺陷目标行仍有映射证据；后续应设计 `FILE_HEADER/IMPORT` Chunk，不能篡改覆盖状态。真实运行发生过 GitHub 瞬时克隆失败；失败分支单独恢复成功后，使用 `--reuse-imports` 要求 8 个最近任务全部成功并重新执行完整 Diff，最终全量复核通过。

本轮尚未向 V13 录入 manifest 标准答案，也未调用 Embedding、FIXED 或 AGENT。H2 证明外部 Git、解析和 Diff 链路可用，但本机 MySQL 当前未监听，真实持久化环境仍需复验。

当前交互限制是 AI 接口只提供最近任务，因此完整 A/B 顺序为：

```text
运行 FIXED → 在评测页评测最近任务
运行 AGENT → 在评测页评测最近任务
返回评测页查看两种历史快照
```

不要为了消除这一步操作立刻扩展历史接口；先使用已经发布的分支跑通真实评测，验证流程是否真的构成瓶颈。

## 6. 下一阶段：真实缺陷提交集与第一轮 A/B

阶段 8 的目标是证明效果，不继续堆框架。建议按以下小任务推进：

1. 已在 H2 完成公开仓库 8 个项目的源码导入、默认 `HEAD^ → HEAD` Diff 与证据核对。
2. 恢复本机 MySQL 后运行同一验收工具，确认 V13 真实持久化链路仍为 8 PASS、6 FULL、2 PARTIAL。
3. 通过现有 API 为每个成功 Diff 录入 manifest 中的人工标准答案，不复制完整源码到评测表，并核对文件属于 Diff、行范围与目标变更及 TARGET 证据相交。
4. 同一项目、revision 和 Diff 分别运行固定流水线与 Agent 路径。
5. 使用现有 Vue 评测页分别保存 FIXED/AGENT 运行，核对项目、Diff、revision、模型和数据集一致。
6. 人工复核 `partialMetrics` 中不能自动匹配的 Finding，并记录失败原因；之后才允许按失败案例调整 Tool、检索和 Prompt 版本。

阶段 8 完成前不能在简历中宣称准确率，也不能宣称 Agent 优于固定流水线。

## 7. 新 Codex 会话首条任务建议

完整 Codex、ChatGPT 和紧急精简提示词见 [跨账号启动提示词](ACCOUNT_HANDOFF_PROMPTS.md)。当前阶段最短可用版本：

> 阅读 AGENTS.md、docs/CODEX_HANDOFF.md、docs/ENGINEERING_STANDARDS.md、docs/DEVELOPMENT_ROADMAP.md、docs/REVIEW_EVALUATION.md、benchmarks/review-fixtures/README.md、manifest.json、revisions.json 和 docs/PROJECT_LOG.md。先检查 main、未合并 PR、工作区和 MySQL 状态。H2 + 真实 GitHub 已验收 8 PASS、6 FULL、2 PARTIAL；下一小任务先在恢复后的 MySQL 用 `verify-live-imports.mjs` 复验，再把 manifest 人工标准答案录入对应 Diff 并核对 TARGET 证据。禁止先调用模型、修改 Prompt、把 PARTIAL 改成 FULL 或宣称准确率。

## 8. 账号切换前的收尾要求

旧账号结束前必须：

1. 把可验证修改提交到独立分支并推送；能安全合并时完成 PR，否则记录 PR 地址和阻塞原因。
2. 更新本文件的恢复卡、精确暂停点、测试基线和下一小任务。
3. 在 `PROJECT_LOG.md` 记录业务、架构或开发顺序变化。
4. 只记录所需环境变量名称，不写值；不得复制密码、Token、Cookie、API Key 或私有源码。
5. 写清未完成操作、外部仓库状态、数据库迁移版本、真实验收与 Mock 测试的区别。

新账号开始后必须以仓库证据复核交接，不把旧对话直接当作事实。交接只传递开发上下文，不传递账号订阅、记忆、凭证或权限。

## 9. 验证命令

```bash
node --test benchmarks/review-fixtures/verify-live-imports.test.mjs
./mvnw test

cd frontend
npm test -- --run
npm run build

# 本机 MySQL 与后端恢复后，在仓库根目录执行
cd ..
node benchmarks/review-fixtures/verify-live-imports.mjs
```

真实 MySQL 验证使用默认 `local` Profile；`application-local.yml` 被 Git 忽略。真实模型测试还需要在进程环境配置 `DASHSCOPE_API_KEY`，当前阶段没有用 Mock 结果冒充真实模型效果。

## 10. 开发经验与踩坑记录

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
- 可复现 Git 样本不仅要固定文件，还要固定父提交、作者、时间和消息；远端分支 HEAD 必须与 revision 清单核对，防止强制推送造成数据集漂移。
- fixture 契约测试通过不代表真实导入链路可用；必须同时核对任务、项目 revision、Diff 双方 SHA、变更行和 candidate 证据。
- GitHub 瞬时失败允许单场景重试排障，但最终验收必须重跑完整数据集，不能拼接多次局部成功。
- `--reuse-imports` 只复核最近的 `SUCCEEDED` 导入并重新创建 Diff；缺失、失败或 revision 漂移仍会失败，报告必须保留导入模式。
- `PARTIAL` 可能来自 BASE 或 TARGET 文件头；应记录未映射行并补充专用 Chunk，不能通过扩大类范围伪造 `FULL`。
