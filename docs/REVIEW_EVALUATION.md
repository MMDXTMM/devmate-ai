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

返回最近 100 条稳定排序的运行快照，供下一小阶段 Vue A/B 看板展示。

## 7. 事务和安全边界

- 用例创建和评测结果落库使用短事务；
- 指标计算只读取数据库，不调用 Git、文件、模型或 Shell；
- 结果 JSON 只保存用例 ID、Finding ID、匹配结果和可读原因，不保存完整源码、Prompt 或模型响应；
- BIGINT ID 对前端始终序列化为字符串；
- 项目、Diff、AI 任务和 Finding 归属全部由服务端验证。

## 8. 测试方案

- 创建缺陷用例和无缺陷用例；
- 拒绝重复键、非法路径、非法字段组合和跨项目任务；
- 一次评测同时产生 TP、FP、FN，并验证 Precision/Recall/F1；
- 多候选进入人工复核且指标标记为部分结果；
- 无缺陷且无 Finding 得到完整通过；
- 相同 AI 任务和数据集哈希重复执行幂等返回；
- H2 从空库执行 V1–V13，并在真实 MySQL 验证 V12→V13。

当前验证结果：后端 103 项测试、前端 24 项测试和 Vue 生产构建全部通过；本机 MySQL 已从 V12 成功迁移到 V13，健康检查为 `UP`，原项目数据保持可读。
