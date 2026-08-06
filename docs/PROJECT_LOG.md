# 项目决策与变更日志

本文件记录影响项目定位、架构、开发顺序或简历目标的重要变化。普通代码修改不需要逐条记录。

## 2026-08-06：补齐导入耗时与基础审查业务闭环

- V18 为导入任务增加 clone、scan、plan、parse、persist 和 total 六段耗时，成功、失败和同 revision 幂等路径均持久化可观测数据。
- 耗时使用 `System.nanoTime()`，网络、文件和解析继续位于数据库事务之外；指标先用于判断是否真的需要 RabbitMQ，而不是为简历提前堆组件。
- 首次测试发现 H2 不支持 MySQL 风格的单条多列 `ADD COLUMN`，V18 在尚未提交前改为六条兼容语句，定向迁移测试通过。
- Vue 增加“基础分析”，按源码导入、Diff、静态分析和向量索引顺序执行，失败即停；模型审查继续要求用户显式触发。
- 增加业务端到端测试，从真实 Git 两次提交贯通 AST、Diff、PMD、本地向量、RAG 和 Fake 模型任务落库。该测试只证明工程闭环，不作为真实模型准确率证据。
- 新增 `BUSINESS_WORKFLOW_AND_RESUME.md`，明确可写简历能力、暂不能宣称的结果、演示顺序和六组问题解决案例。
- 全量验证为后端 123 项、前端 39 项、Benchmark Node 48 项和 Vue 生产构建全部通过。
- 隔离 MySQL 26.7 已完成 V17→V18，健康状态为 `UP`；六列存在，9 条历史导入任务默认回填为 0，8 个项目、16 个成功 Diff 和 46 个 Chunk 保持可读。

## 2026-08-06：增量导入保留 revision 快照并修复重复导入删向量

- 审计发现同 revision 重复导入会删除并重建 Chunk；`embedding_vector` 对 Chunk 使用级联外键，因此重复导入可能意外删除已建立的向量。
- 同 revision 现在只创建成功的 `INCREMENTAL` 任务并复用现有 Document、Chunk、Reference 和向量绑定，不再扫描、解析或重写结构数据。
- 新 revision 仍安全克隆并扫描白名单文件，但按路径、文件类型和内容哈希与上一 revision 比较；只有新增、修改和移动文件进入解析器，未变文件从数据库恢复 Chunk 元数据和原始引用。
- 复用引用不会复制旧 `target_chunk_id`，而是结合当前 revision 的全部 Chunk 重新解析目标，避免跨版本引用。删除文件不写入新快照，当前检索按 revision 隔离后自然不可见。
- 保留旧 revision 的 Document、Chunk、Reference 和向量用于历史审查复现，不把“失效”误解为立即物理删除；后续单独设计保留期和垃圾回收。
- V17 为 `index_task` 增加 `reused_files`，任务分别记录实际解析与复用文件数。当前优化跳过解析但仍需克隆和计算文件哈希，尚未宣称实现远端 Git 层面的最小拉取。
- 自动化验证通过后端 122 项、前端 37 项和 Vue 生产构建；H2 从空库执行 V1–V17。隔离 MySQL 26.7 从 V16 原地迁移到 V17，应用健康为 `UP`；9 条历史导入任务的 `reused_files` 均安全回填为 0，原有 8 条成功任务、8 个项目、16 个成功 Diff 和 46 个 Chunk 保持可读。

## 2026-08-06：阶段 9 先实现精确向量复用，不提前引入 MQ

- 现有索引只能在同一 revision 重跑时按 `vector_id` 跳过；新 revision 会因 Chunk ID 改变而重新调用全部 Embedding，即使绝大多数源码未变化。
- V16 增加完整 Embedding 输入 `input_hash` 和任务 `reused_chunks`。复用键覆盖路径、Chunk 类型、符号和内容，不能只用源码内容哈希，因为这些字段都实际发送给 Embedding Provider。
- 只从最近一次同项目、Provider、模型和维度的成功 revision 批量读取候选，避免按每个 Chunk 查询或扫描全部历史。
- 跨 revision 只复制向量值，为新 Chunk 写入独立记录；旧 revision 不删除，以保持历史审查可复现。当前 revision 的检索隔离规则不变。
- Provider 网络调用仍在事务外，复用和批量保存各使用短事务。任务分别记录实际生成、跨版本复用和当前版本跳过数量。
- 自动化验证通过后端 121 项、前端 37 项和 Vue 生产构建。H2 从空库执行 V1–V16；包含历史评测数据的隔离 MySQL 26.7 从 V15 原地迁移到 V16，应用健康为 `UP`，新列和六列复用索引存在，8 个项目、16 个成功 Diff 与 46 个 Chunk 保持可读。历史库没有向量，无需回填伪输入哈希。
- 暂不引入 RabbitMQ。下一步先做文件/Chunk 级增量导入并测量同步任务耗时、失败和恢复需求，再决定队列、重试与死信策略。

## 2026-08-06：真实 A/B 前增加受控执行与绑定门禁

