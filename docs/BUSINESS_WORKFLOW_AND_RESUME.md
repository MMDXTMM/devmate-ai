# 基础业务闭环、简历证据与问题复盘

## 1. 当前可运行的业务闭环

DevMate AI 当前已经具备一条可演示、可测试的 Java 代码审查基础流程：

```text
注册/登录并建立服务端身份
  → 创建有 OWNER 关系的 Git 项目
  → 导入源码并解析 AST、配置和迁移 SQL
  → 比较最近两个 Git revision 并生成覆盖清单
  → PMD 与项目规则检查本次变更
  → 为当前 revision 建立向量索引
  → FIXED 或 Agent 模式执行证据约束的 AI 审查
  → 展示结构化 Finding、Tool 调用和覆盖范围
  → 开发者反馈
  → 固定标准答案评测
```

Vue 的“基础分析”会按依赖顺序执行前四个确定性步骤：源码导入、Diff、静态分析和向量索引。任一步骤失败后立即停止，后续步骤不会在错误前置状态上继续运行。完成后打开 AI 审查工作台，由用户显式选择 FIXED 或 AGENT 并确认模型调用，避免打开页面或基础分析时产生费用。

后端 `BasicReviewWorkflowTest` 从真实 Git 两次提交开始，贯通数据库状态、AST、Diff、PMD、本地 Embedding、RAG 和 AI 审查任务；只有模型最终响应使用 Fake，因此该测试证明工程链路，不代表真实模型准确率。

## 2. 当前闭环边界

已经形成闭环：

- BCrypt 登录认证、有期限 JWT、Vue 会话和项目成员数据隔离。
- 项目 CRUD、Git 导入、失败状态和增量复用。
- Diff 范围、BASE/TARGET 行号、完整/部分/跳过覆盖清单。
- PMD 确定性检查和事务、循环查询、锁内 IO 项目规则。
- 关键词、关系图、向量与 Hybrid RAG，含项目/revision 隔离和预算裁剪。
- FIXED 与受控 Tool Calling Agent 两种审查路径。
- Finding 证据白名单、反馈、固定缺陷集和评测计算。
- 前端到后端的基础分析编排，以及成功、失败任务的查询。

尚未形成生产闭环：

- 没有 Refresh Token、服务端 Token 撤销、成员管理和管理员权限，不能宣称完整企业 IAM。
- 同步导入仍需克隆并扫描全部允许文件；是否使用 RabbitMQ 要由耗时和失败数据决定。
- 没有 Docker/Nginx 部署与线上监控验收。
- 没有运行真实模型 canary 和完整 A/B，不能宣称准确率或 Agent 优于 FIXED。

## 3. 可直接使用的简历版本

### DevMate AI 智能代码审查 Agent 平台

**项目描述：** 基于 Spring Boot 构建面向 Java 项目的智能代码审查平台，结合 Git Diff、静态分析、混合 RAG 与受控 Tool Calling，对变更代码生成包含真实位置、代码证据、风险场景和验证方法的结构化审查结果。

**技术栈：** Java 21、Spring Boot、MyBatis-Plus、MySQL、Flyway、JGit、PMD、Vue 3、TypeScript、Embedding、RAG、LLM Tool Calling

- 设计“短事务准备 → Git/文件/模型外部执行 → 短事务完成或失败”的任务状态机，避免耗时外部调用长期占用数据库连接，并保存可查询的失败状态和分阶段耗时。
- 基于 Spring Security、BCrypt 和有期限 JWT 实现登录认证，通过统一项目路径门禁与 Service 角色校验隔离源码、检索和审查数据；项目创建事务同时写入所有者和 OWNER 成员关系。
- 基于 JGit 与 JDK AST 实现源码导入和 Diff 精确映射，以类、方法、包声明和 import 语义块处理新增、修改、删除与 Rename，并输出 `FULL/PARTIAL/SKIPPED` 覆盖清单，避免静默漏审。
- 结合 PMD 与项目级规则检查确定性问题，仅保留命中本次变更行的 Finding；使用调用、配置和数据库关系补充事务失效、循环查询与锁内 IO 的上下文证据。
- 实现项目和 revision 隔离的关键词、向量与关系图混合检索，通过 Top-K、RRF、内容去重和 Token 预算为审查模型提供受控上下文，并支持跨 revision 精确复用未变化向量。
- 构建 FIXED 与 Tool Calling Agent 两种审查路径，使用 Java 工具白名单、参数二次校验、调用/超时上限和 Chunk 证据校验，记录 Token、延迟和工具链路，并通过固定缺陷集计算 TP/FP/FN、Precision、Recall 与 F1。

