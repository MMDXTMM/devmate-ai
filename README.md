# DevMate AI

DevMate AI 是一个面向 Java 项目的智能代码审查 Agent 平台。它不是单纯的聊天机器人，而是结合静态分析、Git Diff、RAG 和受控 Tool Calling，发现普通编译检查难以覆盖的并发、事务、缓存、消息一致性、性能和架构风险。

当前已经完成基础工程和第一版数据库，下一阶段是 **项目管理业务闭环**。现阶段采用模块化单体，先完成可运行、可测试、可演进的代码审查闭环，再根据真实压力拆分 Spring Cloud 服务。

## 当前已具备

- Java 21 + Spring Boot 3.5
- Maven Wrapper，多台电脑无需预装 Maven
- Spring Web、Validation、Actuator
- MyBatis-Plus
- Flyway 数据库版本管理
- MySQL 运行配置模板
- H2 零配置开发模式
- 统一接口响应与全局异常处理
- 健康检查接口及基础测试

## 快速启动

本机需要 JDK 21 或更高版本。

```bash
./mvnw spring-boot:run
```

默认使用内存 H2 数据库，适合首次启动和接口调试。访问：

- `GET http://localhost:8080/api/health`
- `GET http://localhost:8080/actuator/health`

运行测试：

```bash
./mvnw test
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

3. 修改本机数据库账号密码，然后启动：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

`application-local.yml` 已被 Git 忽略；两台电脑可以拥有各自的数据库密码。数据库结构由 `src/main/resources/db/migration` 中的 Flyway 脚本同步，而不是提交数据库文件。

## 文档

- [项目总设计](docs/PROJECT_BLUEPRINT.md)
- [分阶段开发路线](docs/DEVELOPMENT_ROADMAP.md)
- [本地开发与多端同步](docs/LOCAL_DEVELOPMENT.md)
- [数据库设计](docs/DATABASE_DESIGN.md)
- [代码审查 Agent 设计](docs/CODE_REVIEW_DESIGN.md)
- [AI 辅助开发与项目归属规范](docs/AI_COLLABORATION_GUIDE.md)
- [项目决策与变更日志](docs/PROJECT_LOG.md)
- [架构决策记录](docs/ARCHITECTURE_DECISIONS.md)
