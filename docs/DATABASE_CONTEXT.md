# 数据库结构上下文

## 1. 目标

代码审查中的 SQL 性能、事务和数据一致性问题通常依赖表结构、字段可空性和索引。只看到 `Mapper` 调用或实体类无法判断真实约束，因此本阶段把受控数据库迁移转为可检索的结构事实，并关联 Java 实体映射。

## 2. 处理流程

```text
Git 仓库常见 migration 目录
  → 只扫描 .sql 文件
  → 有状态切分 SQL，保留语句起止行
  → JSqlParser 限时解析 CREATE TABLE / CREATE INDEX / ALTER TABLE
  → 生成表、列、索引、约束和结构变更 Chunk
  → JDK AST 提取 @TableName / @Table(name = ...)
  → code_reference 关联 Java 类型与 DATABASE_TABLE
  → API/Vue 展示结构摘要和目标迁移文件
```

## 3. 数据模型

本阶段继续复用知识库和关系图：

- `knowledge_document.source_kind = DATABASE_SCHEMA`；
- `knowledge_document.file_type = SQL`；
- Chunk 类型包括 `DATABASE_TABLE`、`DATABASE_COLUMN`、`DATABASE_INDEX`、`DATABASE_CONSTRAINT` 和 `DATABASE_CHANGE`；
- 表符号使用规范化小写表名，列符号使用 `table.column`，索引/约束使用 `table#index`；
- Java 实体通过 `DATABASE_TABLE` 引用指向表定义。

接口不返回原始 SQL，只返回结构化、可展示的安全摘要。例如：

```text
project.id BIGINT nullable=false
idx_project_status ON project (status)
```

## 4. 安全与资源边界

- 只接受 `db/migration`、`migrations`、`database/migrations` 等常见迁移目录中的 SQL；
- 不执行目标项目 SQL、Maven/Gradle 脚本，也不连接目标数据库；
- INSERT/UPDATE/DELETE 等 DML 不进入知识库，避免保存种子数据或秘密；
- SQL 切分器识别引号和注释，避免字符串中的分号被错误拆分；
- 每条 DDL 解析限制 1 秒，每个文件最多 500 条结构语句；
- 延续 SQL 文件数量、单文件大小、总容量和符号链接限制；
- 数据库存储结构摘要，不保存默认值和原始迁移正文。

## 5. 正确性边界

- 当前解析迁移事实，不模拟所有迁移后得到的最终数据库快照；
- `ALTER TABLE ADD COLUMN` 会生成列 Chunk，无法可靠归并的 ALTER 会记录为 `DATABASE_CHANGE`；
- DROP、RENAME、数据库特有语法和动态 SQL 需要后续版本化 Schema Reducer；
- `@TableName/@Table` 只证明实体声明的映射，不能证明所有运行时 SQL 都使用该表；
- 当前不解析 MyBatis XML、注解 SQL、存储过程或线上临时索引；
- 生产数据库可能存在未纳入 Git 的漂移，需要后续只读 Schema Snapshot 对比。

因此系统把这些内容作为审查证据，不直接输出“线上一定缺少索引”等确定结论。

## 6. 面试需要掌握

- 为什么不让审查 Agent 直接连接生产数据库；
- DDL、DML、迁移事实和最终 Schema 快照的区别；
- 为什么 SQL 解析使用正式 Parser，而不是只用正则；
- 为什么解析仍需要超时、语句数和文件目录白名单；
- Java 实体注解到表定义如何形成跨文件关系；
- 为什么索引存在不代表查询一定使用索引，还需要 SQL、选择性和执行计划；
- 为什么复用知识 Chunk/引用图，而不是新建与 RAG 割裂的专用表。

## 7. 验收证据

- 单元测试覆盖 CREATE TABLE、列、约束、CREATE INDEX、ALTER ADD COLUMN、字符串内分号、非法 DDL 和全部项目 Flyway 脚本；
- 扫描测试覆盖迁移目录白名单、任意 SQL 排除和文件数限制；
- 接口测试覆盖 SQL 文档入库、安全摘要、Java 实体表关联和目标迁移路径；
- 后端 60 个自动化测试全部通过；
- 前端 11 个自动化测试、TypeScript 类型检查和生产构建全部通过；
- 使用本地 MySQL 26.7 完成真实启动，Flyway 成功校验 7 个迁移，健康检查为 `UP`，并成功读取已有项目数据。
