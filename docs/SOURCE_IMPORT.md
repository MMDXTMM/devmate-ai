# Git 源码导入闭环

## 本阶段目标

把 `project` 表中的 Git 仓库配置变成结构化 Java 源码与配置上下文，并把一次导入的状态、版本、文件和符号元数据记录到数据库。当前已经完成安全获取、AST/配置解析、Chunk 和引用关系持久化；向量索引作为导入后的独立步骤，由当前 revision 和模型版本严格隔离。

## 用户操作与数据流

```text
Vue 点击“导入源码”
        ↓ POST /api/projects/{id}/imports
校验 project 存在、类型为 GIT、仓库地址和分支合法
        ↓
project.status = INDEXING
index_task = RUNNING
        ↓
JGit 浅克隆指定分支到独立任务目录
        ↓
递归扫描普通 Spring Boot 或 Maven 多模块仓库中的 Java、YAML、Properties 和迁移目录 SQL，并应用分类数量、单文件和总容量限制
        ↓
与上一 revision 按路径和内容哈希比较；未变文件复用结构元数据，变更文件使用 JDK AST、配置或 SQL 解析器
        ↓
计算路径/内容哈希并写入 knowledge_document、knowledge_chunk、code_reference
        ↓
index_task = SUCCEEDED，project = READY
```

任一步骤失败时，任务会进入 `FAILED`，项目也会进入 `FAILED`，错误信息保存在 `index_task.error_message` 并通过统一异常响应返回前端。

## 三张核心表的职责

- `project`：保存仓库地址、默认分支、当前已导入 revision 和项目级状态。
- `index_task`：每次导入生成一条任务记录，用于追踪开始、结束、文件数和失败原因。
- `knowledge_document`：每个 Java、配置或 SQL 迁移文件对应一条文档元数据，记录类型、路径、哈希、状态和 revision。
- `knowledge_chunk`：保存类、方法、配置项或数据库表/列/索引，包含符号名、安全摘要、哈希和起止行。
- `code_reference`：保存方法调用、数据访问以及 Java 到配置/数据库定义的关系。

同一 revision 重复导入时直接复用已有 Document、Chunk、Reference 和向量绑定，不再删除重建。新 revision 会扫描并计算全部允许文件的哈希，但只解析新增、修改或移动的文件；路径、类型和内容哈希都一致的文件从上一 revision 重建结构元数据，并把原始引用重新绑定到新 revision 的 Chunk。删除文件不会写入新 revision，因此不会参与当前检索；旧 revision 保留用于复现历史审查。

`index_task` 使用 `FULL/INCREMENTAL` 区分首次与后续导入，并分别记录 `processed_files` 和 `reused_files`。复用表示跳过了解析，不表示新 revision 直接引用旧 Chunk ID。V18 进一步记录 `clone/scan/plan/parse/persist/total` 六段耗时；使用 `System.nanoTime()` 计算耗时，避免系统时钟调整产生负数。成功、失败和同 revision 快速路径都会保存指标，同 revision 的扫描、计划和解析耗时固定为 0。

## 事务边界

网络克隆和磁盘扫描耗时且不可回滚，因此不能放进一个长数据库事务。当前采用三个短事务：

1. `prepare`：校验项目，将项目改为 `INDEXING`，创建 `RUNNING` 任务。
2. 克隆与扫描：不持有数据库事务。
3. `complete` 或 `fail`：原子保存文件、符号以及任务和项目最终状态。

这种设计避免网络请求长时间占用数据库连接和行锁。它不是“所有步骤一次性回滚”，而是通过可观测任务状态实现最终一致；后续异步化后仍沿用这个边界。

## 安全边界

- 当前只允许 `https://` Git 地址，拒绝 URL 用户信息。
- 拒绝 localhost、`.local` 和私有 IP 字面量，降低 SSRF 风险。
- 只扫描普通 `.java`、`.yml`、`.yaml`、`.properties` 和迁移目录 `.sql` 文件，不跟随符号链接。
- 忽略 `.git`、`.idea`、`target`、`build`、`out` 和 `node_modules`。
- 对文件数量、单文件大小和总扫描容量设置上限。
- 配置值不进入数据库；敏感键只保存 `<redacted>`，防止后续向量化或 Prompt 泄密。
- SQL 只保存 DDL 结构摘要，忽略 DML、默认值和原始正文，也绝不执行目标迁移。
- 每个任务使用独立工作目录，防止项目路径互相覆盖。
- 默认日志为 INFO，避免 MyBatis 把完整源码 Chunk 作为 SQL 参数打印到日志。