- 只读审计确认两个 AI 创建入口原来会自动选择最近成功 Diff，且查询接口只返回最近任务；这适合人工单次操作，但 8 项付费批量实验会受到新 Diff 竞态、POST 响应丢失、并发同配置任务和中断恢复不确定性的影响。
- 两个创建入口现要求客户端显式提交预期 Diff ID、40 位 revision 和 UUID v4 attemptKey；模型解析前预检，创建任务短事务内二次校验，Diff 漂移返回 409 且 AI 任务、调用日志零落库。
- V15 为 `ai_review_task` 增加唯一 `attempt_key`，并复用为模型调用 trace ID。响应丢失后通过项目和 attempt 精确回读，不依赖 latest，也不盲目重发付费 POST；重复 attempt 返回 409。
- 受控执行器先完成 8 项零模型全批预检，再逐项目执行 FIXED→评测→AGENT→评测；任何失败停止后续付费调用。同步多轮 Agent 使用可配置的长请求/恢复窗口，但没有服务端批次状态机时不声称支持跨进程自动续跑。
- 总体质量按累计 TP/FP/FN 计算微平均 Precision/Recall/F1，不直接平均各场景 F1；报告同时固定 manifest/revisions 哈希，并记录模型、两条路径各自的 Prompt/检索版本、Token、延迟和 Tool 成功率。
- Mock/Fake 覆盖正常顺序、全批漂移、AI/评测响应丢失、并发任务归属、失败即停、微平均和成功/失败报告脱敏。失败报告只保存受控错误类别，不复制源码、Prompt、凭证或任意服务端错误文本。
- 隔离 MySQL 26.7 使用已有 V14 数据目录原地迁移到 V15，应用健康为 `UP`；`attempt_key` 列和唯一索引均存在，8 个项目与 16 个成功 Diff 保持可读。历史库没有 AI 审查任务，因此无需为旧行生成伪 attempt。
- 全量回归已通过后端 120 项、前端 37 项、Benchmark Node 48 项和 Vue 生产构建；下一步提交 PR。之后才在用户确认密钥与额度后运行 1 个真实 canary，canary 通过后才执行 8 组、16 个真实审查任务。本轮没有调用模型。

## 2026-08-06：账号交接改为一次性历史恢复

- 不再维护需要每轮复制的 Codex/ChatGPT 长提示词。新账号第一次进入仓库时只执行一次恢复清单，核对 Git、PR、测试、Flyway、数据库和当前暂停点，随后正常开发。
- ChatGPT 导出的 `conversations.json` 只在仓库缺少设计原因时定向读取一次，不重建侧边栏或记忆，也不得提交到 Git；项目事实仍以代码、测试、数据库和交接文档为准。
- 同一环境可通过任务列表或 `/resume` 继续可见任务，但不依赖两个账号自动合并 Codex 任务、ChatGPT 历史或记忆。
- `~/.codex/auth.json` 是登录凭证而不是历史记录，禁止进入仓库或交接材料；长期规则继续放在 `AGENTS.md` 和版本化文档。

## 2026-08-06：完成 manifest 人工标准答案幂等同步与 MySQL 回读验收

- 扩展 `verify-live-imports.mjs`，新增必须组合使用的 `--reuse-imports --reuse-diffs --record-gold-cases`；标准答案模式只能全量运行，不能与单场景排障混用。
- 先验证全部 8 个项目、Import、latest Diff、revision、覆盖和 TARGET 证据，再跨 Diff 预检同一项目与数据集的已有用例；任一旧 Diff 绑定、重复/额外 caseKey 或字段漂移都会在零次 POST 时失败。
- 预检完整镜像后端 DTO 的字符集、长度、类别、路径和行范围约束，只创建缺失项；POST 响应丢失后通过唯一键范围回读判断是否已提交，最后全量核对字符串 ID、项目、Diff、dataset、revision、类型、类别、路径、行号和依据。
- V13 唯一键为 `(project_id, dataset_version, case_key)`，不包含 `review_task_id`，因此幂等重跑必须复用同一个 latest Diff。标准用例 GET 保留按 Diff 查询，并支持省略 `reviewTaskId` 做数据集级跨 Diff 预检。
- 外部批处理不使用跨 8 个 HTTP 请求的长事务；全批预检减少可提前发现的部分写入，应用阶段依靠数据库唯一键、失败状态、精确回读和可恢复重跑。
- Node 验收工具 28 项测试覆盖 DEFECT/CLEAN 映射、字段漂移、旧 Diff 冲突、整批门禁、非法 manifest、首次补录、零写入重跑、响应丢失恢复和失败计数；后端 111 项测试通过，`git diff --check` 通过。
- 使用已迁移到 V14 的隔离 MySQL 26.7 和 8 个历史 Diff 完成真实验收：首次 `8 created / 8 verified`，立即重跑 `0 created / 8 reused / 8 verified`；数据库最终为 7 条 `DEFECT`、1 条 `CLEAN`，分别绑定 8 个项目和 8 个 Diff，必填证据字段无缺失。
- 本任务没有调用 Embedding、FIXED、AGENT 或模型，也没有生成准确率。下一小任务先实现受控 A/B 执行器和预期 Diff 绑定校验，再运行 canary 与完整真实 FIXED/AGENT A/B。

## 2026-08-06：强化人工标准答案的 Diff 与 TARGET 证据约束

- 修复标准答案只校验相对路径格式和正数行号、却未确认位置真实属于指定 Diff 的缺口，避免虚构标注污染 Precision/Recall/F1。
- DEFECT 文件按 `projectId + reviewTaskId + newPath` 精确绑定目标版本；其他 Diff 的同项目文件和删除侧 `oldPath` 不能作为目标答案。
- 标注范围必须命中 `changedLines`，并与 `revisionSide=TARGET`、正数 `chunkId` 的符号范围形成同一行三重交集；BASE 内存符号不能冒充持久化目标证据。
- 不要求文件整体为 `FULL`：`PARTIAL` 文件只要缺陷行本身有 TARGET 证据仍可录入，未映射行继续由覆盖清单暴露。
- V14 为 `code_review_file` 增加 `new_path_hash` 和任务内联合索引，避免 MySQL 大小写不敏感排序规则混淆 Git 路径，也避免批量录入时反复扫描并反序列化整个 Diff。
- TARGET 符号映射改为按已有的 `knowledge_document.path_hash` 查询，并在 Java 中再次精确比较完整路径，防止上游先把 `Foo.java` 与 `foo.java` 映射到错误 Chunk。
- 新 Diff 写入路径原始大小写的 SHA-256；历史 V13 行保持空哈希并由 Java 精确匹配回退，不使用数据库专属哈希函数做不可移植回填。
- 自动化测试补齐反向行范围、其他 Diff 文件、rename 旧路径、重复目标路径、未命中目标变更、BASE-only、无效 TARGET Chunk、严格三重交集、合法 PARTIAL、跨项目路径以及 TARGET 文档哈希查询和完整路径复核；后端 111 项、Node 16 项通过。
- H2 已从空库执行 V1-V14；隔离 MySQL 26.7 已将包含 16 个成功历史 Diff 的 V13 数据目录原地迁移到 V14，应用健康为 `UP`，16 个历史文件均保留为空哈希兼容状态。
- 隔离 MySQL 临时表验证表明，`utf8mb4_unicode_ci` 下按 `file_path` 查询会同时命中大小写变体，按原始路径 SHA-256 查询只命中正确文件；验证后未保留临时数据。
- 本任务没有前端契约或模型调用，也未录入 manifest 或产生准确率。下一步批量录入并回读复核 8 个真实 Diff 的人工标准答案。

