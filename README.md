# DevMate AI

DevMate AI 是一个面向 Java 项目的智能代码审查 Agent 平台。它不是单纯的聊天机器人，而是结合静态分析、Git Diff、RAG 和受控 Tool Calling，发现普通编译检查难以覆盖的并发、事务、缓存、消息一致性、性能和架构风险。

当前已经完成基础工程、项目管理 CRUD、Git 源码导入、Java AST 解析、Git Diff 覆盖报告、**PMD 确定性静态分析 MVP**和第一版代码上下文关系图。现阶段采用模块化单体，先完成可运行、可测试、可演进的代码审查闭环，再根据真实压力拆分 Spring Cloud 服务。

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
- 独立 Vue 3 + TypeScript 项目管理前端
- Vue 源码文件与符号结构浏览器
- JGit 提交差异分析、变更行到 AST 符号映射和逐文件覆盖报告
- PMD 受控规则执行、Diff 行过滤、统一 Finding、去重和前端问题展示
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
- [前端开发与联调](docs/FRONTEND_DEVELOPMENT.md)
- [代码审查 Agent 设计](docs/CODE_REVIEW_DESIGN.md)
- [同类开源项目对比与路线优化](docs/OPEN_SOURCE_COMPARISON.md)
- [AI 辅助开发与项目归属规范](docs/AI_COLLABORATION_GUIDE.md)
- [项目决策与变更日志](docs/PROJECT_LOG.md)
- [架构决策记录](docs/ARCHITECTURE_DECISIONS.md)
