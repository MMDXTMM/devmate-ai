# DevMate AI 跨账号开发交接

更新时间：2026-08-06

本文件用于在 Codex 账号、会话或电脑切换后快速恢复开发上下文。账号切换时只执行一次 [历史恢复流程](ACCOUNT_HANDOFF_PROMPTS.md)，恢复完成后不重复粘贴提示词。

## 0. 30 秒恢复卡

| 项目 | 当前值 |
| --- | --- |
| 本机仓库 | `/Users/dengxintong/Documents/devmate-ai` |
| 主仓库 | `https://github.com/MMDXTMM/devmate-ai` |
| 公开评测仓库 | `https://github.com/MMDXTMM/devmate-review-benchmark` |
| 权威分支 | 远端 `main` |
| 开发基线 | PR #22，`main@3ca3959` |
| 本轮交付 | PR #23（`codex/auth-project-access`）：JWT 登录、Vue 会话、项目成员隔离和 429 可读失败处理 |
| 数据库 | H2 已从空库迁移到 V18；隔离 MySQL 26.7 已完成 V17→V18，健康检查与历史数据回读通过 |
| 测试基线 | 后端 130 项、前端 46 项、Benchmark Node 48 项和 Vue 生产构建全部通过 |
| 精确暂停点 | 认证与项目隔离闭环完成并通过隔离 MySQL；尚未执行真实 FIXED/AGENT canary，也未产生准确率结论 |
| 下一小任务 | 以项目理解和面试讲解为主，从“3 分钟项目介绍 → 认证与权限闭环 → 源码审查闭环”逐步训练；真实 AI canary 继续等待显式额度确认 |

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
| 认证与权限 | 完成第一版闭环 | BCrypt、JWT、Vue 会话、OWNER 事务、项目路径与列表隔离 |
| 源码导入 | 完成 | JGit 安全克隆、私有仓库只读凭证、任务状态、失败恢复 |
| 结构上下文 | 完成 | Java AST、配置脱敏、迁移 SQL 摘要、代码关系图 |
| Diff 与静态分析 | 完成 | JGit Diff、行到符号映射、PMD 和项目级确定性规则 |
| RAG | 完成工程闭环 | 关键词/关系图基线、固定评测、Embedding、向量与 Hybrid/RRF |
| AI 审查 | 完成工程闭环 | DashScope JSON 输出、Chunk 白名单、结构化 Finding、任务审计 |
| Tool Calling Agent | 完成工程闭环 | 四个只读 Tool、Qwen 多轮协议、限制与审计、Vue 调用链 |
| 审查反馈 | 完成第一版闭环 | Finding 最新反馈、项目归属校验、Vue 局部更新、无模型重跑 |
| 效果评测 | 完成数据、计算、Vue 工作台、样本、Git 历史、真实 Diff/标准答案与受控执行器 | 8 PASS、6 FULL、2 PARTIAL；7 条 `DEFECT`、1 条 `CLEAN` 已落库，真实 A/B 待完成 |

当前代码数据库版本为 V18。H2 已验证从空库执行 V1-V18；本机此前完成过 MySQL V12→V13 和原项目读取。2026-08-06 另用隔离空库 MySQL 26.7 完成 V1-V13 与 8 场景真实持久化验收：25 张表、8 个 `READY` 项目、8 个文档、46 个 Chunk、8 个成功导入任务和 16 个成功 Diff 任务。随后同一数据目录依次成功执行 V13→V14→V15→V16→V17→V18，应用健康为 `UP`。V15 的 `attempt_key`、V16 的向量复用字段与索引、V17 的 `reused_files` 以及 V18 的六个阶段耗时列均存在；9 条历史导入任务安全回填为 0，8 个项目、16 个成功 Diff 与 46 个 Chunk 保持可读。历史库没有 AI 审查任务或向量，因此没有相关旧行回填问题。系统安装的 3306 服务仍未监听，作为独立本机运维问题处理。