## 2026-08-06：完成 H2 与真实 MySQL 评测样本导入和 Diff 验收

- 新增零第三方依赖的 `verify-live-imports.mjs`，通过现有 API 顺序完成 8 个项目的创建或精确复用、源码导入、状态回读、默认 Diff 和证据核对。
- 16 项 Node 回归覆盖正常路径、manifest/revision 漂移、重复或配置漂移项目、`PARTIAL/SKIPPED`、标准答案未命中同一条持久化 TARGET Diff 证据、聚合计数不一致、非 JSON 错误、单场景参数、最近成功导入复核及前置失败不创建 Diff。
- 使用 H2 `test` Profile 和隔离空库 MySQL 26.7 分别连接真实公开 GitHub 仓库完整运行，两次最终结果均为 `8 PASS / 6 FULL / 2 PARTIAL / 0 SKIPPED / 0 FAIL`；所有 base/candidate SHA 均与 `revisions.json` 一致。
- `case-005` 的 TARGET 第 3 行新增 import、`case-008` 的 BASE 第 3 行删除 import 未进入当前 class/method Chunk；缺陷方法行仍有 candidate 映射证据，后续应设计 `FILE_HEADER/IMPORT` Chunk，不能伪造完整覆盖。
- 真实运行曾遇到 GitHub TLS/克隆瞬时失败；先使用单场景重试恢复失败分支，再用 `--reuse-imports` 要求 8 个最近任务全部成功并重新执行完整 Diff，避免每次全量重克隆让其他成功分支再次暴露于网络抖动。最终报告明确记录 `REUSE_LATEST_SUCCEEDED`，不是拼接局部结果。
- JGit 用户错误提示补充网络检查，避免只误导用户排查 Token；复用模式不会接受缺失、失败或 revision 漂移的任务。
- 隔离 MySQL 从空库执行 Flyway V1-V13，生成 25 张表；最终持久化 8 个 `READY` 项目、8 个文档、46 个 Chunk、8 个成功导入任务和 16 个成功 Diff 任务。首次 GitHub 瞬时失败保留 1 个可观察失败任务，重试后恢复成功。
- 系统安装的 MySQL 3306 仍未监听，这是独立的本机运维问题；不要删除 `ibdata1`、Socket 或重建系统数据目录。它不再阻塞隔离 MySQL 对项目真实方言、迁移和持久化链路的验收结论。
- 本轮未调用 Embedding、FIXED 或 AGENT，未录入标准答案，也未产生准确率。下一步先强化标准答案创建时的 Diff 文件归属、目标变更行和持久化 TARGET Chunk 证据校验，再批量录入 manifest；之后才运行真实 FIXED/AGENT A/B。

## 2026-08-05：建立跨账号可验证交接协议

- 将 GitHub `main`、已合并 PR、自动化测试和数据库迁移设为项目事实，交接文档次之，聊天历史只作背景参考。
- 在 `CODEX_HANDOFF.md` 增加 30 秒恢复卡、可信度顺序、新账号首次检查和旧账号收尾要求。
- 新增 `ACCOUNT_HANDOFF_PROMPTS.md`，提供 Codex、ChatGPT 和紧急精简三种可复制提示词，以及新旧账号检查清单。
- 账号切换只迁移代码和可验证上下文，不在文档中保存密码、Token、模型 Key、订阅、记忆或私有源码。
- 新账号恢复后仍以“真实样本导入与 Diff 验收”为唯一下一任务，暂不调用模型或扩展架构。

## 2026-08-05：生成并发布可复现的评测 Git 历史

- 为 8 个样本分配不泄漏缺陷类别的 `case-001` 至 `case-008` 分支；每个分支固定为 main 根提交、base 提交、candidate 提交。
- 使用 JGit 和固定作者、时间、消息及父提交生成确定性历史；重复生成的 16 个 revision 完全一致。
- `revisions.json` 固定场景、分支、base revision 与 candidate revision，测试验证候选 HEAD、父提交和单 Java 文件 MODIFY Diff。
- 输出目录必须为空，生成器拒绝覆盖已有文件，避免误删人工数据。
- 创建公开纯虚构仓库 `MMDXTMM/devmate-review-benchmark` 并推送 main 和 8 个 case 分支；远端分支 HEAD 已与 revision 清单逐一核对。
- 仓库源码不含标准答案、凭证或 Bug 标记；人工依据只保留在 DevMate 主仓库的 manifest 中。
- 后端全量 107 项测试通过；本任务没有前端和数据库结构变化。
- 下一步通过公开仓库创建 8 个评测项目并执行导入、Diff、标准答案录入及真实 FIXED/AGENT A/B。

## 2026-08-05：建立第一版真实缺陷样本契约

