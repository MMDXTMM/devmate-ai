# 代码审查评测样本

本目录保存可进入 Git 历史的代码审查样本，不保存模型输出或人为调整后的指标。

`known-defects-v1/manifest.json` 是唯一标准答案清单。每个场景包含：

- `base/`：引入本次变更前的代码；
- `candidate/`：待审查的目标代码（避免与 Maven 的 `target/` 忽略规则冲突）；
- `DEFECT` 场景：一个或多个带类别、相对路径和目标版本行范围的缺陷；
- `CLEAN` 场景：没有已知缺陷的对照变更。

每个场景必须独立生成一个 Diff。V13 的评测约束不允许同一 Diff 和数据集版本同时包含 `DEFECT` 与 `CLEAN`，因此不能把所有 candidate 文件一次性提交。

## 可导入 Git 仓库

- 地址：`https://github.com/MMDXTMM/devmate-review-benchmark.git`
- 分支：`case-001` 至 `case-008`
- 每个分支的 HEAD 是 candidate 提交，`HEAD^` 是 base 提交；符合系统默认 Diff 规则。
- 分支使用不表达缺陷类别的编号，避免把人工答案泄漏给模型。
- `known-defects-v1/revisions.json` 固定每个场景的分支、base revision 和 candidate revision。

`ReviewBenchmarkRepositoryBuilder` 使用 JGit、固定作者、提交时间、消息和父提交生成确定性历史。`ReviewBenchmarkGitHistoryTest` 会生成两次仓库并断言 revision 完全相同，同时检查每个候选提交只有一个父提交且 Diff 只修改一个 Java 文件。

## 约束

- 样本只使用虚构业务和本地常量，不包含真实仓库源码、密码、Token 或 Prompt。
- 缺陷代码中不写“这里有 Bug”之类提示，防止答案泄漏给模型。
- 行号以 `candidate/` 快照为准；修改代码时必须同步清单并通过测试。
- `known-defects-v1` 一旦产生真实评测结果即冻结；之后修订必须创建新版本目录。
- 第一轮结果必须来自真实 FIXED/AGENT 运行，不能用 Mock 指标写入简历。

## 自动校验

根工程测试会检查：

1. 场景键和标准答案键唯一；
2. 类别属于系统支持的 `AiFindingCategory`；
3. 路径是项目内相对路径且文件真实存在；
4. 行范围位于 candidate 文件内；
5. base 与 candidate 快照确实存在变更；
6. CLEAN 场景没有缺陷定位，DEFECT 场景至少有一个标准答案。

上述公开仓库的 8 条人工标准答案已经批量录入并完成幂等回读验收。受控 A/B 执行器和服务端预期 Diff 绑定已经实现，并通过零模型漂移、恢复和失败停止测试；真实模型结果仍需先完成单场景 canary，再在相同项目、revision、Diff、模型和数据集条件下执行完整 8 组 FIXED/AGENT A/B。

## 真实导入、Diff 与标准答案验收

启动本机 MySQL 和 DevMate 后端后执行：

```bash
node --test benchmarks/review-fixtures/verify-live-imports.test.mjs
node benchmarks/review-fixtures/verify-live-imports.mjs

# 复用已经验证的 Import 和 Diff，录入或复核全部 manifest 标准答案
node benchmarks/review-fixtures/verify-live-imports.mjs \
  --reuse-imports \
  --reuse-diffs \
  --record-gold-cases \
  --report target/benchmark-results/known-defects-v1-mysql-gold-cases.json
```

默认模式只调用项目、源码导入和 Diff 接口。标准答案模式额外调用评测用例 GET/POST；两种模式都不调用 Embedding、FIXED、AGENT 或模型。工具会：

