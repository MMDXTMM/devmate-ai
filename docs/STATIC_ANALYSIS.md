# 确定性静态分析

## 目标

静态分析负责模型不应该猜测的问题。当前版本使用 PMD 7.26.0 对目标版本中本次 Diff 涉及的 Java 文件运行受控规则，并只保留位置与目标变更行相交的违规。

这一步不能替代后续 AI 审查：PMD 适合稳定规则，LLM 才用于需要调用链和业务上下文的并发、事务、缓存及消息一致性推理。

## 执行链路

```text
POST /api/projects/{projectId}/static-analyses
        ↓ 短事务
查找最近成功的 Diff，创建 static_analysis_task = RUNNING
        ↓ 无数据库事务
从 code_review_file 选择目标版本变更 Java 文件
        ↓
校验文件仍位于受控源码工作区，限制文件数和执行时间
        ↓
PMD 使用固定规则集分析源码
        ↓
过滤未命中本次目标变更行的历史问题
        ↓ 短事务
按指纹去重，写入 review_finding，任务置为 SUCCEEDED
```

失败时任务进入 `FAILED` 并保存脱敏错误。PMD 不运行目标仓库的 Maven、Gradle 或脚本。

## 当前规则

| PMD 规则 | DevMate 分类 | 目的 |
|---|---|---|
| `CloseResource` | `RESOURCE` | 发现可能未关闭的资源 |
| `EmptyCatchBlock` | `ERROR_HANDLING` | 发现吞掉异常的空 catch |
| `ReturnEmptyCollectionRatherThanNull` | `API_DESIGN` | 避免集合返回 null |
| `AvoidPrintStackTrace` | `LOGGING` | 避免直接打印堆栈 |
| `UnusedLocalVariable` | `CODE_QUALITY` | 发现无效局部变量 |

规则集刻意保持较小。新增规则前必须用固定样例评估误报，不能为了增加问题数量一次启用 PMD 全部规则。

## 统一 Finding

每条静态问题保存：

- 工具及规则 ID；
- 分类和严重程度；
- 文件与真实起止行；
- 确定性消息和证据说明；
- 基于规则、路径、行号和消息生成的 SHA-256 去重指纹；
- 路径另存 SHA-256 哈希用于联合索引，完整路径仍用于展示；
- `source = STATIC`，为后续 `LLM/HYBRID` 结果预留统一模型。

## 安全与资源边界

- 最多分析 200 个本次变更 Java 文件。
- 默认 30 秒超时，工作线程可取消。
- 文件必须位于源码导入工作区，拒绝符号链接和越界路径。
- PMD 单线程运行，不执行目标项目代码，不读取 Git Token。
- 当前不提供辅助 classpath，依赖完整类型解析的规则暂不加入规则集。

## 接口

- `POST /api/projects/{projectId}/static-analyses`：针对最近一次成功 Diff 执行静态分析。
- `GET /api/projects/{projectId}/static-analyses/latest`：查询最近一次任务及 Finding。

前端项目行的“静态分析”按钮会执行任务并展示工具版本、文件数、问题位置、规则和证据。

## 面试必须理解

1. PMD 为什么比 SpotBugs 更适合当前“未必能编译的外部源码”场景。
2. 为什么只分析 Diff 文件，并过滤到目标变更行，避免把历史技术债算到本次提交。
3. 为什么静态工具运行在数据库事务外，任务状态却需要短事务持久化。
4. 为什么 Finding 要保存工具版本、规则 ID、真实行号和去重指纹。
5. 为什么静态分析没有问题不能证明代码没有并发、事务或一致性风险。

PMD 官方资料：

- <https://pmd.github.io/pmd/pmd_userdocs_tools_java_api.html>
- <https://pmd.github.io/pmd/pmd_release_notes_old.html>