最后一条只能描述“具备评测能力”，当前不能填写具体准确率，也不能写“Agent 效果优于固定流水线”。

## 4. 面试可讲的问题与解决方法

### 问题一：H2 通过不等于 MySQL 可用

V6 在真实 MySQL 上遇到 `utf8mb4` 长路径联合索引超限。最终保留完整路径用于展示，额外保存 SHA-256 `path_hash` 用于索引和精确匹配。V18 又发现 H2 不支持 MySQL 的单条多列 `ADD COLUMN` 写法，迁移改为六条双方都支持的语句。结论是 H2 负责快速回归，迁移、索引和锁语义必须补真实 MySQL 验收。

### 问题二：同 revision 重导导致向量消失

旧实现删除并重建 Chunk，外键级联同时删除向量。修复后同 revision 走零重写路径，Document、Chunk、Reference 和 Vector ID 保持不变；新 revision 只解析变化文件，并为复用文件重新绑定本 revision 的引用目标。

### 问题三：只保存 Diff 目标行会漏掉删除代码

JGit `Edit` 同时包含 BASE 删除范围和 TARGET 新增范围。系统分别保存两侧范围，目标版本复用已入库 AST，基准版本从 Git Blob 按需解析，从而让删除文件也有可验证证据而不复制整套历史知识库。

### 问题四：模型会伪造文件、行号和证据

模型只能引用服务端提供的 Chunk ID。Java 再校验项目、revision、Diff、Chunk 白名单和字段范围，文件路径及行号由 AST/Diff 证据映射；无效 Finding 被拒绝但保留拒绝数量，不让结构化 JSON 被误当作可信业务数据。

### 问题五：付费 POST 响应丢失会造成重复调用

每次 AI 审查绑定 UUID v4 `attemptKey`、预期 Diff ID 和 revision，并由数据库唯一键兜底。客户端遇到模糊响应不重发付费 POST，只按 attemptKey 精确回读；服务端在模型调用前再次检查 Diff 漂移。

### 问题六：为什么暂时没有直接上 RabbitMQ

异步化会引入投递确认、重复消费、租约、重试和死信等新一致性问题。当前先记录 clone、scan、plan、parse、persist 和 total 耗时，并保留失败任务；只有数据证明同步链路影响响应或吞吐后再引入 MQ，技术选型才有真实依据。

### 问题七：外部模型返回 429 时如何处理

429 可能表示请求频率达到上限或账号额度不足。系统不会自动重试付费 AI、Agent 或 Embedding 请求，而是转换为可读错误、保存失败任务并停止后续调用，避免限流期间继续放大请求量。恢复前需要等待服务商窗口或检查额度；Codex 平台账号限流不属于项目代码能够解除的范围。

### 问题八：覆盖报告为什么出现 PARTIAL，如何修复

首次公开 8 场景验收只有 6 个 `FULL`，`case-005/008` 的新增或删除 import 行不在 class/method Chunk 内。系统没有直接把类范围扩大到文件头，而是新增精确 `FILE_HEADER` 和逐条 `IMPORT` Chunk，并分别验证 TARGET 持久化映射与 BASE Git Blob 内存映射。重新全量导入后得到 `8 FULL / 0 PARTIAL`；这说明修复的是证据模型，而不是美化统计结果。

## 5. 演示顺序

1. 创建一个至少有两个提交的 Java Git 项目。
2. 点击“基础分析”，展示导入耗时、Diff 覆盖、静态 Finding 和向量结果。
3. 查看源码结构和关系，解释 AST 与固定字符切片的差别。
4. 打开 AI 审查，明确说明这一步才会调用模型；选择 FIXED 或 AGENT。
5. 展示证据、风险场景、验证方法和 Tool 调用链。
6. 保存反馈，再打开评测面板说明标准答案、误报和漏报如何计算。

面试前应能不依赖 AI 解释正常路径、失败路径、事务边界、三张核心任务表，并现场修改一个校验或测试。
