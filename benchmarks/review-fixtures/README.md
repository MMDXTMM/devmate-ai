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

后续任务直接通过上述公开仓库执行源码导入、Diff、FIXED、AGENT 和 V13 评测接口的第一轮 A/B。
