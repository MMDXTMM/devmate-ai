# DevMate AI 跨账号交接与启动提示词

更新时间：2026-08-06

本文件解决两个问题：旧账号如何留下可验证状态，新账号如何在不重做工作的前提下继续开发。项目事实以 Git、测试和数据库为准，聊天记录只作为补充背景。

## 1. 推荐的继承方式

跨账号恢复使用四层材料：

1. GitHub 仓库：保存代码、迁移、测试和版本历史。
2. `docs/CODEX_HANDOFF.md`：保存当前进度、精确暂停点和下一小任务。
3. `docs/PROJECT_LOG.md` 与设计文档：保存重要取舍及其原因。
4. ChatGPT 导出的 `conversations.json`：只在需要追溯旧讨论时上传到新账号作参考。

这不是账号合并。不要依赖新账号自动获得旧账号的 Codex 任务、ChatGPT 侧边栏、记忆、文件、订阅或密钥。

### ChatGPT 记录迁移步骤

1. 旧账号进入 `Settings → Data Controls → Export Data` 并确认导出。
2. 从邮件下载 ZIP，解压后找到 `conversations.json`；文件过大时只保留本项目相关会话或拆分后上传。
3. 登录要继续使用的新个人账号，新建一段对话并上传该 JSON。
4. 同时上传本文件或粘贴第 4 节提示词，明确 JSON 只是背景，不能覆盖 Git 和测试证据。

官方支持这种“上传后作为参考”的方式，但它不会把旧对话恢复成独立聊天，也不会迁移侧边栏、记忆、GPT、设置、订阅或工作区权限。

### Codex 记录迁移方式

Codex 任务历史与 ChatGPT 历史分开，并与登录账号/工作区关联。目前不要把它当作可跨账号合并的数据源。新账号继续开发时：

1. 打开同一个本机仓库，或从 GitHub 重新克隆。
2. 使用第 3 节提示词启动新任务。
3. 让新账号先按 `CODEX_HANDOFF.md` 复核远端 main、PR、测试和数据库，再继续唯一下一任务。

如果旧 Codex 对话中有未写入仓库的重要决策，先人工提炼到交接文档；不要粘贴整段密钥、终端环境或私有源码。

## 2. 旧账号交接清单

- 确认当前任务达到一个可验证的小闭环，不留下“代码写了一半但文档说完成”的状态。
- 运行本次变更需要的测试与 `git diff --check`。
- 检查没有密码、Token、API Key、完整私有源码、构建产物或本地配置进入 Git。
- 提交、推送并记录分支、PR、commit、合并状态和测试结果。
- 更新 `CODEX_HANDOFF.md` 的恢复卡、暂停点和唯一下一任务。
- 业务、架构或开发顺序变化同步更新 `PROJECT_LOG.md` 和相关设计文档。
- 环境只记录变量名，例如 `DASHSCOPE_API_KEY`，不记录变量值。
- 如有未合并工作，明确写出阻塞原因、已完成部分、未完成部分和安全回滚方法。

## 3. 新 Codex 账号首条提示词

复制下面整段到新 Codex 任务：