1. 按 `benchmark-known-defects-v1-case-NNN` 精确查找并复用项目，同名多条或仓库配置漂移时拒绝继续；
2. 顺序重新导入 8 个公开分支，核对导入任务、项目状态和 candidate revision；
3. 执行默认 `HEAD^ → HEAD` Diff，核对 base/target revision、唯一 Java `MODIFY`、文件路径、双方变更行和覆盖计数；
4. 验证 DEFECT 标准答案行同时与真实目标 Diff、candidate 的 `TARGET` 符号证据相交；
5. 在标准答案模式先完成全部 8 个 Import/Diff 和已有用例预检，任一重复、额外或字段漂移都在零次 POST 时失败；
6. 只创建缺失用例，POST 响应丢失时通过精确回读判断是否已提交，最后严格复核 7 条 `DEFECT`、1 条 `CLEAN` 共 8 条；
7. 将 `PARTIAL` 和未映射的 BASE/TARGET 行作为显式警告保存，`SKIPPED`、SHA 漂移或证据缺失会令命令失败。

默认报告写入被 Git 忽略的 `target/benchmark-results/known-defects-v1-import-diff.json`。可通过 `--base-url` 指定其他后端地址，通过 `--report` 指定报告位置。报告只包含项目/任务 ID、revision、覆盖状态和行范围，不包含源码、凭证或模型内容。

若 GitHub 瞬时网络错误只影响一个分支，可使用 `--scenario case-008`（也接受场景键）单独重试，并用 `--report` 保存独立重试报告。所有失败分支的最近导入都恢复为 `SUCCEEDED` 后，使用 `--reuse-imports` 执行一次完整 8 场景复核：该模式不会再次克隆，而是要求每个确定性项目的最近任务、项目 revision 和 candidate SHA 全部一致；默认仍创建新 Diff。`--reuse-diffs` 会进一步复用并验证最近 Diff。

V13 的用例唯一键是 `(project_id, dataset_version, case_key)`，不包含 `review_task_id`。因此 `--record-gold-cases` 必须同时使用 `--reuse-imports --reuse-diffs`，并禁止与单场景模式混用；否则重建 Diff 后会让同一 caseKey 指向不同任务，失去可重复验收语义。

完整验收结论必须来自最后一次 8 场景全量运行，不能用单场景成功拼接结果。网络稳定时可以直接再次触发全部导入；网络持续抖动时使用 `--reuse-imports`，报告会通过 `importMode` 和每个结果的 `importTriggered` 明确记录证据来源。

公开仓库不需要 Git Token。运行验收时不要为了方便把数据库密码、Git Token 或模型 Key 写进命令、脚本或报告；本阶段也不需要配置 `DASHSCOPE_API_KEY`。

标准答案创建接口已经执行同样的服务端证据约束：DEFECT 文件必须唯一属于对应 Diff 的目标版本，标注范围必须与目标变更行相交，并与带正数 `chunkId` 的 `TARGET` 映射形成三重交集。`PARTIAL` 文件只要缺陷行本身有 TARGET 证据仍可录入；完全未映射的行继续由覆盖结果暴露，不能进入本评测集后被包装成“已评测”。

2026-08-06 先使用 H2 `test` Profile，再使用隔离空库 MySQL 26.7 和真实公开 GitHub 仓库完成同一全量验收。MySQL 从空库执行 Flyway V1-V13，应用健康检查为 `UP`；最终 8 个项目均为 `READY`，持久化 8 个文档、46 个 Chunk、8 个成功导入任务和 16 个成功 Diff 任务。`case-008` 首次导入遇到 GitHub 瞬时失败并保留 1 条可观察的 `FAILED` 任务，重试成功后完整复核结果为 8 个场景全部通过、6 个 `FULL`、2 个 `PARTIAL`、0 个 `SKIPPED`。随后同一数据目录已原地迁移到 V14，16 个历史 Diff 文件保持可读，并通过空哈希兼容回退参与标准答案校验。标准答案首次运行得到 `8 created / 8 verified`，立即重跑得到 `0 created / 8 reused / 8 verified`；数据库最终为 7 条 `DEFECT`、1 条 `CLEAN`，分别绑定 8 个项目和 8 个 Diff。

`case-005` 的 TARGET 第 3 行新增 import、`case-008` 的 BASE 第 3 行删除 import，不在当前从 class 声明开始的 Chunk 内；两者的类、方法和缺陷目标行仍有真实映射证据。MySQL 标准答案报告位于被忽略的 `target/benchmark-results/known-defects-v1-mysql-gold-cases.json`，重复运行报告以 `goldCasesCreated=0` 和 `goldCasesReused=8` 证明幂等。系统安装的 3306 服务仍未监听，这是独立的本机运维问题，不影响上述隔离 MySQL 对真实方言、迁移和持久化链路的验收结论。本轮不包含模型效果指标。

