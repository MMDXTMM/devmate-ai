# 代码审查固定缺陷集与评测设计

## 1. 目标

阶段 8 的评测不是证明模型“会输出文本”，而是使用固定标准答案比较固定流水线和 Agent 在同一 Diff 上的实际表现。每次运行必须绑定项目、Diff、revision、执行模式、模型、Prompt、检索配置和数据集哈希，确保结果可以复现。

本阶段不调用模型，只评估已经成功完成的 `ai_review_task`。这样评测失败不会重复消耗 Token，也不会把模型调用和指标计算混在同一个事务中。

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
- 同一 Diff 同一数据集混用 `CLEAN` 和 `DEFECT`：409。

### 查询标准用例

`GET /api/projects/{projectId}/review-evaluation-cases?datasetVersion=...&reviewTaskId=...`

### 执行评测

`POST /api/projects/{projectId}/review-evaluation-runs`

请求只提供 `datasetVersion` 和 `aiReviewTaskId`。服务端校验 AI 任务成功、项目归属、Diff 与 revision 一致，并读取真实执行模式和调用指标。

### 查询运行结果

`GET /api/projects/{projectId}/review-evaluation-runs?datasetVersion=...&reviewTaskId=...`

返回最近 100 条稳定排序的运行快照，供 Vue A/B 看板展示。

## 7. Vue 评测工作台

项目列表的“评测”入口自动读取最近成功 Diff、最近 AI 审查、当前数据集用例和历史运行。用户可以：

1. 选择符合 `[A-Za-z0-9._-]+` 的数据集版本；
2. 录入 `DEFECT` 的类别、相对路径、行范围和人工依据，或录入不带缺陷字段的 `CLEAN` 对照；
3. 对最近一次已持久化的 AI 审查执行评测；
4. 并排查看最近 FIXED/AGENT 的质量、成本与 Tool 指标。

工作台不会调用模型，也不允许请求携带执行模式。当前 AI 接口只提供最近任务，因此第一版 A/B 操作顺序是“运行 FIXED → 评测 → 运行 AGENT → 评测”；两个运行快照随后在同一数据集下并排显示。这一限制优先保证小而可解释的接口范围，后续只有在真实评测流程需要时才增加历史任务选择接口。

## 8. 事务和安全边界

- 用例创建和评测结果落库使用短事务；
- 指标计算只读取数据库，不调用 Git、文件、模型或 Shell；
- 结果 JSON 只保存用例 ID、Finding ID、匹配结果和可读原因，不保存完整源码、Prompt 或模型响应；
- BIGINT ID 对前端始终序列化为字符串；
- 项目、Diff、AI 任务和 Finding 归属全部由服务端验证。

## 9. 测试方案

- 创建缺陷用例和无缺陷用例；
- 拒绝重复键、非法路径、非法字段组合和跨项目任务；
- 一次评测同时产生 TP、FP、FN，并验证 Precision/Recall/F1；
- 多候选进入人工复核且指标标记为部分结果；
- 无缺陷且无 Finding 得到完整通过；
- 相同 AI 任务和数据集哈希重复执行幂等返回；
- H2 从空库执行 V1–V13，并在真实 MySQL 验证 V12→V13。

当前验证结果：后端 107 项测试、前端 29 项测试、真实验收工具 16 项 Node 测试和 Vue 生产构建全部通过；隔离空库 MySQL 26.7 已执行 V1-V13，生成 25 张表并完成 8 场景真实持久化验收。系统安装的 3306 服务仍未监听，属于独立的本机运维问题。评测工作台和样本契约没有新增数据库结构，也没有调用真实模型。

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

当前已完成样本定义、自动校验、确定性 Git 历史、公开仓库发布及 8 个场景的真实导入/Diff 验收；尚未向 V13 录入人工标准答案，也未产生真实 FIXED/AGENT 指标。

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

## 12. 真实导入与 Diff 验收

`verify-live-imports.mjs` 通过现有 API 顺序执行项目创建或精确复用、源码导入、项目与任务回读、默认 Diff 和 Diff 回读。它同时核对：

- import、project、Diff 的 candidate SHA 与 `revisions.json` 一致；
- Diff base/target SHA、唯一 Java `MODIFY`、文件路径和双方变更行一致；
- 覆盖状态与汇总计数一致，不能出现 `SKIPPED`；
- 每条 DEFECT 标准答案既命中真实目标 Diff，也命中 candidate 的 `TARGET` 符号范围。

2026-08-06 先使用 H2 `test` Profile，再使用隔离空库 MySQL 26.7 和真实公开 GitHub 仓库全量运行，两种数据库的最终结果均为：

```text
8 PASS
6 FULL
2 PARTIAL
0 SKIPPED
0 FAIL
```

`case-005` 的 candidate 新增 import，因此 TARGET 第 3 行未进入当前 Chunk；`case-008` 的 base 删除 import，因此 BASE 第 3 行未进入当前 Chunk。当前 Java Chunk 从 class 声明开始，这两个 `PARTIAL` 是真实覆盖缺口，不能为了结果好看改写成 `FULL`。缺陷方法行仍有 candidate 映射证据；后续若补齐，应新增具有明确语义的 `FILE_HEADER/IMPORT` Chunk，而不是扩大类 Chunk 伪造覆盖。

真实运行曾遇到 GitHub TLS/克隆瞬时失败。工具允许使用 `--scenario` 做单场景排障重试；当每个项目的最近导入都恢复为 `SUCCEEDED` 后，`--reuse-imports` 会在不再次克隆的前提下，完整复核 8 个最新任务、项目 revision 和 candidate SHA，并重新创建 Diff。失败或缺失的最近任务仍会令验收失败，报告通过 `importMode` 与 `importTriggered` 区分“触发导入”和“复用已验证导入”。

MySQL 从空库执行 Flyway V1-V13，生成 25 张表，应用健康检查为 `UP`。验收结束时 8 个项目均为 `READY`，存在 8 个 `knowledge_document`、46 个 `knowledge_chunk`、8 个成功导入任务和 16 个成功 Diff 任务。`case-008` 首次导入因 GitHub 瞬时错误留下 1 个可观察的失败任务，重试后恢复成功。

最终结论来自故障恢复后的完整 8 场景 `--reuse-imports` 运行，不是把单场景报告拼在一起。H2 与 MySQL 报告分别保存在被 Git 忽略的 `target/benchmark-results/known-defects-v1-import-diff.json` 和 `target/benchmark-results/known-defects-v1-mysql-import-diff.json`；报告不保存源码、凭证、Prompt 或模型内容。MySQL 最终报告的 `importMode` 为 `REUSE_LATEST_SUCCEEDED`。

本轮没有执行 Embedding、FIXED 或 AGENT，没有把 manifest 写入评测表，也没有生成或宣称任何准确率。H2 与 MySQL 实跑共同证明 API、GitHub、JGit、解析、Diff 和真实持久化证据链可工作。系统安装的 MySQL 3306 仍未监听，但这不再阻塞项目数据库链路验收；后续可按运维手册单独恢复。