```text
你将继续开发本机仓库 /Users/dengxintong/Documents/devmate-ai。

先不要修改代码。完整阅读根目录 AGENTS.md，以及：
- docs/CODEX_HANDOFF.md
- docs/ENGINEERING_STANDARDS.md
- docs/DEVELOPMENT_ROADMAP.md
- docs/REVIEW_EVALUATION.md
- docs/PROJECT_LOG.md
- benchmarks/review-fixtures/README.md
- benchmarks/review-fixtures/known-defects-v1/manifest.json
- benchmarks/review-fixtures/known-defects-v1/revisions.json

随后检查 pwd、git status、最近 5 个 commit、远端 main、未合并 PR 和工作区修改。以“远端 main/已合并 PR/测试/数据库 > 交接文档 > 聊天记录”为事实优先级；不要因为交接文字声称完成就跳过验证，也不要覆盖用户的无关修改。

项目是 Spring Boot 模块化单体的智能 Java 代码审查 Agent 平台。保持 Controller → Service → Mapper、DTO 隔离、构造器注入、短事务、受控只读 Tool、Flyway 只增不改、BIGINT 前端字符串等边界。不要新增微服务、MQ、Redis、专业向量库、任意 Shell/SQL 或自动改码，除非当前需求有验证依据。不要记录或提交密码、Token、完整 Prompt 和完整私有源码。

当前已完成 V13 评测工作台、可复现 Git fixture、H2 和隔离 MySQL 26.7 的真实导入/Diff 验收，以及 V14 标准答案证据约束。公开样本仓库是 https://github.com/MMDXTMM/devmate-review-benchmark，case-001 至 case-008 已发布。最近合并基线为 PR #17、main@7e8a92e；PR #18 已从 codex/review-manifest-ingestion 打开，提交 25db123 已实现 manifest 标准答案幂等同步。自动化基线为后端 111 项、前端 29 项、Node 28 项和 Vue 生产构建通过。

H2 与隔离 MySQL 均已完成 8 个 case 的真实 GitHub 导入和默认 HEAD^ → HEAD Diff，结果为 8 PASS、6 FULL、2 PARTIAL。ReviewEvaluationCaseService 已强制标准答案文件属于对应 Diff，并要求标注范围、目标变更行和持久化 TARGET Chunk 形成三重交集。隔离 MySQL 首次同步标准答案为 8 created/verified，立即重跑为 8 reused/verified；最终为 7 条 DEFECT、1 条 CLEAN。下一任务先实现受控 A/B 执行器和服务端预期 Diff ID/revision 校验，使用 Mock/Fake 验证全批预检、漂移拒绝、响应丢失恢复、失败即停和微平均聚合；本任务不调用真实模型。之后才在相同项目、revision、Diff、模型配置和数据集条件下运行真实 FIXED 与 AGENT，冻结并分别记录两条路径各自的 Prompt/检索版本，保存评测快照并人工解释失败案例。

开始执行前先用简短中文汇报你核实到的仓库状态、A/B 可比条件、调用顺序、失败路径和验收方案。执行器完成前不要调用真实模型。真实运行属于显式、有成本的后续操作：先确认 DASHSCOPE_API_KEY 只存在于进程环境，并向用户说明将运行 16 个审查任务、Agent 可能产生多轮模型请求及其费用风险；不要把 Mock 当真实结果。完成一个小任务后更新 docs/CODEX_HANDOFF.md 和必要的 PROJECT_LOG/设计文档，运行相关测试与 git diff --check，提交独立 codex/ 分支并创建 PR。最终用中文说明完成内容、验证结果、失败案例、下一步，以及我为了 Java 面试必须理解的 3—5 个核心点。
```

## 4. 新 ChatGPT 账号首条提示词

ChatGPT 适合继续项目理解、学习路线、面试训练和需求讨论。若它不能访问本机仓库，不要让它声称已经验证代码。

```text
我正在开发 DevMate AI：一个基于 Spring Boot 模块化单体、Git Diff、静态分析、混合 RAG 和受控 Tool Calling 的 Java 智能代码审查 Agent 平台。

我会上传旧账号导出的 conversations.json，并提供 GitHub 仓库与以下文件：docs/CODEX_HANDOFF.md、docs/ENGINEERING_STANDARDS.md、docs/DEVELOPMENT_ROADMAP.md、docs/PROJECT_LOG.md。请把导出的对话仅作为历史参考，把仓库代码、测试结果、Flyway 版本和交接文档作为项目事实。

你的职责是帮助我理解业务闭环、Java/AI 架构取舍、学习路线和面试表达；不要把未实现能力包装进简历，不要假定 Mock 测试等于真实模型效果，也不要建议模型直接执行任意 Shell/SQL、访问数据库或自动提交代码。

每次回答请区分：已经实现并验证、计划实现、我必须亲自理解、AI 可以辅助。若信息冲突，先指出冲突并要求用仓库或测试证据确认。
```

## 5. 紧急精简提示词

当上下文空间有限时使用：

```text
继续 /Users/dengxintong/Documents/devmate-ai。先读 AGENTS.md 和 docs/CODEX_HANDOFF.md，检查 main、PR、git status 和测试证据，不要直接改代码或重做已完成工作。事实优先级：Git/测试/数据库 > 文档 > 旧对话。当前唯一任务以 CODEX_HANDOFF 的“精确暂停点/下一阶段”为准。保持模块化单体、短事务、Flyway 只增不改、受控 Tool 和密钥不入库；完成后测试、更新交接文档并提交独立 PR。
```

## 6. 交接成功标准

新账号能在不读取全部历史聊天的情况下回答以下问题，才算恢复成功：

1. 当前 `main` 的最新已合并能力是什么？
2. 数据库 Flyway 版本和测试基线是什么？
3. 当前精确暂停点和唯一下一小任务是什么？
4. 哪些能力尚未真实验证，不能写入简历？
5. 当前任务的输入、输出、状态机、失败路径和测试方案是什么？

任何答案都必须能指向 commit、PR、测试或仓库文档，不能只引用聊天记忆。
