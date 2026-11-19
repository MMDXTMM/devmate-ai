# DevMate AI

[![CI](https://github.com/MMDXTMM/devmate-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/MMDXTMM/devmate-ai/actions/workflows/ci.yml)

DevMate AI 是一个**面向新人的跨语言项目理解与 Java 代码审查工具**：先解释项目做什么、有哪些能力、事情如何发生，再按需进入同一版本的源码和问答。通用拆解适用于可读取的文本项目；Java 额外提供 AST、静态入口、调用关系及 Diff 审查，其他语言当前使用带真实行号的文本片段。

导入 Git 仓库后，打开工作台读取已保存导览。首次生成或更新须点击标有“使用 API 额度”的按钮；阅读、刷新、打开历史不会自动调用模型。首层显示用途、模块职责和一条主流程，业务地图、学习建议与术语按需展开；选择模块后查看规则、条件与失败结果，再点击依据核对源码。项目、模块、流程和会话有可恢复的地址。

导览使用 `project-guide-v7` Prompt、固定快照和有界取证，优先核心实现。模型归纳能力，宿主校验引用并回填位置；区分源码观察、测试预期、文档描述和未核实内容。精确符号问题优先查定义，中文业务问题可借助本账号同版本报告定位；本地代码哈希向量不宣称理解语义。回答引用可定位，源码可按需续读。

完整方法见 [跨语言调研与方案](docs/UNIVERSAL_PROJECT_GUIDE_PLAN.md)，可携带 [project-guide Skill](docs/skills/project-guide/SKILL.md) 和 [独立提示词](docs/PROJECT_GUIDE_PROMPT.md) 可交给具备文件读取能力的 AI；未自动安装到全局。产品范围与实际验证见 [接入记录](docs/PROJECT_GUIDE_IMPLEMENTATION.md)、[体验验收矩阵](docs/BEGINNER_EXPERIENCE_ACCEPTANCE.md) 和 [论文方法与证据](docs/PROJECT_UNDERSTANDING_THESIS_EVIDENCE.md)。

“生成新项目”支持真实 AI 多轮选择题：先聊用途和功能，再选编程语言、架构框架、存储与运行方式；确认版本后后台生成源码，提供文件预览与 ZIP 下载。支持按需求选择不同技术栈；生成后可打开“验证与修复”，在受限容器中验证支持的项目，继续对话修改、比较和恢复版本，并生成新人学习导览与运行清单。详见 [多语言项目生成](docs/PROJECT_GENERATION.md)。

## 当前主要使用方式

### 审查 Java 代码变更

```text
导入 Git 仓库 → 解析源码与项目关系 → 生成 Git Diff
→ 静态规则检查 → Hybrid RAG 检索上下文
→ Agent 调用受控只读 Tool → 结构化审查报告 → 人工反馈与评测
```

审查结论必须给出文件、行号、代码证据、风险场景、修改建议和验证方法；第一版不自动修改或提交用户代码。

## 项目入口

### 通过选择题生成新项目（多语言）

```text
描述项目 → 业务选择 → 语言推荐 → 架构与框架 → 存储与运行
→ 确认方案 → 后台生成 → 文件预览与源码 ZIP
→ 可选隔离验证 / 有限修复 → 修改计划与版本 → 学习导览与运行向导
```

每轮问题由当前账户的模型依据历史选择生成，提供推荐理由、取舍和自定义补充；历史会话可继续。生成目录与导入项目隔离，文件经过路径、大小和基本交付物检查；只有用户显式启动验证时才由固定容器运行器执行支持的检查；不自动下载依赖，不承诺任意技术栈均可直接运行。

### 理解已有项目（跨语言）

```text
导入 Git 仓库 → 安全索引 → 显式生成用途/模块/流程导览
→ 选择一件事 → 查看规则与依据 → 结合真实代码继续问
```

“向项目提问”会创建固定项目版本和模型的持久化对话。每一轮根据已保存报告的业务线索定位真实代码，未匹配时执行 Hybrid RAG，再通过 Spring AI 流式调用模型，并以 SSE 逐字展示回答和真实文件/行号证据；历史可按标题查找，刷新或关闭后仍可继续追问。也可从报告中的某条业务直接进入聊天。聊天工作台借鉴 Ragent 交互，支持日期分组、历史改名/删除、已配置模型选择、Markdown 表格与代码复制、代码证据侧栏，以及停止生成并保存半段回答，迁移范围与验收见 [工作台迁移记录](docs/CHAT_WORKSPACE_MIGRATION.md)。MCP 只读工具服务属于下一闭环，尚未作为已完成功能发布，设计见 [多轮项目 Agent、SSE 与 MCP](docs/PROJECT_CONVERSATION_SSE_AND_MCP.md)。

## 当前已具备

- Java 21 + Spring Boot 3.5 + Spring AI 1.1.8
- Maven Wrapper，多台电脑无需预装 Maven
- Spring Web、Validation、Actuator
- Springdoc OpenAPI 与 Swagger UI，覆盖业务接口并有全局契约回归门禁
- MyBatis-Plus
- Flyway 数据库版本管理
- MySQL 运行配置模板
- H2 零配置开发模式
- 统一接口响应与全局异常处理
- 安全 `X-Request-Id`、MDC 日志关联和前端错误定位
- Spring Security、BCrypt、有期限 JWT、Vue 登录态和项目成员隔离
- 项目创建、详情、分页筛选、修改和逻辑删除接口
- HTTPS Git 仓库校验、指定分支浅克隆、Java AST 与常见源码/文档/清单的安全文本导入
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
- 项目版本固定的持久化多轮对话、业务报告辅助代码检索与 Spring AI 流式逐字回答
- 基于 POST + JWT 的 SSE 事件流、代码证据展示、失败落库和网络重试幂等
- 项目版本固定、证据 ID 白名单、付费请求幂等、并发限制、超时恢复及 Token/耗时审计
- Diff、静态分析、代码检索和项目结构四个只读 Tool
- Tool 参数校验、超时、调用/循环上限、证据预算和脱敏审计
- Vue 固定流水线/Agent 模式选择与工具调用链展示
- Finding 采纳、驳回、误报、稍后处理和备注持久化
- `DEFECT/CLEAN` 固定评测用例、FIXED/AGENT 执行模式快照与幂等运行
- TP/FP/FN、Precision/Recall/F1、Token、耗时和 Tool 成功率评测
- 健康检查接口及基础测试
- GitHub Actions 后端、前端和 Benchmark 三路持续集成
- 一句话创建多语言项目生成会话，真实 AI 分轮推荐语言、架构、框架与存储，方案版本化保存和确认锁定
- 单选/多选追问、推荐理由、选项影响、AI 代选与自定义补充，支持历史继续
- 确认后后台生成独立源码、文件预览与 ZIP 下载；可选隔离验证（Python unittest、Node 原生 JS 测试、Java 无依赖源码编译）和最多 2 次 AI 修复
- 修改计划确认后生成独立版本，查看文件差异、恢复旧版本；提供版本专属学习导览、运行清单、报错解释、真实任务阶段与 Token 用量及取消
- `guided-requirement-v1` 历史问题与纯文本答案兼容回读

## 快速启动

本机需要 JDK 21 或更高版本，以及一个本地 MySQL。

推荐直接运行一键启动脚本（兼容 sh / bash / zsh）：

```bash
./scripts/start-local.sh
```

脚本会在启动前依次检查 JDK 版本、本地数据库配置和数据库端口，并在缺少配置时自动从
`application-local.yml.example` 生成 `application-local.yml`。不满足的前置条件会给出中文提示和
可直接执行的修复命令，而不是直接抛出 Java 异常栈。启动成功后终端会打印健康检查、接口文档地址
和前端启动命令。

首次运行会在 Git 忽略且仅当前用户可读的 `.devmate-local-secrets` 中生成稳定的 JWT 和模型加密
密钥，后续重启继续复用，避免已保存的模型 API Key 因加密主密钥变化而失效。脚本首次运行还会以
隐藏输入方式询问本机 MySQL 密码并保存在同一文件中。该文件不能提交或分享。

如果本机 MySQL 不在默认的 3306 端口（例如使用 3307），请先修改 `application-local.yml` 中的
`jdbc:mysql://localhost:3306/devmate`；脚本会按该文件里的真实端口做连通性检查。

也可以手动注入环境变量后启动：

```bash
export DEVMATE_JWT_SECRET='<at-least-32-random-characters>'
export DEVMATE_MODEL_ENCRYPTION_SECRET='<another-stable-32-character-secret>'
./mvnw spring-boot:run
```

默认激活 `local` Profile 并连接本机 MySQL。数据库连接信息保存在不会提交 Git 的 `application-local.yml`；安全默认开启，JWT 和模型 API Key 加密密钥都必须至少 32 个字符、通过环境注入且不能写入 Git。`DEVMATE_MODEL_ENCRYPTION_SECRET` 必须长期保持稳定，否则已保存的模型 Key 需要重新填写。访问：

- `GET http://localhost:8080/api/health`
- `GET http://localhost:8080/actuator/health`
- OpenAPI：`http://localhost:8080/v3/api-docs`
- Swagger UI：`http://localhost:8080/swagger-ui.html`

本地默认开启接口文档；生产环境设置 `DEVMATE_OPENAPI_ENABLED=false` 关闭 OpenAPI 和 Swagger UI。

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

3. 修改本机数据库账号、密码和端口（模板默认 `3306`），然后启动：

```bash
./scripts/start-local.sh
```

`application-local.yml` 已被 Git 忽略；两台电脑可以拥有各自的数据库密码。数据库结构由 `src/main/resources/db/migration` 中的 Flyway 脚本同步，而不是提交数据库文件。

自动化测试显式使用 `test` Profile 和 H2 内存数据库，不会修改本地 MySQL 数据。

运行真实 AI 审查前，在页面“大模型连接”中选择提供方、填写 API Key、保存并执行连接测试。模型配置按账户隔离，Key 加密后存储且不回显；服务端加密主密钥仍只能由环境提供。没有启用模型时普通业务仍可运行，AI 审查会返回可读配置提示。

## 文档

- [开发贡献检查清单](CONTRIBUTING.md)
- [工程开发与运维规范](docs/ENGINEERING_STANDARDS.md)
- [API 契约治理](docs/API_CONTRACTS.md)
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