## 私有 GitHub 仓库

私有仓库需要显式提供只读凭证。应用只从进程环境变量读取凭证：

- `DEVMATE_GIT_USERNAME`：GitHub 用户名；不填时使用 `x-access-token`。
- `DEVMATE_GIT_TOKEN`：GitHub Fine-grained personal access token。

建议创建仅允许访问目标仓库、仅具有 `Contents: Read-only` 权限且设置有效期的 Fine-grained Token。不要把 Token 写入 `project.source_location`、`application.yml`、数据库、代码或 Git 提交。

macOS 终端可以通过隐藏输入启动应用，避免把 Token 直接写进命令历史：

```bash
export DEVMATE_GIT_USERNAME=MMDXTMM
read -s DEVMATE_GIT_TOKEN
export DEVMATE_GIT_TOKEN
./mvnw spring-boot:run
```

执行 `read -s` 后粘贴 Token 并按回车，终端不会显示输入。应用关闭后可以执行：

```bash
unset DEVMATE_GIT_TOKEN
unset DEVMATE_GIT_USERNAME
```

## 当前限制

- 当前仍同步执行 Git 克隆、全量文件扫描和变更文件解析，适合学习和小型仓库；是否通过 MQ 异步化要由真实耗时、吞吐和失败恢复数据决定。
- 当前不会自动读取 GitHub CLI 或系统钥匙串；私有仓库必须显式设置环境变量。
- 当前只有一组进程级 Git 凭证；多用户生产版本后续改为 GitHub App Installation Token。
- JDK AST 当前完成语法结构解析，不执行仓库代码，也不解析完整跨文件类型绑定。
- 普通单模块和 Maven 多模块 Spring Boot 仓库都会递归扫描；是否能完整理解不取决于模块数量，而取决于文件上限、受支持文件类型及静态关系能否被保守解析。
- Embedding 是导入后的独立显式步骤；本地 Provider 可完成零费用闭环，真实语义 Provider 需要运行环境密钥。

## 接口

- `POST /api/projects/{projectId}/imports`：执行一次全量 Git 源码导入。
- `GET /api/projects/{projectId}/imports/latest`：查询该项目最近一次导入任务。
- `GET /api/projects/{projectId}/sources`：查询当前 revision 的源码与配置文档。
- `GET /api/projects/{projectId}/sources/{documentId}/symbols`：查询类、方法或配置项。
- `GET /api/projects/{projectId}/sources/references`：查询方法调用、数据访问与配置引用。

深层理解优先使用业务地图接口：

- `GET /api/projects/{projectId}/business-map`：按 Controller 组织业务模块和 HTTP 功能入口。
- `GET /api/projects/{projectId}/business-map/features/{featureId}`：返回功能调用流程、数据操作以及 Controller/Service/关联实现代码块。

当前分析模式为 `STATIC_CODE_EVIDENCE_V1`，只陈述源码和解析关系能够支持的事实与推断；需要逐文件排查时继续使用原始结构接口。

Vue 项目列表已接入导入接口。项目就绪后可点击“结构”，查看文件、包名、符号、注解和源码行号。

这些结构化结果是后续 RAG、项目业务地图和代码审查的证据层。文件、类和方法列表只能回答“代码在哪里”，不能直接回答“项目解决什么业务、流程如何流转”。中文深层理解必须继续基于调用关系、分层职责、状态字段和数据库关系进行归纳，并为每条判断保留源码证据。

## 真实验收记录

2026-08-03 使用本机 MySQL 和私有 GitHub 仓库 `MMDXTMM/devmate-ai` 完成验收：指定 `main` 分支克隆成功，识别 revision，扫描并保存 31 个 Java 文件元数据，`index_task` 状态为 `SUCCEEDED`，`project` 状态为 `READY`。

2026-08-04 完成 AST 解析闭环，Flyway V3 已在 H2 与本机 MySQL 26.7 执行成功。后端 33 个测试、前端 8 个测试和生产构建通过。
