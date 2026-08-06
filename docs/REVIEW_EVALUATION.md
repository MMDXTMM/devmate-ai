# 代码审查固定缺陷集与评测设计

## 1. 目标

阶段 8 的评测不是证明模型“会输出文本”，而是使用固定标准答案比较固定流水线和 Agent 在同一 Diff 上的实际表现。每次运行必须绑定项目、Diff、revision、执行模式、模型、Prompt、检索配置和数据集哈希，确保结果可以复现。

评测计算接口本身不调用模型，只评估已经成功完成的 `ai_review_task`。FIXED/AGENT A/B 会先显式运行两条真实审查路径，再分别调用评测接口；这样评测失败不会重复消耗 Token，也不会把模型调用和指标计算混在同一个事务中。

## 2. 数据集用例

每条 `review_evaluation_case` 绑定：

- 项目和成功的 `code_review_task`；
- 数据集版本和稳定的 `case_key`；
- 该 Diff 的目标 revision；
- `DEFECT` 或 `CLEAN` 期望类型；
- 缺陷类别、相对文件路径和目标行范围；
- 可读名称和人工标注依据。

`DEFECT` 必须提供类别、路径和合法行范围。`CLEAN` 表示整个 Diff 是无缺陷对照，不允许再提供缺陷位置。同一数据集版本和同一个 Diff 不能同时存在 `CLEAN` 与 `DEFECT`，避免标准答案自相矛盾。

数据集产生第一条运行记录后即冻结，不允许再追加或原地修改用例。标准答案变化时创建新版本，例如 `known-defects-v2`，而不是覆盖旧数据并让历史指标失去含义。

## 3. Finding 匹配规则

一条 Finding 只有同时满足以下条件才是某个标准缺陷的候选：

1. `category` 完全相同；
2. 规范化后的相对文件路径相同；
3. Finding 行范围与标准答案行范围存在交集。

不使用标题或建议文本完全相等，因为不同模型可以用不同语言描述同一个风险。

候选关系按二分图处理：

- 标准缺陷只有一个候选，且该 Finding 也只对应一个标准缺陷：`TRUE_POSITIVE`；
- 标准缺陷没有候选：`FALSE_NEGATIVE`；
- Finding 没有候选：`FALSE_POSITIVE`；
- 任意一侧存在多个候选：`MANUAL_REVIEW`，不强行分配匹配关系。

歧义项不进入自动 Precision/Recall 分母，并通过 `partialResult=true` 明确提示当前指标不完整。后续人工复核功能可以把歧义项固定为标准匹配，再重新计算完整指标。

## 4. 指标语义

- `precision = TP / (TP + FP)`；
- `recall = TP / (TP + FN)`；
- `f1 = 2 * precision * recall / (precision + recall)`；
- 无缺陷对照且无 Finding 时三项均记为 1；
- 无缺陷对照出现 Finding 时 Precision 和 F1 为 0，Recall 为 1；
- 存在歧义候选时指标标记为部分结果，不能作为最终简历数据。

运行同时快照模型、Prompt、检索配置、执行模式、Token、耗时和工具成功率。固定流水线与 Agent 只有在项目、Diff、revision、数据集版本和模型配置一致时才适合横向比较。

## 5. V13 数据关系

```text
code_review_task
       │
       ├── review_evaluation_case
       │        └── 固定标准答案
       │
       └── ai_review_task
                ├── review_finding
                └── review_evaluation_run
                         └── result_json（仅 ID、结果和原因，不保存源码）
```

V13 同时为 `ai_review_task` 增加 `execution_mode=FIXED/AGENT`。模式由后端创建审查任务时写入，评测请求不能自行指定或覆盖。

`review_evaluation_run` 使用 `(ai_review_task_id, dataset_hash)` 唯一键保证幂等。数据集新增用例后哈希变化，可以生成新的运行快照；完全相同的数据和 AI 结果重复提交时返回已有结果。

## 6. API 与失败路径

### 创建标准用例

`POST /api/projects/{projectId}/review-evaluation-cases`

失败路径：