- 新增 `benchmarks/review-fixtures/known-defects-v1`，使用独立 `base/candidate` 快照描述每个待生成的 Git Diff，避免同一 Diff 混用 `DEFECT/CLEAN`；`candidate` 命名避免被 Maven `target/` 忽略规则排除。
- 固定 8 个场景：并发丢失更新、事务同类自调用、缓存击穿式并发回源、消息先确认后落库、SQL 循环查询、路径穿越、持锁远程调用和批量查询 CLEAN 对照。
- `manifest.json` 记录稳定场景键、人工依据、类别、项目内相对路径和 target 行范围，不保存模型输出、真实业务源码或敏感信息。
- 新增契约测试，校验场景/用例键、类别、路径逃逸、行号、base/candidate 文件集合及标准答案必须覆盖真实变更行；失败清单也有回归测试。
- 后端全量 105 项测试通过；本任务没有前端和数据库结构变化。
- 本任务不创建模型结果、不调用 Qwen，也不宣称准确率；下一步把快照生成独立 Git 提交并通过现有导入、Diff 和 V13 接口执行第一轮真实 A/B。

## 2026-08-05：完成固定评测集与 FIXED/AGENT 对比工作台

- Vue 项目列表新增“评测”入口，自动绑定最近成功 Diff，避免用户手工复制雪花 ID 或 revision。
- 前端支持按版本读取和录入 `DEFECT/CLEAN` 标准答案；缺陷用例要求类别、相对路径和行范围，无缺陷对照不会发送缺陷字段。
- 评测操作只读取最近一次已持久化的 AI 审查任务，不调用模型；执行模式继续由后端任务决定，客户端不能伪造 `FIXED/AGENT`。
- 同一数据集的最近 FIXED/AGENT 快照并排展示 F1、Precision、Recall、TP/FP/FN、Token、耗时、Tool 成功率和人工复核数量。
- 只有一种模式时明确展示缺失步骤；`partialMetrics` 标记为部分指标，不能当作最终准确率。
- 当前为了保持接口范围可控，用户需要在 AI 审查页“运行一种模式 → 回到评测页评测”，再对另一模式重复；后续若真实使用证明有必要，再增加按 Diff 查询 AI 历史任务的接口。
- 后端 103 项测试、前端 29 项测试和 Vue 生产构建通过；本阶段没有数据库迁移，也没有消耗真实模型额度。
- 下一步建立独立的真实缺陷提交集，录入人工标准答案并执行第一轮真实模型 A/B；结果完成前仍不宣称准确率或 Agent 优于固定流水线。

## 2026-08-05：建立固定缺陷标准答案与评测运行模型

- V13 新增 `review_evaluation_case/review_evaluation_run`，并在 AI 审查任务创建时固定 `FIXED/AGENT` 执行模式。
- 标准答案绑定成功 Diff、目标 revision、数据集版本和内容哈希；支持已知缺陷与无缺陷对照，但同一数据集不能混合两种语义。
- 自动匹配要求类别和相对文件一致、行区间重叠且候选关系唯一；歧义项进入人工复核，不用脆弱的标题或描述字符串相等冒充准确率。
- 评测保存 TP/FP/FN、Precision/Recall/F1、Token、延迟和 Tool 成功率；相同 AI 任务与数据集哈希幂等返回，不会再次调用模型。
- 评测只接受人工标准答案，不把 `REJECTED` 反馈自动当作误报。
- 后端 103 项测试、前端 24 项测试和生产构建通过；真实 MySQL 已从 V12 迁移到 V13，健康检查为 `UP`，原项目保持可读。
- 下一步建立真实缺陷提交集和 Vue A/B 对比页，真实模型验证前不宣称准确率。

## 2026-08-05：完成代码审查反馈第一版闭环

- V12 新增 `code_review_feedback`，按 Finding 唯一保存最新反馈，并保留首次创建和最近更新时间。
- 反馈语义固定为 `ACCEPTED/REJECTED/FALSE_POSITIVE/DEFERRED`；`REJECTED` 只表示暂不采纳，不能直接作为误报标签。
- 新增幂等 `PUT` 接口，服务端同时校验项目存在、Finding 存在以及二者归属，跨项目请求不暴露资源信息。
- 当前尚未完成认证，因此不接受客户端提供 `userId`；阶段 10 再绑定服务端认证身份，避免伪造操作人。
- 最近 AI 审查响应批量回填反馈，避免逐条查询；Vue 支持备注和四种反馈，保存时不重新运行模型或消耗 Token。
- 后端 96 项测试、前端 24 项测试与生产构建通过；本机 MySQL 已从 V11 迁移到 V12，健康检查为 `UP`，原项目数据保持可读。
- 下一小阶段建立包含已知缺陷和标准答案的固定数据集，比较固定流水线与 Agent 的命中、漏报、误报、Token、延迟和工具成功率。

## 2026-08-05：完成受控 Tool Calling Agent 工程闭环

- 保留阶段 6 固定流水线，新增独立 Agent 审查入口，为阶段 8 做同数据集 A/B 评测准备。
- 按 Qwen Function Calling 协议实现多轮消息回填；模型只负责规划，Java 负责工具白名单、执行和最终业务校验。
- 提供 `getDiffCoverage/getStaticAnalysis/searchCode/analyzeProjectStructure` 四个只读工具；项目、任务和 revision 由服务端固定。
- 增加合法 JSON、字段、长度、数量、超时、输出、总步数和重复调用限制；必须获得真实代码 Chunk 才能完成审查。
- V11 扩展工具审计，记录调用 ID、顺序、参数哈希、脱敏摘要和错误类别，不保存完整查询、源码或 Prompt。
- Agent 多次检索证据去重并受 Chunk/Token 预算限制；规划与最终审查 Token 合并记录。
- Vue 增加“固定流水线/Agent 智能取证”显式选择和工具调用链展示，打开弹窗不会自动消耗额度。
- 后端 93 项测试、前端 21 项测试与生产构建通过；真实模型效果仍待 DashScope Key 和阶段 8 固定缺陷集验证。
- 本机 MySQL 26.7 已从 V10 迁移到 V11，应用健康检查为 `UP`，原有 `devmate-ai` 项目仍可正常读取。
- 下一阶段进入审查反馈和效果评测，不提前增加自动改码、任意 Shell/SQL 或微服务。

