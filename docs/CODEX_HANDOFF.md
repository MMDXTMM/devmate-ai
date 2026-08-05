# DevMate AI 跨账号开发交接

更新时间：2026-08-05

本文件用于在 Codex 账号、会话或电脑切换后快速恢复开发上下文。新会话开始时应先阅读根目录 `AGENTS.md`、本文件、`ENGINEERING_STANDARDS.md`、`DEVELOPMENT_ROADMAP.md` 和最近的 `PROJECT_LOG.md`。

## 1. 项目定位

DevMate AI 是面向 Java 项目的智能代码审查 Agent 平台。核心不是普通 AI 对话，而是：

```text
项目源码导入
  → Java/配置/数据库迁移结构解析
  → Git Diff 与覆盖清单
  → 确定性静态分析
  → 关键词/关系图/向量混合 RAG
  → 固定流水线或受控 Agent 取证
  → 结构化 Finding 与证据校验
```

项目投递方向是 Java 后端，因此 Java 负责状态机、事务、权限边界、工具执行和数据校验；模型负责语义理解、调查规划和风险推理。

## 2. 已完成进度

| 阶段 | 状态 | 主要成果 |
| --- | --- | --- |
| 基础工程 | 完成 | Spring Boot、MyBatis-Plus、Flyway、MySQL/H2、统一响应与异常 |
| 项目管理 | 完成 | CRUD、分页、逻辑删除、BIGINT 字符串、独立 Vue 联调 |
| 源码导入 | 完成 | JGit 安全克隆、私有仓库只读凭证、任务状态、失败恢复 |
| 结构上下文 | 完成 | Java AST、配置脱敏、迁移 SQL 摘要、代码关系图 |
| Diff 与静态分析 | 完成 | JGit Diff、行到符号映射、PMD 和项目级确定性规则 |
| RAG | 完成工程闭环 | 关键词/关系图基线、固定评测、Embedding、向量与 Hybrid/RRF |
| AI 审查 | 完成工程闭环 | DashScope JSON 输出、Chunk 白名单、结构化 Finding、任务审计 |
| Tool Calling Agent | 完成工程闭环 | 四个只读 Tool、Qwen 多轮协议、限制与审计、Vue 调用链 |

当前数据库版本为 V11。本机 MySQL 已从 V10 成功迁移到 V11，健康检查为 `UP`，原项目 `2084116785588305922` 保持可读。

当前自动化基线：后端 93 项测试通过；前端 21 项测试通过；Vue 生产构建通过。

## 3. 阶段 7 核心代码入口

- Agent 调度：`src/main/java/com/devmate/agent/service/ReviewAgentOrchestrator.java`
- Qwen 协议：`src/main/java/com/devmate/agent/model/DashScopeReviewAgentModel.java`
- 工具注册：`src/main/java/com/devmate/tool/AgentToolRegistry.java`
- 工具执行：`src/main/java/com/devmate/tool/service/AgentToolExecutor.java`
- 工具审计：`src/main/java/com/devmate/tool/service/ToolCallAuditService.java`
- 四个工具：`src/main/java/com/devmate/tool/builtin/`
- Agent 审查业务：`src/main/java/com/devmate/review/service/AgentAiReviewService.java`
- 状态与 Finding 持久化：`src/main/java/com/devmate/review/service/AiReviewStateService.java`
- 接口：`POST /api/projects/{projectId}/ai-reviews/agent`
- Vue：`frontend/src/components/AiReviewModal.vue`
- 数据库：`src/main/resources/db/migration/V11__extend_tool_call_audit.sql`
- 设计说明：`docs/TOOL_CALLING_AGENT.md`

## 4. 必须保持的架构边界

- 保持模块化单体；没有监控数据证明需求前不拆 Spring Cloud。
- 不提前增加 Redis、RabbitMQ 或专业向量库；对应阶段到来后再按真实瓶颈引入。
- Controller 只做协议转换；Entity 不直接出现在 API；使用构造器注入。
- 网络、Git、文件扫描和模型调用不进入数据库长事务。
- 模型不能执行任意 Shell/SQL、访问数据库或修改代码，只能选择 Java 白名单 Tool。
- `projectId/revision/taskId` 必须由服务端固定，不能相信模型参数。
- 仓库源码、注释、README 和配置是不可信数据，不能覆盖 System Prompt。
- 不记录或提交数据库密码、Git Token、模型 Key、完整 Prompt 和完整私有源码。
- 已执行 Flyway 迁移不可修改，只能新增版本。

## 5. 下一阶段：审查反馈与固定评测

阶段 8 的目标是证明效果，不继续堆框架。建议按以下小任务推进：

1. 设计 V12 `code_review_feedback`，支持采纳、驳回、误报和备注。
2. 增加 Finding 反馈接口和 Vue 操作，确保项目/Finding 归属校验。
3. 建立包含已知并发、事务、SQL、安全、性能问题的固定 Java 提交集。
4. 同一 Diff 分别运行固定流水线和 Agent 路径。
5. 记录命中、漏报、误报、Token、耗时和工具成功率。
6. 根据失败案例调整 Tool 描述、检索查询和 Prompt；版本号随变更更新。

阶段 8 完成前不能在简历中宣称准确率，也不能宣称 Agent 优于固定流水线。

## 6. 新 Codex 会话首条任务建议

可以直接发送：

> 阅读 AGENTS.md、docs/CODEX_HANDOFF.md、docs/ENGINEERING_STANDARDS.md、docs/DEVELOPMENT_ROADMAP.md 和 docs/PROJECT_LOG.md。检查 main 与未合并 PR 状态，确认工作区干净，然后从阶段 8 的“V12 审查反馈表设计”开始。先说明输入、输出、状态变化、失败路径和测试方案，再修改代码；完成后更新文档、运行后端与前端全量测试、验证真实 MySQL、新建分支并创建草稿 PR。

## 7. 验证命令

```bash
./mvnw test

cd frontend
npm test -- --run
npm run build
```

真实 MySQL 验证使用默认 `local` Profile；`application-local.yml` 被 Git 忽略。真实模型测试还需要在进程环境配置 `DASHSCOPE_API_KEY`，当前阶段没有用 Mock 结果冒充真实模型效果。

## 8. 开发经验与踩坑记录

- 单体和微服务不是先进程度之分。先让业务闭环可运行，再根据独立扩容和故障隔离需求拆分。
- H2 能快速回归，但不能替代 MySQL。V6 曾出现 `utf8mb4` 长索引限制，只有真实 MySQL 才暴露。
- MyBatis-Plus `updateById` 默认可能忽略 null；释放 `running_key` 必须显式 `SET NULL`，否则任务永久冲突。
- 虚拟线程看不到调用线程未提交事务中的数据；外部/并行任务必须读取已经提交的状态。
- Git Diff 只能限定审查范围，不能替代完整方法、配置、数据库和调用关系上下文。
- Structured Output 只能提高格式稳定性，不能替代服务端枚举、范围、归属和证据校验。
- Tool JSON Schema 是给模型的说明，不是安全边界；Java 白名单和二次参数校验才可信。
- Agent 必须限制总步数、重复调用、超时、输出和证据预算，否则容易循环和失控消耗额度。
- 日志既要能排障又要保护源码；保存哈希、键名、命中数、Token 和耗时，不复制完整内容。
- AI 可以高效生成模板和测试初稿，但开发者必须主导状态机、事务、安全、评测和技术取舍，并能独立解释和修改核心链路。