- 项目或 Diff 任务不存在、任务不属于项目：404；
- Diff 尚未成功：400；
- 同一数据集用例键重复：409；
- 数据集已经产生评测运行后继续追加用例：409，必须创建新版本；
- `CLEAN/DEFECT` 字段组合错误、路径逃逸、行范围非法：400；
- DEFECT 文件不属于指定 Diff 的目标版本，或标注范围未命中目标变更行：400；
- DEFECT 标注范围、目标变更行和持久化 `TARGET` Chunk 映射没有共同交集：400；
- 同一 Diff 同一数据集混用 `CLEAN` 和 `DEFECT`：409。

DEFECT 位置校验只使用 `newPath/changedLines`，不会用删除文件的 `oldPath/baseChangedLines` 冒充目标版本证据。`revisionSide=TARGET` 且 `chunkId` 为正数表示该符号在生成 Diff 时来自已持久化 Chunk；BASE 符号来自历史源码的内存解析，`chunkId` 为空，不能作为目标版本标准答案证据。文件整体为 `PARTIAL` 时，只要缺陷行本身满足三重交集仍允许创建。

### 查询标准用例

`GET /api/projects/{projectId}/review-evaluation-cases?datasetVersion=...&reviewTaskId=...`

`reviewTaskId` 可省略。带该参数时查询指定 Diff 的启用用例，供评测工作台使用；省略时查询项目和数据集下跨 Diff 的启用用例，供批量录入工具在 V13 唯一键范围内发现旧 Diff 漂移。

当前没有禁用标准用例的 API，数据集级查询只覆盖系统管理的启用记录，并沿用单次评测用例上限。若后续增加禁用/恢复或让同一项目的数据集跨大量 Diff，必须增加分页或专用唯一键冲突查询；不能继续把该便捷查询当作完整数据库约束视图。

### 执行评测

`POST /api/projects/{projectId}/review-evaluation-runs`

请求只提供 `datasetVersion` 和 `aiReviewTaskId`。服务端校验 AI 任务成功、项目归属、Diff 与 revision 一致，并读取真实执行模式和调用指标。

### 创建受控 AI 审查

`POST /api/projects/{projectId}/ai-reviews`

`POST /api/projects/{projectId}/ai-reviews/agent`

两个入口都必须显式提交 `{reviewTaskId, revision, attemptKey}`。服务端在解析模型和创建调用记录前，核对请求绑定的是该项目最新成功 Diff 及其目标 revision；任一字段漂移都拒绝执行，不能再由服务端静默改用另一个“最近任务”。`attemptKey` 只关联和恢复单次付费请求，执行模式仍由 FIXED/AGENT 入口决定，客户端不能在请求体中伪造。

### 查询运行结果

`GET /api/projects/{projectId}/review-evaluation-runs?datasetVersion=...&reviewTaskId=...`

返回最近 100 条稳定排序的运行快照，供 Vue A/B 看板展示。

## 7. Vue 评测工作台

项目列表的“评测”入口自动读取最近成功 Diff、最近 AI 审查、当前数据集用例和历史运行。用户可以：

1. 选择符合 `[A-Za-z0-9._-]+` 的数据集版本；
2. 录入 `DEFECT` 的类别、相对路径、行范围和人工依据，或录入不带缺陷字段的 `CLEAN` 对照；
3. 对最近一次已持久化的 AI 审查执行评测；
4. 并排查看最近 FIXED/AGENT 的质量、成本与 Tool 指标。

工作台不会调用模型，也不允许请求携带执行模式。运行 FIXED 或 AGENT 时，前端从当前成功 Diff 读取字符串 `reviewTaskId` 和目标 `revision` 并显式传给后端；后端再次校验绑定，避免页面状态与服务端最近 Diff 发生竞态。

人工单次操作仍可按“运行 FIXED → 评测 → 运行 AGENT → 评测”完成。完整 8 项付费实验使用独立受控执行器，以统一处理全批预检、顺序、结果可比性、响应丢失和失败停止，不能依赖手工连续点击。

## 8. 事务和安全边界