## 2026-08-04：完成证据约束的 AI 代码审查工程闭环

- V10 新增独立 `ai_review_task`，并扩展 AI 调用审计和统一 Finding，固定 Diff、静态分析、revision、模型、Prompt 与检索版本。
- 模型调用采用 DashScope OpenAI 兼容 Chat Completions 与 JSON Mode；设置连接/读取超时，不配置 Key 时保存可观察失败。
- Prompt 将仓库内容视为不可信证据；模型只能引用本次 RAG 提供的 Chunk，真实文件和行号由 Java 映射。
- 校验并限制类别、严重程度、事实/推断/待验证、置信度、字段长度、重复项和 Finding 数；伪造证据进入拒绝计数而不入库。
- 状态机采用数据库短事务包围状态变化，RAG 与模型在事务外；`running_key` 唯一键防并发，超时任务可失败回收。
- 测试发现 MyBatis-Plus `updateById` 默认忽略 null，改用显式 `SET running_key = NULL`，避免成功任务永久阻塞重试。
- Vue 增加显式 AI 审查入口和结构化报告；打开窗口只查询历史，不自动消耗模型额度。
- 后端 87 项测试、前端 18 项测试与生产构建通过；真实模型效果和准确率留到固定缺陷集评测，不用 Mock 结果冒充。
- 下一阶段实现受控 Tool 契约、最大调用步数和工具调用审计，不提前让模型执行任意 Shell/SQL。

## 2026-08-04：完成向量索引与混合 RAG 工程闭环

- 新增本地确定性哈希与 DashScope 两种 `EmbeddingProvider`；本地算法只用于离线测试，不冒充真实语义模型。
- V9 新增向量和索引任务表，按项目、revision、provider、模型与维度隔离，使用确定性 ID 保证批次重试幂等。
- 外部 Embedding 请求放在数据库事务之外，批量结果通过短事务持久化并记录成功、跳过、失败状态。
- 实现 `LEXICAL/VECTOR/HYBRID` 三种模式，使用 RRF 融合关键词、关系图和向量排名，沿用统一 Top-K 与 Token 预算。
- 向量缺失或服务异常时返回 `LEXICAL_FALLBACK` 与原因；固定评测拒绝把降级结果记为 Hybrid 指标。
- 前端增加向量化、检索策略和降级状态展示。
- 当前 MySQL 向量线性扫描只服务于小规模开发闭环；真实代码库规模与延迟达到阈值后再替换 ANN 向量库。
- 后端 74 项测试、前端 15 项测试和生产构建通过；真实 MySQL 已从 V8 迁移到 V9，健康检查为 `UP`，原项目数据保持可读。
- 浏览器联调确认向量化入口、三种检索策略和受控失败提示；旧 revision 没有 Chunk 时不会创建脏索引任务。

## 2026-08-04：建立面向变更的检索基线与固定评测

- 新增 `lexical-graph-v1`：组合符号、路径、内容关键词与逆文档频率，并使用 Diff Chunk 和 `code_reference` 扩展项目上下文。
- 所有检索强制使用项目和 revision 隔离，外部种子 Chunk 必须验证归属，防止跨项目源码泄露。
- 实现内容哈希去重、Top-K、Token 预算和 `DUPLICATE_CONTENT/TOP_K/TOKEN_BUDGET` 裁剪记录。
- 候选扫描达到上限时显式标记结果可能不完整；Token 目前为字符近似值，不冒充模型 tokenizer。
- 新增通用检索、最新 Diff 上下文接口和 Vue 证据浏览界面。
- V8 新增固定评测用例与运行记录，绑定项目 revision 和检索配置版本，计算 Recall@K、Precision@K、HitRate@K 和 MRR。
- 本阶段是接入 Embedding 前的可解释对照组；后续必须使用同一评测集证明向量召回的真实增益。

## 2026-08-04：建立数据库迁移结构上下文

- 扫描范围增加常见迁移目录 SQL，任意位置的 SQL 不进入知识库。
- 通过有状态 SQL 切分与限时 JSqlParser 提取表、列、索引、约束和部分 ALTER 结构，不执行目标 SQL。
- DML、默认值和原始 SQL 不持久化，避免测试数据或秘密进入后续向量库与 Prompt。
- `@TableName` 与 JPA `@Table(name=...)` 通过 `code_reference` 关联到 `DATABASE_TABLE`，接口和 Vue 展示迁移文件与安全摘要。
- 明确迁移事实不等于线上最终 Schema，DROP/RENAME、动态 SQL 和 Schema 漂移留待后续版本化归并与只读快照对比。
- 本阶段复用现有知识表与 V7 引用关系，不新增业务表或 Flyway 迁移。

## 2026-08-04：补齐配置文件上下文与 Java 引用关联

- 源码扫描范围扩展到 Java、YAML 和 Properties，同时保留目录、符号链接、文件数量与容量限制。
- 使用安全 YAML 构造器并拒绝重复键；Properties 支持续行并拒绝同文件重复键。
- 配置文件复用 `knowledge_document/knowledge_chunk`，以 `CONFIG_PROPERTY` 保存展平键、类型、行号和脱敏摘要，不持久化原始配置值。
- 将 `@Value` 精确键与 `@ConfigurationProperties` 前缀关联到候选配置定义；接口和 Vue 展示目标文件路径。
- 明确多 Profile 下一个引用可以有多个候选定义，静态上下文不冒充 Spring 运行时最终值。
- 后端 54 项测试、前端 11 项测试和生产构建通过；本阶段复用 V7 数据模型，不新增迁移。
- 使用本机 MySQL 26.7 验证 V7 已是最新版本，应用健康检查为 `UP`，已有项目只读查询正常。

## 2026-08-04：实现首批项目级工程风险规则

