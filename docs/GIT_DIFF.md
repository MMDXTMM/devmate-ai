# Git Diff 与覆盖清单

## 目标

阶段 3 不直接调用大模型，而是先回答三个可验证问题：

1. 两个提交之间修改了哪些文件和行？
2. 每个目标版本 Java 变更行属于哪些类或方法？
3. 哪些文件或行没有完成映射，原因是什么？

## 执行流程

```text
POST /api/projects/{projectId}/review-diffs
        ↓
校验项目存在并找到最近一次成功的 index_task
        ↓ 短事务
创建 code_review_task = RUNNING
        ↓ 无数据库事务
从对应任务工作区读取 Git 对象
        ↓
默认比较 HEAD^ 与 HEAD，或使用请求中的提交哈希
        ↓
JGit 识别 ADD/MODIFY/DELETE/RENAME/COPY 和基准/目标 Edit 行区间
        ↓
目标版本新增行与 knowledge_chunk 起止行求交集
        ↓
基准版本删除行从 Git 对象读取源码并在内存解析 AST
        ↓ 短事务
保存 code_review_file 覆盖清单并完成任务
```

失败时任务进入 `FAILED`。Git读取和映射不放入长数据库事务。

## 覆盖状态

- `FULL`：目标版本新增/修改行和基准版本删除行都已映射到 AST 符号；纯路径 Rename 没有代码行变化时也视为完整覆盖。
- `PARTIAL`：只映射了部分行、目标版本缺少结构数据，或基准版本源码对象不可用。
- `SKIPPED`：当前阶段不处理的非 Java 文件。

`SKIPPED` 不等于忽略。每个变更文件都会写入覆盖清单并记录原因，避免“静默漏审后返回成功”。

## 当前边界

- Git导入深度从 1 提升为 50，用于读取近期提交历史；更早提交需要重新获取或增加深度。
- 目标版本新增和修改行映射到持久化知识块，证据侧标记为 `TARGET`。
- 删除行读取基准提交中的 Git Blob 并在内存解析，证据侧标记为 `BASE`；不会把旧源码重复持久化。
- 包声明使用精确 `FILE_HEADER` Chunk，每条普通或 static import 使用独立 `IMPORT` Chunk；只覆盖真实语法行，不通过扩大类范围伪造完整覆盖。
- JGit 已启用 Rename 检测，并覆盖完全重命名和带内容修改的相似度重命名用例。
- Git Blob 读取沿用单文件大小限制，避免旧版本大文件绕过导入安全边界。
- 当前输出是覆盖报告，不是静态分析结论或 AI Finding。

## 接口

- `POST /api/projects/{projectId}/review-diffs`：生成覆盖报告。请求 `{}` 时比较最近提交与其父提交。
- `GET /api/projects/{projectId}/review-diffs/latest`：查询最近一次报告。

前端项目行的“Diff”按钮会生成并展示报告。旧的深度 1 工作区需要先“重新导入”源码。