- 用例创建和评测结果落库使用短事务；
- 指标计算只读取数据库，不调用 Git、文件、模型或 Shell；
- 结果 JSON 只保存用例 ID、Finding ID、匹配结果和可读原因，不保存完整源码、Prompt 或模型响应；
- BIGINT ID 对前端始终序列化为字符串；
- 项目、Diff、AI 任务和 Finding 归属全部由服务端验证。
- 标准答案绑定已保存的 Diff 证据快照，不在用例创建事务中重新访问 Git、文件系统或模型。
- V14 通过 `(review_task_id, new_path_hash)` 索引保持 Git 路径大小写语义；历史 V13 空哈希行回退为 Java 精确比较。Diff 的 TARGET 证据生成也按已有 `knowledge_document.path_hash` 查询并复核完整路径，避免先生成错误 Chunk 快照再被标准答案校验接受。
- 批量工具不跨 8 个 HTTP 请求持有数据库长事务。它先全批预检，消除可提前发现的部分写入；应用阶段依靠唯一键、精确回读和可恢复重跑处理响应丢失或中途失败。
- 每次付费 POST 使用新的 UUID v4 `attemptKey`。V15 将其持久化并建立唯一索引；POST 响应不明确时只按 attempt 路径轮询回读，不重复 POST。恢复结果还必须匹配项目、Diff、静态分析、revision、模式和模型，latest 或并发同配置任务不能作为本次证据。
- 执行器仍不自动跨进程续跑完整实验。进程退出后可以用报告中的 `attemptKey` 人工核对已提交任务，但没有服务端批次状态机时，程序不能证明整个 FIXED/AGENT 顺序和所有评测步骤已经连续完成，因此再次运行属于新的显式实验。

## 9. 测试方案

- 创建缺陷用例和无缺陷用例；
- 拒绝重复键、非法路径、反向行范围、跨项目任务、属于其他 Diff 的文件、rename 旧路径和重复目标路径；
- 拒绝未命中目标变更行、只有 BASE 证据、TARGET Chunk ID 无效以及没有三重交集的用例；
- 允许有缺陷行 TARGET 证据的 `PARTIAL` 文件，避免把文件头缺口扩大成整文件不可评测；
- 一次评测同时产生 TP、FP、FN，并验证 Precision/Recall/F1；
- 多候选进入人工复核且指标标记为部分结果；
- 无缺陷且无 Finding 得到完整通过；
- 相同 AI 任务和数据集哈希重复执行幂等返回；
- manifest 请求字段完整镜像后端 DTO 约束，后一个非法条目也必须在整批零 POST 时失败；
- Import/Diff 任一失败时标准答案零 POST；已有用例的 revision、类别、路径、行号、依据、重复或额外 caseKey 漂移均在全批预检失败；
- 首次只补录缺失用例，POST 响应丢失后可回读恢复，立即重跑为零创建且全部复用；
- A/B 全批预检在任意项目、Diff、Gold Case 或静态分析漂移时保持零次 AI POST；
- A/B 严格执行每个场景的 `FIXED → 评测 → AGENT → 评测`，检查两条结果的项目、Diff、revision、provider、model 和 dataset hash，并允许各自保留不同 Prompt/检索版本；
- A/B 的 AI 响应丢失只接受 `attemptKey` 精确回读，评测响应丢失按唯一 AI 任务与数据集回读；任一失败停止后续场景，单场景 canary 不会被标记为完整 8 场景报告；
- A/B 总体质量按汇总 TP/FP/FN 计算微平均 Precision/Recall/F1，同时汇总 Token、耗时和 Tool 调用；`partialMetrics=true` 不进入最终可比结果；
- H2 从空库执行 V1–V15；真实 MySQL 已验证 V13→V14→V15 与历史项目、Diff 兼容，V15 列和唯一索引均已回读确认。

2026-08-06 最终验证结果：后端 120 项、前端 37 项、真实验收与 A/B 工具共 48 项 Node 测试和 Vue 生产构建全部通过；其中受控 A/B 执行器为 20 项零模型 Fake Client 测试。隔离 MySQL 26.7 先在 V13 完成 25 张表和 8 场景真实持久化验收，随后包含 16 个历史 Diff 文件的数据目录成功迁移到 V14，再原地迁移到 V15；应用健康为 `UP`，`attempt_key` 列和唯一索引均存在，8 个项目与 16 个成功 Diff 保持可读。隔离 MySQL 临时表实测确认大小写不敏感 `file_path` 会命中两个变体，而 SHA-256 只命中正确原始路径。本轮没有调用真实模型。系统安装的 3306 服务仍未监听，属于独立的本机运维问题。