- 静态分析升级为 `PMD+DEVMATE` 流水线，统一写入 `review_finding`。
- 新增 `TransactionalSelfInvocation`，根据唯一解析的同类调用和目标注解识别事务代理绕过风险。
- 新增 `DataAccessInsideLoop` 与 `BlockingDataAccessUnderLock`，分别提示 N+1/请求放大和持锁慢 IO 风险。
- 所有规则只命中目标版本 Diff 行；命名约定类规则明确输出待验证语义，避免把启发式线索包装成确认缺陷。
- 测试暴露虚拟线程看不到调用线程未提交事务的问题，最终将数据库上下文读取留在请求线程，只对 PMD 执行设置超时。
- 后端 48 个测试、前端 11 个测试和生产构建通过。

## 2026-08-04：建立第一版代码上下文关系图

- 从 JDK AST 提取方法调用、`@Value` 配置键、`@ConfigurationProperties` 前缀和数据访问入口。
- 新增 V7 `code_reference`，保存来源符号、可空目标符号、revision 和真实行号。
- 同类调用只有在方法名、参数数量和候选目标唯一时才解析，避免对重载和跨类调用制造伪调用链。
- 源码结构接口和 Vue 浏览器增加关系证据展示；旧项目重新导入后生成关系数据。
- 后端 46 个测试、前端 11 个测试和生产构建通过，V7 已在真实 MySQL 成功迁移并完成应用启动验证。
- 下一步使用关系图实现 `@Transactional` 自调用等首批项目级确定性规则。

## 2026-08-04：完成确定性静态分析 MVP

- 接入 PMD 7.26.0 Java API，使用五条小而明确的规则，不运行目标仓库构建脚本。
- 新增 V6 迁移，保存静态分析任务和统一 `review_finding`。
- 分析范围来源于最近成功 Diff，只保留直接命中目标版本变更行的违规。
- 增加超时、文件数、受控工作区路径校验、失败状态和 Finding 指纹去重。
- 新增执行/查询接口和 Vue 静态问题界面。
- 后端 45 个测试、前端 10 个测试与生产构建通过。
- V6 首次在真实 MySQL 执行时发现 `utf8mb4` 长文件路径会超过联合索引长度限制；改为保存完整 `file_path` 并使用 SHA-256 `path_hash` 建索引，清理失败迁移记录后重新验证通过。这个问题说明内存数据库测试不能替代真实数据库迁移验证。
- 阶段 4A 完成；下一步补充项目规则、调用关系和配置/数据库上下文。

## 2026-08-04：完成 Git Diff 基准版本删除行映射

- 同时保存 JGit Edit 的基准版本和目标版本行区间。
- 目标版本继续复用知识库 AST，基准版本通过 Git Blob 按需读取并在内存解析，不复制历史源码数据。
- 映射证据增加 `BASE/TARGET` 版本侧，删除 Java 文件不再被默认跳过。
- 增加相似度 Rename 测试，覆盖文件改名后同时修改方法内容的场景。
- 新增 V5 迁移保存基准版本行区间；后端 40 个测试、前端 9 个测试及生产构建通过。
- 阶段 3 完成，下一步接入确定性 Java 静态分析并统一 Finding 模型。

## 2026-08-04：完成 Git Diff 覆盖报告 MVP

- Git克隆深度从 1 调整为 50，为近期提交比较保留历史对象。
- 使用JGit识别文件变化与目标行区间，不执行仓库命令或脚本。
- 新增V4迁移，持久化Diff任务与逐文件覆盖清单。
- 将目标版本变更行映射到AST类和方法，输出 `FULL/PARTIAL/SKIPPED`。
- 删除和非Java文件也记录原因，不允许静默漏审。
- 新增创建、最近报告接口及Vue Diff报告界面。
- 后端39个测试、前端9个测试和生产构建通过，V4已在本机MySQL验证。
- 下一步补齐删除行的基准版本映射和复杂Rename用例，再进入确定性静态分析。

## 2026-08-04：完成 Java 源码结构化解析

- 使用 JDK Compiler Tree API 解析 Java，不执行目标仓库代码，也不增加第三方解析器依赖。
- 解析 package、类、嵌套类、构造器、方法、注解和准确起止行。
- 新增 Flyway V3，为文件增加包名，为 Chunk 增加可扩展 JSON 元数据。
- 导入完成事务同步写入 `knowledge_document` 和 `knowledge_chunk`；重复 revision 不重复积累 Chunk。
- 新增文件和符号查询接口，Vue 增加源码结构浏览器。
- 将本地和默认日志收紧到 INFO，避免 SQL 参数日志泄露私有源码。
- 后端 33 个测试、前端 8 个测试和生产构建通过，V3 已在本机 MySQL 验证。
- 下一阶段进入 Git Diff、覆盖清单和变更行到 AST 符号映射。

## 2026-08-04：建立统一开发与运维规范

- 新增根目录 `AGENTS.md`，约束后续人工和 AI 开发的模块、事务、安全、测试与文档行为。
- 新增 `CONTRIBUTING.md`，统一分支、提交前检查和 PR 描述要求。
- 新增 `.editorconfig`，统一 Java、Vue、TypeScript、YAML 和 Markdown 基础格式。
- 新增工程规范，覆盖模块边界、API、事务、数据库、日志、外部调用、AI、前端、测试和 Definition of Done。
- 新增运维手册，记录启动、健康检查、正常停止、常见故障、备份、密钥轮换和回滚流程。
- 决定继续使用 Spring Boot 模块化单体和轻量规范，不引入与代码审查主线无关的后台脚手架或自研通用框架。

## 2026-08-03：支持私有 GitHub 仓库只读导入