最终自动化基线在本轮全量测试后更新；不能用修改前的 `111/29/28` 代替当前证据。

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
- 标准答案证据校验：`src/main/java/com/devmate/review/service/ReviewEvaluationCaseService.java`
- 评测迁移：`src/main/resources/db/migration/V13__add_review_evaluation_schema.sql`、`V14__add_review_file_path_hash.sql`
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
- 受控 A/B 执行器：`benchmarks/review-fixtures/run-review-ab.mjs`
- A/B 执行器测试：`benchmarks/review-fixtures/run-review-ab.test.mjs`
- 付费请求关联迁移：`src/main/resources/db/migration/V15__add_ai_review_attempt_key.sql`
- 标准答案同步模式：`--reuse-imports --reuse-diffs --record-gold-cases`
- H2 运行报告：`target/benchmark-results/known-defects-v1-import-diff.json`（被 Git 忽略）
- MySQL 运行报告：`target/benchmark-results/known-defects-v1-mysql-import-diff.json`（被 Git 忽略）
- MySQL 标准答案报告：`target/benchmark-results/known-defects-v1-mysql-gold-cases*.json`（被 Git 忽略）

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

V13 的后端评测模型和 Vue 评测工作台均已完成。`known-defects-v1` 的 8 个 base/candidate 样本已发布，并分别使用 H2 与隔离 MySQL 26.7 完成真实公开 GitHub 导入和默认 Diff：8 个 import/project/candidate revision 和 Diff base/target revision 均与清单一致，两次结果均为 6 个 `FULL`、2 个 `PARTIAL`、0 个 `SKIPPED`。

`case-005` 是 TARGET 新增 import、`case-008` 是 BASE 删除 import 未进入当前 class/method Chunk。两者的业务方法和缺陷目标行仍有映射证据；后续应设计 `FILE_HEADER/IMPORT` Chunk，不能篡改覆盖状态。真实运行发生过 GitHub 瞬时克隆失败；失败分支单独恢复成功后，使用 `--reuse-imports` 要求 8 个最近任务全部成功并重新执行完整 Diff，最终全量复核通过。

MySQL 最终状态为 8 个项目 `READY`、8 个文档、46 个 Chunk、8 个成功导入任务和 16 个成功 Diff 任务；首次瞬时网络失败保留 1 条 `FAILED` 导入记录，重试后完整复核通过。标准答案创建强制校验目标 Diff 文件、目标变更行和持久化 TARGET Chunk 三重交集。manifest 已向对应 8 个 Diff 录入 7 条 `DEFECT` 和 1 条 `CLEAN`，首次同步为 `8 created / 8 verified`，立即重跑为 `0 created / 8 reused / 8 verified`；尚未调用 Embedding、FIXED、AGENT 或真实模型。

本轮已经补齐受控执行闭环。AI 创建请求必须携带 `reviewTaskId`、40 位 `revision` 和 UUID v4 `attemptKey`。模型解析前预检最近成功 Diff，创建任务的短事务内再次校验，漂移时返回 409 且不创建 AI 任务或调用日志。V15 将 `attemptKey` 持久化并建立唯一索引，响应丢失后可通过 `/ai-reviews/attempts/{attemptKey}` 精确回读，不再依赖 latest 猜测任务归属。

完整 A/B 顺序固定为：

```text
全批 8 项零模型预检
  → FIXED → 评测
  → AGENT → 评测
  → 下一场景
  → 汇总微平均、Token、延迟与 Tool 指标
```

执行器为同步多轮 Agent 提供可配置的长请求与恢复窗口，付费 POST 出现模糊响应时不重试，只按 `attemptKey` 轮询。失败报告只保存受控错误类别，不保存任意服务端文本、源码、Prompt 或凭证。Mock/Fake 已覆盖 Diff 漂移、并发同配置任务、AI/评测响应丢失、失败即停止、微平均聚合和失败文本脱敏；真实 canary 仍未执行。

## 6. 下一阶段：受控执行器与第一轮真实 A/B

阶段 8 的目标是证明效果，不继续堆框架。建议按以下小任务推进：