## 10. 第一版真实缺陷样本契约

`benchmarks/review-fixtures/known-defects-v1` 保存第一版固定样本。每个场景使用独立 `base/candidate` 快照，后续生成一个独立 Git Diff；这是因为同一 Diff 的 V13 数据集不能同时包含 `CLEAN` 和 `DEFECT`。目标目录使用 `candidate` 而不是 `target`，避免被 Maven 构建目录的 Git 忽略规则排除。

当前覆盖：

- `CONCURRENCY`：读取、判断、写回库存导致并发丢失更新；
- `TRANSACTION`：带事务方法的同类自调用绕过 Spring 代理；
- `CACHE`：热点缓存未命中时缺少并发回源保护；
- `MESSAGE`：消息确认早于业务持久化；
- `SQL`：循环内逐条查询形成 N+1；
- `SECURITY`：不可信文件名导致路径穿越；
- `PERFORMANCE`：持有 JVM 锁执行远程调用；
- `CLEAN`：从逐条查询重构为批量查询的无缺陷对照。

`manifest.json` 是人工标准答案源，行号绑定 candidate 快照。自动契约测试会拒绝重复键、路径逃逸、越界行号、未发生真实变更的快照，以及没有覆盖目标变更行的标准答案。该目录产生真实运行后冻结；修改标准答案必须创建新版本。

当前已完成样本定义、自动校验、确定性 Git 历史、公开仓库发布、8 个场景的真实导入/Diff 验收，以及 7 条 `DEFECT`、1 条 `CLEAN` 人工标准答案的 MySQL 录入与幂等回读；尚未产生真实 FIXED/AGENT 指标。

## 11. 可复现 Git 历史

样本已发布到 `https://github.com/MMDXTMM/devmate-review-benchmark.git`。每个场景映射到不携带缺陷语义的 `case-NNN` 分支：

```text
main 根提交
  └── base revision
        └── candidate revision（分支 HEAD）
```

因此系统使用默认 `HEAD^ → HEAD` 即可得到单场景 Diff。`revisions.json` 记录 base 和 candidate SHA；生成器固定文件树、父提交、作者、提交者、时间和消息，重复生成必须得到相同 SHA。

自动测试还验证：

- 8 个分支名唯一且使用 `case-NNN`，不把缺陷类别泄漏给模型；
- candidate 只有一个父提交，base 的父提交是 main；
- base/candidate 之间仅有一个 Java 文件 `MODIFY`；
- 输出目录非空时拒绝覆盖；
- 远端分支 HEAD 发布后与本地 revision 清单逐一一致。

样本仓库只包含虚构 Java 代码和中性 README；缺陷名称、类别、路径、行号和人工依据只保存在主仓库 manifest，不进入待审查仓库。

## 12. 真实导入、Diff 与标准答案验收

`verify-live-imports.mjs` 通过现有 API 顺序执行项目创建或精确复用、源码导入、项目与任务回读、默认 Diff 和 Diff 回读。它同时核对：

- import、project、Diff 的 candidate SHA 与 `revisions.json` 一致；
- Diff base/target SHA、唯一 Java `MODIFY`、文件路径和双方变更行一致；
- 覆盖状态与汇总计数一致，不能出现 `SKIPPED`；
- 每条 DEFECT 标准答案既命中真实目标 Diff，也命中 candidate 的 `TARGET` 符号范围。
- 标准答案模式复用已验证的 Import 和 latest Diff，先全批预检再只补缺失项，最终按字符串 ID 和全部业务字段严格回读。

2026-08-06 先使用 H2 `test` Profile，再使用隔离空库 MySQL 26.7 和真实公开 GitHub 仓库全量运行，两种数据库的最终结果均为：

```text
8 PASS
6 FULL
2 PARTIAL
0 SKIPPED
0 FAIL
```