- 确认首次真实导入失败是因为目标 GitHub 仓库为 Private，匿名 HTTPS 无权克隆。
- 增加 `DEVMATE_GIT_USERNAME` 与 `DEVMATE_GIT_TOKEN` 环境变量配置。
- JGit 仅在 Token 存在时注入 CredentialsProvider，公开仓库继续支持匿名克隆。
- 凭证不进入项目表、任务表、日志、异常信息或 Git 配置文件。
- 开发阶段推荐 Fine-grained Token，仅选择目标仓库并授予 Contents Read-only。
- 多用户生产版本仍计划迁移到 GitHub App Installation Token，避免共享个人 Token。
- 已使用本机 GitHub CLI 钥匙串凭证完成真实私有仓库验收：克隆 `MMDXTMM/devmate-ai` 的 `main` 分支成功，revision 为 `7caad90855ffa5b94f85e2c55c9924904a30a204`，发现并持久化 31 个 Java 文件，项目最终进入 `READY`，任务进入 `SUCCEEDED`。

## 2026-08-03：根据同类开源项目调整为 Diff-first 路线

- 对比 Alibaba Open Code Review、PR-Agent、Vercel OpenReview 和 Calimero AI Code Reviewer。
- 保留“确定性静态分析 + RAG + LLM”的混合架构，不改成纯 Agent。
- 将 Git Diff、覆盖清单和精确位置映射提前到通用 RAG 之前。
- 每次审查必须展示完整、部分和跳过文件，禁止静默漏审后返回成功。
- Diff 只限定范围，模型判断前必须补充完整方法/文件和必要依赖上下文。
- 真实行号由 AST 和 Diff 映射，模型只引用 symbol/chunk 和证据。
- 增加项目级规则匹配、Token 裁剪记录和不可信仓库 Prompt 边界。
- MVP 暂不采用多 Agent、自动修复和自动提交；先用固定评测集验证单 Agent。
- 详细依据记录在 `OPEN_SOURCE_COMPARISON.md`。

## 2026-08-03：完成第一版 Git 源码导入闭环

- 使用 JGit 按项目默认分支执行浅克隆，不通过 Shell 拼接执行 Git 命令。
- Git 地址仅允许 HTTPS，并拒绝本机、私有 IP 字面量和 URL 内嵌凭证。
- 扫描器只读取普通 Java 文件，忽略构建产物和开发工具目录，并限制文件数量与容量。
- 每次导入创建 `index_task`，成功或失败都会更新任务与项目状态。
- 将文件路径、路径哈希、内容哈希和 revision 写入 `knowledge_document`，相同 revision 重复导入不重复建档。
- 网络和文件操作不占用长数据库事务，采用准备、完成、失败三个短事务形成最终一致闭环。
- Vue 项目列表增加“导入源码/重新导入”操作和结果提示。
- 当前只支持无需认证的 HTTPS 仓库；私有仓库凭证与异步大仓库导入留待后续阶段。
- 下一次开发进入 Java 结构化解析与 `knowledge_chunk` 生成。

## 2026-08-03：增加面试导向学习路线

- 将学习内容与 10 个开发里程碑一一对应。
- 每个阶段明确必须掌握、AI 可协助、开发者必须主导的内容。
- 每个阶段增加面试检查题和可验证验收标准。
- 当前三次开发确定为项目详情、分页修改、逻辑删除与 OpenAPI。
- 不使用 AI 生成代码比例判断项目归属，以解释、修改、调试和验证能力作为标准。

## 2026-08-03：启动项目管理业务闭环

- 新增 `POST /api/projects` 项目创建接口。
- 使用请求 DTO 隔离接口参数与数据库实体。
- 使用 Bean Validation 校验字段长度和源码类型。
- 在 Service 层校验 Git 项目必须提供仓库地址。
- 项目创建后默认进入 `CREATED` 状态。
- 登录认证尚未实现，因此第一版暂不强制写入 `owner_id`。
- 增加接口成功、参数失败和数据库写入测试。
- 新增 `GET /api/projects/{id}` 项目详情接口和只读事务。
- 项目不存在时统一返回 HTTP 404，非法项目 ID 返回 HTTP 400。
- 使用 `local` Profile 完成真实 MySQL 创建与详情查询验收。
- 记录 IDEA 未启用 `local` Profile 导致应用查询 H2、DataGrip 查询 MySQL 的环境隔离问题。
- 新增项目分页、名称/状态筛选和 PUT 元数据更新接口。
- 增加 MyBatis-Plus 分页插件，并限制每页最多 100 条。
- 更新 SQL 采用允许字段白名单，避免并发覆盖系统管理的状态和索引版本。
- 新增项目逻辑删除接口；删除后的数据由 MyBatis-Plus 自动从详情和列表查询中排除，重复删除返回 404。
- 项目雪花 ID 在 JSON 中序列化为字符串，避免 JavaScript 大整数精度丢失导致查询不到项目。
- 项目管理基础 CRUD 已完成，17 个自动化测试全部通过；真实 MySQL 全 CRUD 验收仍待执行。
- 新增独立 Vue 3 + TypeScript 前端，通过 Vite 将 `/api` 代理到 Spring Boot。
- 前端实现项目列表、分页、筛选、新建、编辑、逻辑删除和统一错误提示。
- 使用临时 H2 完成 Vue 代理下的真实 CRUD 联调，删除后查询正确返回 404。
- 前端 6 个自动化测试、生产构建和 npm 安全审计通过，浏览器控制台无错误。
- 将 `local` 设为默认运行 Profile，后端直接启动即可连接本机 MySQL。
- 所有 Spring Boot 测试显式固定到 `test` Profile，继续使用 H2，防止测试污染 MySQL。
- 验证默认启动成功读取 MySQL 中已有的 `devmate-ai` 项目。

## 2026-08-03：代码审查升级为项目主线

### 背景

AI 生成代码不仅可能包含语法或空指针等硬性 Bug，还可能存在并发、事务、缓存、消息一致性、SQL 性能、安全和架构方面的隐性风险。普通代码问答难以形成足够强的业务闭环和简历辨识度。

### 决定

