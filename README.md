# DevMate AI

[![CI](https://github.com/MMDXTMM/devmate-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/MMDXTMM/devmate-ai/actions/workflows/ci.yml)

DevMate AI 当前聚焦为一个**基于 RAG 的 Java 代码审查 Agent**：导入 Java Git 仓库后，通过 Git Diff 限定变更范围，结合 AST、PMD、调用关系和混合检索补充项目上下文，再由受控 Tool Calling Agent 输出带真实代码证据的结构化审查结论。

当前已经完成登录认证、项目管理、Git 导入、Java AST、Diff 精确映射、PMD 与项目规则、混合 RAG、证据约束的 AI 审查、受控 Tool Calling、反馈和固定评测集等工程底座。接下来的开发不再扩张产品范围，而是把已有能力收敛成可一键演示、可量化验证、本人能够完整解释的求职项目闭环。

审查工作台现已提供一键编排入口，按源码刷新、Diff、静态分析、Embedding 和 Agent 审查顺序执行，并记录幂等、并发冲突、失败阶段和恢复动作。项目理解入口默认提供新人导览：先说明项目定位和核心业务流程，再展示接口、状态模型、数据库表、失败信号和推荐阅读顺序；用户还可以显式调用当前账户选择的模型，由 Spring AI 根据静态业务地图与 RAG 代码证据生成中文深度报告。模型只能引用服务端提供的 Chunk ID，文件、行号和代码块由 Java 回填，逐文件结构只作为辅助排查入口。

“一句话生成 Spring Boot 项目”已保留版本化需求澄清和确认能力，但从当前开发主线冻结。不会删除相关代码；以后有时间再继续接入真实需求模型、工程生成和编译测试。在完整生成闭环完成前，不把它写入简历能力。

## 当前主要使用方式

### 审查 Java 代码变更（当前主线）

```text
导入 Git 仓库 → 解析源码与项目关系 → 生成 Git Diff
→ 静态规则检查 → Hybrid RAG 检索上下文
→ Agent 调用受控只读 Tool → 结构化审查报告 → 人工反馈与评测
```

审查结论必须给出文件、行号、代码证据、风险场景、修改建议和验证方法；第一版不自动修改或提交用户代码。

## 保留入口

### 生成新的 Spring Boot 项目（冻结在需求确认阶段）

```text
一句话需求 → 需求/架构草案 → Agent 反向提问 → 用户确认
→ 生成完整 Spring Boot 工程 → 编译测试 → 质量报告
```

第一版只生成新的 Spring Boot 模块化单体，不修改导入项目，不生成 Spring Cloud。

### 理解已有 Java 项目（审查前置能力）

```text
导入 Git 仓库 → 结构化解析 → 中文项目报告 → 代码证据问答
→ 找到业务入口和继续开发范围
```

## 当前已具备

- Java 21 + Spring Boot 3.5 + Spring AI 1.1.8
- Maven Wrapper，多台电脑无需预装 Maven
- Spring Web、Validation、Actuator
- MyBatis-Plus
- Flyway 数据库版本管理
- MySQL 运行配置模板
- H2 零配置开发模式
- 统一接口响应与全局异常处理
- 安全 `X-Request-Id`、MDC 日志关联和前端错误定位
- Spring Security、BCrypt、有期限 JWT、Vue 登录态和项目成员隔离
- 项目创建、详情、分页筛选、修改和逻辑删除接口
- HTTPS Git 仓库校验、指定分支浅克隆和 Java 文件安全扫描
- 通过进程环境变量安全读取私有 GitHub 仓库，凭证不持久化
- 导入任务状态、Git revision 与源码文件元数据持久化
- Git revision 与结构解析版本双重标识、零重写幂等导入和受控显式重建
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
- Spring AI `ChatClient.responseEntity` 结构化审查、超时和失败审计
- 基于 Diff、静态分析与 RAG 的版本固定审查流水线
- Chunk 证据白名单、服务端位置映射和伪造引用拒绝
- AI Finding 的事实/推断/待验证、置信度、风险场景、建议与验证方法
- 数据库幂等运行键、超时任务恢复和 Token/耗时审计
- Vue 显式 AI 审查入口与结构化报告（打开弹窗不自动消耗额度）
- Spring AI Tool Calling 多轮协议与 Java 受控工具执行器
- DeepSeek、通义千问和 OpenAI 模型连接中心，支持账户级加密配置、模型切换和显式连接测试
- Spring AI 中文项目深度报告，包含核心业务流程、阅读顺序、风险边界和服务端回填的真实代码证据
- 项目版本固定、证据 ID 白名单、付费请求幂等、并发限制、超时恢复及 Token/耗时审计
- Diff、静态分析、代码检索和项目结构四个只读 Tool
- Tool 参数校验、超时、调用/循环上限、证据预算和脱敏审计
- Vue 固定流水线/Agent 模式选择与工具调用链展示
- Finding 采纳、驳回、误报、稍后处理和备注持久化
- `DEFECT/CLEAN` 固定评测用例、FIXED/AGENT 执行模式快照与幂等运行
- TP/FP/FN、Precision/Recall/F1、Token、耗时和 Tool 成功率评测
- 健康检查接口及基础测试
- GitHub Actions 后端、前端和 Benchmark 三路持续集成
- 一句话创建 Java 项目生成会话、版本化需求方案和确认锁定
- 规则驱动的单选/多选/自由文本澄清、AI 推荐、选项影响和 AI 代选
- `guided-requirement-v1` 历史问题与纯文本答案兼容回读

## 快速启动

本机需要 JDK 21 或更高版本。

```bash
export DEVMATE_JWT_SECRET='<at-least-32-random-characters>'
export DEVMATE_MODEL_ENCRYPTION_SECRET='<another-stable-32-character-secret>'
./mvnw spring-boot:run
```

默认激活 `local` Profile 并连接本机 MySQL。数据库连接信息保存在不会提交 Git 的 `application-local.yml`；安全默认开启，JWT 和模型 API Key 加密密钥都必须至少 32 个字符、通过环境注入且不能写入 Git。`DEVMATE_MODEL_ENCRYPTION_SECRET` 必须长期保持稳定，否则已保存的模型 Key 需要重新填写。访问：

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

首次访问先注册账号。新账号只能看到自己创建或作为成员加入的项目；历史上 `owner_id` 为空的项目不会自动暴露，需要后续显式认领。

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

运行真实 AI 审查前，在页面“大模型连接”中选择提供方、填写 API Key、保存并执行连接测试。模型配置按账户隔离，Key 加密后存储且不回显；服务端加密主密钥仍只能由环境提供。没有启用模型时普通业务仍可运行，AI 审查会返回可读配置提示。

## 文档

- [开发贡献检查清单](CONTRIBUTING.md)
- [工程开发与运维规范](docs/ENGINEERING_STANDARDS.md)
- [运维手册](docs/OPERATIONS_RUNBOOK.md)
- [HTTP 请求追踪与日志关联](docs/REQUEST_CORRELATION.md)
- [持续集成与远端质量门禁](docs/CONTINUOUS_INTEGRATION.md)
- [项目总设计](docs/PROJECT_BLUEPRINT.md)
- [双模板产品与 Spring Boot 工程生成 Agent](docs/PRODUCT_MODES_AND_GENERATION_AGENT.md)
- [分阶段开发路线](docs/DEVELOPMENT_ROADMAP.md)
- [面试导向学习与开发路线](docs/LEARNING_ROADMAP.md)
- [本地开发与多端同步](docs/LOCAL_DEVELOPMENT.md)
- [数据库设计](docs/DATABASE_DESIGN.md)
- [源码结构版本与安全重建](docs/SOURCE_STRUCTURE_VERSIONING.md)
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
- [代码审查反馈闭环](docs/REVIEW_FEEDBACK.md)
- [代码审查固定缺陷集与效果评测](docs/REVIEW_EVALUATION.md)
- [前端开发与联调](docs/FRONTEND_DEVELOPMENT.md)
- [代码审查 Agent 设计](docs/CODE_REVIEW_DESIGN.md)
- [基础业务闭环、简历证据与问题复盘](docs/BUSINESS_WORKFLOW_AND_RESUME.md)
- [认证与项目访问控制](docs/AUTHENTICATION_AND_ACCESS_CONTROL.md)
- [账户级大模型连接与 Spring AI 接入](docs/MODEL_CONNECTIONS.md)
- [同类开源项目对比与路线优化](docs/OPEN_SOURCE_COMPARISON.md)
- [AI 辅助开发与项目归属规范](docs/AI_COLLABORATION_GUIDE.md)
- [项目决策与变更日志](docs/PROJECT_LOG.md)
- [架构决策记录](docs/ARCHITECTURE_DECISIONS.md)
- [Codex 跨账号开发交接](docs/CODEX_HANDOFF.md)