2026-08-07 新增精确 `FILE_HEADER` 包声明和逐条 `IMPORT` Chunk 后，使用全新 H2 V1-V18 与全新 MySQL 26.7 V1-V18 对同一公开仓库分别执行全部导入与 Diff，两次均为 `8 PASS / 8 FULL / 0 PARTIAL / 0 SKIPPED` 且无警告。新 MySQL schema 保存 8 个项目、8 个文档、62 个 Chunk（8 个 `FILE_HEADER`、8 个 `IMPORT`）和 8 个成功 Diff；运行没有调用 Embedding 或模型。此前 MySQL 历史 Diff 仍保留旧解析时的 `6 FULL / 2 PARTIAL`，没有被重写。

## 受控 FIXED/AGENT A/B

执行器位于 `run-review-ab.mjs`，只使用 Node 标准库和本目录已有校验辅助函数。先运行零模型测试：

```bash
node --test benchmarks/review-fixtures/run-review-ab.test.mjs
```

确认模型密钥只存在于进程环境、额度足够且后端依赖就绪后，先运行单场景 canary：

```bash
node benchmarks/review-fixtures/run-review-ab.mjs \
  --scenario case-001 \
  --report target/benchmark-results/known-defects-v1-review-ab-canary.json
```

canary 全链路通过后，才执行完整 8 组：

```bash
node benchmarks/review-fixtures/run-review-ab.mjs \
  --report target/benchmark-results/known-defects-v1-review-ab.json
```

需要连接其他后端时使用 `--base-url`。单场景运行只会生成 `CANARY` 报告；完整报告必须包含全部 8 组成功配对，不能拼接多次 canary。

执行器行为：

1. 读取 8 个 manifest/revision 场景，并记录两份输入文件的 SHA-256；
2. 在任何模型调用前完成全批预检，精确解析确定性项目，校验 latest 成功 Diff 的字符串 ID、base/target/candidate revision、完整 Gold Case，以及同一 Diff 的 latest 成功静态分析；
3. AI 创建请求显式携带 `{reviewTaskId, revision, attemptKey}`，后端在模型调用前复核它仍是项目 latest 成功 Diff，并用 V15 唯一键关联本次付费请求；
4. 每个场景严格按 `FIXED → 评测 → AGENT → 评测` 执行，并要求两条路径的项目、Diff、revision、provider、model 和 dataset hash 一致；Prompt 与检索版本允许按路径不同，但会分别快照；
5. 每次付费 POST 使用新的 UUID v4 `attemptKey`。响应丢失时只按 attempt 路径回读，不重复 POST；并发产生的同项目、Diff、模式和模型任务不会被误认；
6. 同步多轮 Agent 默认使用 20 分钟请求和恢复窗口，可通过 CLI 在受控范围内调整。任一失败立即停止后续场景；没有服务端批次状态机，因此不自动跨进程续跑整个实验；
7. 报告按汇总 TP/FP/FN 计算微平均 Precision/Recall/F1，并汇总 Prompt/Completion/总 Token、耗时以及 Tool 调用成功/失败数；`partialMetrics=true` 会拒绝作为最终可比结果；
8. 报告不保存源码、Finding/Tool 原始载荷、API 地址、凭证或完整模型内容；失败信息只保存允许列表错误类别，不复制任意服务端错误文本。

2026-08-06 已通过全部 48 项 Benchmark Node 测试，其中执行器 20 项 Fake Client 测试覆盖成功顺序、全批零 POST 失败、Diff/模型/数据集漂移、AI 与评测响应丢失恢复、并发同配置任务隔离、失败即停、字符串 ID、微平均、长超时参数和成功/失败报告脱敏。尚未运行真实 FIXED/AGENT canary 或完整 A/B，因此没有真实准确率、Token 或延迟指标可以写入简历。