- 将项目定位调整为“智能代码审查 Agent 平台”。
- 采用静态分析 + Git Diff + RAG + LLM 的混合审查方案。
- 代码问答、Bug 诊断和需求分析保留为辅助能力。
- 第一版只提供结构化审查建议，不自动修改或提交代码。
- 在 RAG 之后优先实现 Git Diff、静态分析和 AI 代码审查。
- 增加审查反馈与固定评测集，避免只展示生成文本。

### 影响

- 更新项目总设计和开发路线。
- 新增代码审查设计文档。
- 后续数据库需要增加审查任务、问题项和反馈表。
- 简历重点调整为 Java 隐性风险审查、上下文检索和可验证结果。

## 2026-08-04：接入数据库结构上下文

- 只扫描受控迁移目录中的 SQL，不执行目标项目脚本，也不连接目标项目数据库。
- 使用有状态 SQL 切分与 JSqlParser 提取表、列、索引、约束和结构变更，忽略 DML、默认值和原始 SQL，避免把种子数据或秘密写入知识库。
- 从 MyBatis-Plus `@TableName` 和 JPA `@Table` 提取 Java 实体到数据库表的跨文件关系。
- API 与前端能够展示脱敏后的数据库结构摘要和目标迁移文件位置。
- 明确迁移事实不等于线上最终 Schema，索引存在也不代表执行计划一定使用索引。
- 后端 60 个测试、前端 11 个测试和生产构建通过。
- 本地 MySQL 26.7 启动验证通过：Flyway 校验 7 个迁移，健康检查为 `UP`，已有项目数据读取成功。

## 2026-08-04：完成面向变更的检索基线

- 新增 `lexical-graph-v1`：以符号、文件路径和源码词项召回候选，并用 Diff 种子和 `code_reference` 关系图扩展上下文。
- 所有检索强制隔离 `project_id + revision`，外部种子 Chunk 重新校验归属，防止跨项目源码泄露。
- 对结果执行内容哈希去重、稳定重排、Top-K 和 Token 预算裁剪，并返回采用原因、裁剪原因和扫描上限状态。
- 新增最新 Diff 上下文接口，把变更符号作为种子，但不把 Diff 片段误当完整业务上下文。
- 新增固定检索评测集和运行记录，支持 Recall@K、Precision@K、HitRate@K、MRR；无法解析的预期目标不进入指标分母。
- Vue 新增检索弹窗，可查看排名、得分、估算 Token、证据来源和被裁剪候选。
- 后端 69 项测试、前端 14 项测试以及前后端生产构建全部通过；真实 MySQL 已迁移至 V8，浏览器控制台无错误。
- 真实库的旧 revision 只有文件记录、没有 Chunk，系统明确提示未建立知识索引；GitHub 网络恢复后需重新导入，随后建立第一批固定评测用例。
- 下一子阶段在同一接口和评测集上接入 Embedding/向量召回，量化相对关键词基线的提升，不直接用主观 Demo 宣称 RAG 有效。

## 2026-08-04：完成证据约束的 AI 代码审查 MVP

- 固定“最新成功 Diff + 对应静态分析 + Hybrid RAG”作为模型输入，第一版不允许模型自主执行 Shell、SQL、数据库访问或修改代码。
- 通过 `AiReviewModel` 隔离模型提供方，当前接入 DashScope OpenAI 兼容接口与 JSON Object 输出；API Key 只从运行环境读取。
- 服务端校验模型返回的 Chunk ID、枚举、字段长度、置信度和重复项；文件路径与行号只使用服务端证据元数据。
- 将模型调用放在数据库事务外，使用短事务记录 RUNNING/SUCCEEDED/FAILED 状态、Prompt 版本、请求哈希、Token 和耗时；`running_key` 负责并发幂等与超时恢复。
- Vue 新增 AI 审查报告弹窗，打开时只读取历史记录，必须显式点击才会产生模型费用。
- 后端 87 项测试、前端 18 项测试和生产构建通过；H2 从空库执行 V1–V10。
- 本地 MySQL 26.7 已从 V9 成功迁移到 V10，健康检查为 `UP`，原有项目数据读取正常；Flyway 对该高版本 MySQL 给出兼容性提醒。
- 尚未使用真实 DashScope Key 评测结论质量，因此当前不宣称准确率；下一阶段再实现受控 Tool Calling Agent，后续用人工标注集统计命中、漏报和误报。

### 当前暂停点

- 阶段 6 的实现、自动化测试、前端构建和真实 MySQL V10 迁移均已完成。
- 本阶段代码保存在 `codex/ai-review-mvp`，通过草稿 PR 交付；上一阶段向量 RAG PR #7 已合并。
- 暂停期间不继续引入 Agent、MQ、Redis、鉴权或自动改码，避免扩大未学习范围。
- 下次恢复先同步远端 `main`，再进入阶段 7：受控 Tool Calling Agent；开发前需先复习 `docs/AI_REVIEW_MVP.md` 的 8 个面试问题。

## 2026-08-03：明确 AI 辅助开发边界

### 决定

- 不以手敲代码比例判断项目归属。
- 开发者必须主导需求、架构、关键逻辑、验证和最终合并。
- AI 可以承担模板代码、候选方案、测试初稿、文档和第二视角审查。
- 简历只描述已实现、已测试且开发者能够独立解释的能力。
- 每次项目主线和重要架构变化需要同步开发文档。

## 2026-07-31：数据库迁移到本地 MySQL

- 使用 Flyway 管理 V1、V2 数据库结构。
- 完成 H2 与真实 MySQL 双环境验证。
- MySQL 保存业务关系和向量引用，向量本体留给后续向量存储。
- 本地数据库密码配置由 Git 忽略。

## 2026-07-30：初始化项目

- 采用 Java 21、Spring Boot 3.5、MyBatis-Plus 和 Flyway。
- 采用模块化单体，先完成业务闭环，再根据真实压力评估 Spring Cloud 拆分。