`case-005` 的 candidate 新增 import，因此 TARGET 第 3 行未进入当前 Chunk；`case-008` 的 base 删除 import，因此 BASE 第 3 行未进入当前 Chunk。当前 Java Chunk 从 class 声明开始，这两个 `PARTIAL` 是真实覆盖缺口，不能为了结果好看改写成 `FULL`。缺陷方法行仍有 candidate 映射证据；后续若补齐，应新增具有明确语义的 `FILE_HEADER/IMPORT` Chunk，而不是扩大类 Chunk 伪造覆盖。

真实运行曾遇到 GitHub TLS/克隆瞬时失败。工具允许使用 `--scenario` 做单场景排障重试；当每个项目的最近导入都恢复为 `SUCCEEDED` 后，`--reuse-imports` 会在不再次克隆的前提下，完整复核 8 个最新任务、项目 revision 和 candidate SHA。默认模式重新创建 Diff；`--reuse-diffs` 则复用并严格验证 latest Diff。失败或缺失的最近任务仍会令验收失败，报告通过 `importMode/diffMode` 区分证据来源。

MySQL 从空库执行 Flyway V1-V13，生成 25 张表，应用健康检查为 `UP`。验收结束时 8 个项目均为 `READY`，存在 8 个 `knowledge_document`、46 个 `knowledge_chunk`、8 个成功导入任务和 16 个成功 Diff 任务。`case-008` 首次导入因 GitHub 瞬时错误留下 1 个可观察的失败任务，重试后恢复成功。

标准答案模式必须同时传入 `--reuse-imports --reuse-diffs --record-gold-cases`，且禁止单场景运行。原因是 V13 唯一键为 `(project_id, dataset_version, case_key)`，不包含 `review_task_id`；创建新 Diff 后继续使用相同 caseKey 会破坏重复运行语义。

最终结论来自完整 8 场景运行，不是把单场景报告拼在一起。MySQL 首次标准答案运行得到 `8 created / 8 verified`，立即重跑得到 `0 created / 8 reused / 8 verified`；数据库为 7 条 `DEFECT`、1 条 `CLEAN`，8 个字符串 ID、项目、Diff、revision、类别、路径、行号和依据均通过回读。报告保存在被 Git 忽略的 `target/benchmark-results/known-defects-v1-mysql-gold-cases*.json`，不包含源码、凭证、Prompt 或模型内容。

本轮没有执行 Embedding、FIXED、AGENT 或模型，也没有生成或宣称任何准确率。H2 与 MySQL 实跑共同证明 API、GitHub、JGit、解析、Diff、人工标准答案和真实持久化证据链可工作。系统安装的 MySQL 3306 仍未监听，但这不再阻塞项目数据库链路验收；后续可按运维手册单独恢复。

## 13. 受控 FIXED/AGENT A/B 执行器

`benchmarks/review-fixtures/run-review-ab.mjs` 读取同一份 `manifest.json` 和 `revisions.json`，并把两份原始文件的 SHA-256 写入脱敏报告。运行测试：

```bash
node --test benchmarks/review-fixtures/run-review-ab.test.mjs
```

确认模型密钥仅存在于进程环境、额度足够且服务端依赖就绪后，先运行一个场景 canary：

```bash
node benchmarks/review-fixtures/run-review-ab.mjs \
  --scenario case-001 \
  --report target/benchmark-results/known-defects-v1-review-ab-canary.json
```

canary 的模型、绑定、评测和报告均通过后，再执行完整 8 组：

```bash
node benchmarks/review-fixtures/run-review-ab.mjs \
  --report target/benchmark-results/known-defects-v1-review-ab.json
```

也可以通过 `--base-url` 指定后端。完整模式会先解析并预检全部 8 个确定性项目，严格核对 latest 成功 Diff 的 ID/base/target/candidate revision、完整 Gold Case 和同 Diff 的 latest 成功静态分析；任一漂移时不会发出 AI POST。随后按场景顺序执行 FIXED、立即评测、AGENT、立即评测，失败后把剩余场景标记为未执行。

报告只保存必要 ID、revision、模型/版本、指标和成本快照，不保存源码、Finding/Tool 载荷、API 地址或凭证。单场景 `--scenario` 只产生 `CANARY` 报告，只有 8 组全部完成时 `fullDatasetCompleted` 才为 `true`。当前只完成执行器的零模型测试，尚未运行真实 canary 或完整 A/B，也没有可用于简历的真实指标。
