# DevMate AI

DevMate AI 是一个面向 Java 项目的智能代码审查 Agent 平台。它不是单纯的聊天机器人，而是结合静态分析、Git Diff、RAG 和受控 Tool Calling，发现普通编译检查难以覆盖的并发、事务、缓存、消息一致性、性能和架构风险。

当前已经完成基础工程、项目管理 CRUD、Git 源码导入、Java AST、配置与数据库迁移解析、Git Diff 覆盖报告、**PMD 确定性静态分析 MVP**、第一版代码关系图、带固定评测集的关键词/向量/关系图混合 RAG、证据约束的结构化 AI 审查，以及受控 Tool Calling Agent 工程闭环。现阶段采用模块化单体，先完成可运行、可测试、可演进的代码审查闭环，再根据真实压力拆分 Spring Cloud 服务。

## 当前已具备

- Java 21 + Spring Boot 3.5
- Maven Wrapper，多台电脑无需预装 Maven
- Spring Web、Validation、Actuator
- MyBatis-Plus
- Flyway 数据库版本管理
- MySQL 运行配置模板
- H2 零配置开发模式
- 统一接口响应与全局异常处理
- 项目创建、详情、分页筛选、修改和逻辑删除接口
- HTTPS Git 仓库校验、指定分支浅克隆和 Java 文件安全扫描
- 通过进程环境变量安全读取私有 GitHub 仓库，凭证不持久化
- 导入任务状态、Git revision 与源码文件元数据持久化
- 基于 JDK AST 解析类、构造器、方法、注解和准确源码行号
- `knowledge_chunk` 符号持久化与源码结构查询接口
- 方法调用、配置键和数据访问入口提取，以及保守的同类方法目标解析
- YAML/Properties 安全解析、敏感值脱敏，以及 Java 配置引用到候选定义的关联
- 迁移 SQL 表/列/索引摘要，以及 `@TableName/@Table` 到表定义的关联
- 独立 Vue 3 + TypeScript 项目管理前端
- Vue 源码文件与符号结构浏览器
- JGit 提交差异分析、变更行到 AST 符号映射和逐文件覆盖报告
- PMD 受控规则执行、Diff 行过滤、统一 Finding、去重和前端问题展示
- 事务自调用、循环数据访问和同步锁内 IO 的项目级风险规则
- 项目/revision 隔离的关键词与符号检索、Diff 种子和关系图上下文扩展
- Top-K、Token 预算、内容去重和可见裁剪原因
- 固定检索评测集以及 Recall@K、Precision@K、HitRate@K、MRR 指标
- 本地确定性 Embedding 与 DashScope 真实语义模型双 Provider
- 项目/revision/模型版本隔离的幂等向量索引和失败续建
- `LEXICAL/VECTOR/HYBRID` 三种检索模式、RRF 融合与显式降级
- Vue 上下文检索与证据浏览界面
- DashScope JSON 结构化 AI 审查 Provider、超时和失败审计
- 基于 Diff、静态分析与 RAG 的版本固定审查流水线
- Chunk 证据白名单、服务端位置映射和伪造引用拒绝
- AI Finding 的事实/推断/待验证、置信度、风险场景、建议与验证方法
- 数据库幂等运行键、超时任务恢复和 Token/耗时审计
- Vue 显式 AI 审查入口与结构化报告（打开弹窗不自动消耗额度）
- Qwen Function Calling 多轮协议与 Java 受控工具执行器
- Diff、静态分析、代码检索和项目结构四个只读 Tool
- Tool 参数校验、超时、调用/循环上限、证据预算和脱敏审计
- Vue 固定流水线/Agent 模式选择与工具调用链展示
- 健康检查接口及基础测试

## 快速启动

本机需要 JDK 21 或更高版本。

```bash
./mvnw spring-boot:run
```

默认激活 `local` Profile 并连接本机 MySQL。数据库连接信息保存在不会提交 Git 的 `application-local.yml`。访问：

- `GET http://localhost:8080/api/health`
- `GET http://localhost:8080/actuator/health`

运行测试：

```bash
./mvnw test
```

## 启动 Vue 前端

后端保持在 `http://localhost:8080` 运行，再打开一个终端：

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。开发服务器会把 `/api` 请求代理到 Spring Boot，因此前后端仍是两个独立工程，但不需要额外处理开发环境跨域。

前端检查命令：

```bash
cd frontend
npm test
npm run build
```

## 切换到本地 MySQL

1. 创建数据库：

```sql
CREATE DATABASE devmate
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

2. 复制配置模板：

```bash
cp src/main/resources/application-local.yml.example \
   src/main/resources/application-local.yml
```

3. 修改本机数据库账号密码，然后直接启动：

```bash
./mvnw spring-boot:run
```

`application-local.yml` 已被 Git 忽略；两台电脑可以拥有各自的数据库密码。数据库结构由 `src/main/resources/db/migration` 中的 Flyway 脚本同步，而不是提交数据库文件。

自动化测试显式使用 `test` Profile 和 H2 内存数据库，不会修改本地 MySQL 数据。

运行真实 AI 审查前，在启动后端的同一终端设置模型密钥：

```bash
export DASHSCOPE_API_KEY='<your-key>'
./mvnw spring-boot:run
```

密钥不能写入本地配置模板、数据库、前端或 Git。没有密钥时普通业务仍可启动，AI 审查会留下可查询的失败任务。

## 文档

- [开发贡献检查清单](CONTRIBUTING.md)
- [工程开发与运维规范](docs/ENGINEERING_STANDARDS.md)
- [运维手册](docs/OPERATIONS_RUNBOOK.md)
- [项目总设计](docs/PROJECT_BLUEPRINT.md)
- [分阶段开发路线](docs/DEVELOPMENT_ROADMAP.md)
- [面试导向学习与开发路线](docs/LEARNING_ROADMAP.md)
- [本地开发与多端同步](docs/LOCAL_DEVELOPMENT.md)
- [数据库设计](docs/DATABASE_DESIGN.md)
- [项目管理模块](docs/PROJECT_MANAGEMENT.md)
- [Git 源码导入闭环](docs/SOURCE_IMPORT.md)
- [Git Diff 与覆盖清单](docs/GIT_DIFF.md)
- [确定性静态分析](docs/STATIC_ANALYSIS.md)
- [代码上下文关系图](docs/CODE_CONTEXT_GRAPH.md)
- [配置上下文解析与关联](docs/CONFIGURATION_CONTEXT.md)
- [数据库结构上下文](docs/DATABASE_CONTEXT.md)
- [面向变更的检索基线与评测](docs/RETRIEVAL_BASELINE.md)
- [向量索引与混合 RAG](docs/VECTOR_RAG.md)
- [证据约束的 AI 代码审查 MVP](docs/AI_REVIEW_MVP.md)
- [受控 Tool Calling 代码审查 Agent](docs/TOOL_CALLING_AGENT.md)
- [前端开发与联调](docs/FRONTEND_DEVELOPMENT.md)
- [代码审查 Agent 设计](docs/CODE_REVIEW_DESIGN.md)
- [同类开源项目对比与路线优化](docs/OPEN_SOURCE_COMPARISON.md)
- [AI 辅助开发与项目归属规范](docs/AI_COLLABORATION_GUIDE.md)
- [项目决策与变更日志](docs/PROJECT_LOG.md)
- [架构决策记录](docs/ARCHITECTURE_DECISIONS.md)
- [Codex 跨账号开发交接](docs/CODEX_HANDOFF.md)