1. 已在 H2 和隔离 MySQL 完成公开仓库 8 个项目的源码导入、默认 `HEAD^ → HEAD` Diff 与证据核对。
2. 已强化 `ReviewEvaluationCaseService` 的写入约束，服务端会拒绝其他 Diff 文件、未命中目标变更以及没有共同 TARGET Chunk 证据的位置。
3. 已通过验收脚本为每个成功 Diff 录入 manifest 人工标准答案，不复制完整源码到评测表，并完成幂等重跑和全字段回读。
4. 已让两个 AI 创建入口接收预期 Diff ID/revision/attemptKey，并在任何模型调用前由服务端拒绝过期或漂移输入。
5. 已实现受控 A/B 执行器：全批零模型预检、每项目 FIXED→评测→AGENT→评测、精确响应丢失恢复、失败后停止继续消耗额度，以及 8 项总体报告。
6. 已使用 Mock/Fake 验证正常、漂移、失败、并发归属、恢复和脱敏路径，完成 V15 真实 MySQL 验收以及后端 120 项、前端 37 项、Benchmark Node 48 项和 Vue 生产构建；下一步提交 PR，再确认模型密钥和额度后运行 1 个真实 canary。通过后才在相同项目、revision、Diff、模型配置和数据集条件下完成 8 组真实 A/B。两条路径可以使用不同 Prompt 和检索流程，但必须冻结并分别记录各自版本。
7. 总体质量使用累计 TP/FP/FN 计算微平均 Precision/Recall/F1，不直接平均每个场景 F1；同时记录 Token、延迟、Tool 成功率和 manifest/revisions 哈希。
8. 人工复核 `partialMetrics` 中不能自动匹配的 Finding，并记录失败原因；之后才允许按失败案例调整 Tool、检索和 Prompt 版本。

阶段 8 完成前不能在简历中宣称准确率，也不能宣称 Agent 优于固定流水线。

## 7. 新账号一次性恢复

新账号不需要反复粘贴启动提示词。第一次进入仓库时执行 [一次性账号切换恢复流程](ACCOUNT_HANDOFF_PROMPTS.md)，输出恢复报告后直接继续本文件记录的下一小任务。旧聊天只在仓库缺少设计原因时定向读取一次，不能覆盖 Git、测试和数据库证据。

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
node --test benchmarks/review-fixtures/run-review-ab.test.mjs
./mvnw test

cd frontend
npm test -- --run
npm run build

# 需要重新做真实 MySQL 验收时，在仓库根目录执行
cd ..
node benchmarks/review-fixtures/verify-live-imports.mjs

# 确认密钥、额度和后端状态后才运行，不属于零模型回归
node benchmarks/review-fixtures/run-review-ab.mjs --scenario case-001
```

真实 MySQL 验证使用 `local` Profile；`application-local.yml` 被 Git 忽略。系统安装的 3306 服务仍需单独恢复，但隔离 MySQL 已完成项目链路验收。真实模型测试还需要在进程环境配置 `DASHSCOPE_API_KEY`，当前阶段没有用 Mock 结果冒充真实模型效果。

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
- 客户端不能自报 `userId`；需要操作人时必须读取服务端认证上下文。当前反馈仍采用单一最新记录，尚未扩展多评审人。
- 真实模型或 Embedding 返回 429 时要保存失败状态并停止，不自动重试付费请求；Codex 账号自身的 429 只能等待额度恢复或切换账号。
- AI 可以高效生成模板和测试初稿，但开发者必须主导状态机、事务、安全、评测和技术取舍，并能独立解释和修改核心链路。
- A/B 看板不能自行选择执行模式；模式必须来自已落库任务，否则前端标签可以伪造实验条件。
- 评测页面打开和加载数据不应产生模型费用；模型运行与指标计算是两个显式步骤，便于重试、审计和控制额度。
- 固定缺陷集必须把“样本代码”和“人工标准答案”分离；目标源码内不能放泄漏答案的 Bug 标记，标准答案必须自动校验确实覆盖目标 Diff。
- 可复现 Git 样本不仅要固定文件，还要固定父提交、作者、时间和消息；远端分支 HEAD 必须与 revision 清单核对，防止强制推送造成数据集漂移。
- fixture 契约测试通过不代表真实导入链路可用；必须同时核对任务、项目 revision、Diff 双方 SHA、变更行和 candidate 证据。
- GitHub 瞬时失败允许单场景重试排障，但最终验收必须重跑完整数据集，不能拼接多次局部成功。
- `--reuse-imports` 只复核最近的 `SUCCEEDED` 导入；未传 `--reuse-diffs` 时才创建新 Diff，传入后会复用并严格校验最近 Diff。缺失、失败或 revision 漂移仍会失败，报告必须保留导入和 Diff 模式。
- `PARTIAL` 可能来自 BASE 或 TARGET 文件头；应记录未映射行并补充专用 Chunk，不能通过扩大类范围伪造 `FULL`。
