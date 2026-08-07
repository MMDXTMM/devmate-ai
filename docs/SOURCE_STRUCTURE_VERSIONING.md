# 源码结构版本与安全重建

## 1. 问题

同一 Git revision 的源码文本不会变化，但 AST、配置或 SQL 解析规则会升级。若系统直接删除并重建同 revision 的 Chunk，会同时破坏向量绑定、Diff 中保存的 Chunk ID、Finding 证据和评测可复现性。

因此源码版本必须由二元组标识：

```text
Git revision + structure_version
```

当前实现版本为 `source-structure-v2`；V19 将已有结构回填为 `source-structure-v1`。

## 2. 数据模型

- `project.current_structure_version`：当前可用结构版本，未导入项目为空。
- `index_task.structure_version`：本次任务使用的解析规则版本。
- `knowledge_document.structure_version`：文件结构快照的解析规则版本。

版本覆盖 Java AST、配置和数据库迁移结构，不只代表 Java 解析器。

## 3. 导入策略

| 场景 | 行为 |
| --- | --- |
| 同 revision、同结构版本、普通导入 | 零重写复用，保留 Document、Chunk、Reference 和 Vector ID |
| 新 revision、同结构版本 | 按路径、类型和内容哈希增量复用 |
| 新 revision、旧结构版本 | 所有文件用当前版本重新解析，不跨版本复用 Chunk |
| 同 revision、旧结构版本、普通导入 | 返回 409，提示显式重建 |
| 同 revision、显式重建且无下游证据 | 全量解析并原子替换当前 revision 的结构 |
| 同 revision、显式重建且已有证据 | 返回 409，不改变 Chunk 和项目可用状态 |

显式入口为 `POST /api/projects/{projectId}/imports/rebuild`。拒绝的重建任务保存为 `FAILED`，项目恢复到进入任务前的状态；真实 Git、扫描或解析失败仍将项目标记为 `FAILED`。

## 4. 并发与事务

源码导入、Diff 和向量任务在准备短事务中锁定同一项目行：

```text
导入获得项目行锁 → READY/CREATED 切换为 INDEXING → 提交
Diff/向量获得项目行锁 → 仅 READY 可创建任务 → 提交
```

Git 克隆、文件扫描、解析和 Embedding 不在该事务中。项目处于 `INDEXING` 时禁止新建 Diff 或向量任务；源码导入也会拒绝与正在运行的 Diff/向量任务并发。这样关闭了“先检查依赖、后替换 Chunk”之间的竞态窗口，同时没有制造长事务。

模块之间通过 `SourceStructureUsageChecker` 协作：knowledge 实现检查向量和检索评测，review 实现检查 Diff。源码导入不直接依赖 review Mapper。

## 5. 为什么不自动重写历史 Diff

历史 Diff、Finding 和评测记录描述的是当时运行得到的证据。解析器升级后自动改写它们会让结果无法复现，也可能把新规则产生的更好覆盖误记成旧任务结果。第一版保留历史快照，新 revision 自动使用新结构；同 revision 只有在没有下游证据时才允许显式重建。

后续若确实需要对同一 revision 保存多个结构版本，应新增快照维度和迁移方案，而不是放宽当前唯一键或绕过门禁。

## 6. 验证

- H2 全新执行 Flyway V1-V19。
- 后端 142 项测试通过，覆盖成功、旧版本冲突、全量重建、Diff/向量/检索评测依赖和 INDEXING 门禁。
- 前端 47 项测试与生产构建通过。
- MySQL 26.7 历史 schema V18→V19：8 项目、8 文档、62 Chunk、8 Diff 保持可读，旧结构全部回填为 v1。
- MySQL 26.7 全新 schema V1→V19：25 张表，三个版本字段和默认值正确，应用健康为 `UP`。
- 本任务没有调用 Embedding Provider 或大模型。
