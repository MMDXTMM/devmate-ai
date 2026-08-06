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

下一任务通过上述公开仓库批量录入并回读复核人工标准答案，再执行 FIXED、AGENT 和 V13 评测接口的第一轮 A/B。

## 真实导入与 Diff 验收

启动本机 MySQL 和 DevMate 后端后执行：

```bash
node --test benchmarks/review-fixtures/verify-live-imports.test.mjs
node benchmarks/review-fixtures/verify-live-imports.mjs
```

验收工具只调用项目、源码导入和 Diff 接口，不调用 Embedding、FIXED 或 AGENT。它会：

1. 按 `benchmark-known-defects-v1-case-NNN` 精确查找并复用项目，同名多条或仓库配置漂移时拒绝继续；
2. 顺序重新导入 8 个公开分支，核对导入任务、项目状态和 candidate revision；
3. 执行默认 `HEAD^ → HEAD` Diff，核对 base/target revision、唯一 Java `MODIFY`、文件路径、双方变更行和覆盖计数；
4. 验证 DEFECT 标准答案行同时与真实目标 Diff、candidate 的 `TARGET` 符号证据相交，但不把标准答案写入数据库；
5. 将 `PARTIAL` 和未映射的 BASE/TARGET 行作为显式警告保存，`SKIPPED`、SHA 漂移或证据缺失会令命令失败。

默认报告写入被 Git 忽略的 `target/benchmark-results/known-defects-v1-import-diff.json`。可通过 `--base-url` 指定其他后端地址，通过 `--report` 指定报告位置。报告只包含项目/任务 ID、revision、覆盖状态和行范围，不包含源码、凭证或模型内容。

若 GitHub 瞬时网络错误只影响一个分支，可使用 `--scenario case-008`（也接受场景键）单独重试，并用 `--report` 保存独立重试报告。所有失败分支的最近导入都恢复为 `SUCCEEDED` 后，使用 `--reuse-imports` 执行一次完整 8 场景复核：该模式不会再次克隆，而是要求每个确定性项目的最近任务、项目 revision 和 candidate SHA 全部一致，再重新创建并校验 Diff。它不会跳过或接受失败任务。

完整验收结论必须来自最后一次 8 场景全量运行，不能用单场景成功拼接结果。网络稳定时可以直接再次触发全部导入；网络持续抖动时使用 `--reuse-imports`，报告会通过 `importMode` 和每个结果的 `importTriggered` 明确记录证据来源。

公开仓库不需要 Git Token。运行验收时不要为了方便把数据库密码、Git Token 或模型 Key 写进命令、脚本或报告；本阶段也不需要配置 `DASHSCOPE_API_KEY`。

标准答案创建接口已经执行同样的服务端证据约束：DEFECT 文件必须唯一属于对应 Diff 的目标版本，标注范围必须与目标变更行相交，并与带正数 `chunkId` 的 `TARGET` 映射形成三重交集。`PARTIAL` 文件只要缺陷行本身有 TARGET 证据仍可录入；完全未映射的行继续由覆盖结果暴露，不能进入本评测集后被包装成“已评测”。

2026-08-06 先使用 H2 `test` Profile，再使用隔离空库 MySQL 26.7 和真实公开 GitHub 仓库完成同一全量验收。MySQL 从空库执行 Flyway V1-V13，应用健康检查为 `UP`；最终 8 个项目均为 `READY`，持久化 8 个文档、46 个 Chunk、8 个成功导入任务和 16 个成功 Diff 任务。`case-008` 首次导入遇到 GitHub 瞬时失败并保留 1 条可观察的 `FAILED` 任务，重试成功后完整复核结果为 8 个场景全部通过、6 个 `FULL`、2 个 `PARTIAL`、0 个 `SKIPPED`。随后同一数据目录已原地迁移到 V14，16 个历史 Diff 文件保持可读，并通过空哈希兼容回退参与标准答案校验。

`case-005` 的 TARGET 第 3 行新增 import、`case-008` 的 BASE 第 3 行删除 import，不在当前从 class 声明开始的 Chunk 内；两者的类、方法和缺陷目标行仍有真实映射证据。MySQL 最终报告位于被忽略的 `target/benchmark-results/known-defects-v1-mysql-import-diff.json`，`importMode` 为 `REUSE_LATEST_SUCCEEDED`。系统安装的 3306 服务仍未监听，这是独立的本机运维问题，不影响上述隔离 MySQL 对真实方言、迁移和持久化链路的验收结论。本轮不包含模型效果指标。
