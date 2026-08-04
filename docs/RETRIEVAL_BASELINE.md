# 面向变更的检索基线与评测

## 1. 本阶段目标

在接入 Embedding 之前先建立可解释的检索基线。它用于回答：当前仅依赖符号、关键词和关系图能够找回多少正确上下文，后续向量检索究竟改善了哪些案例。

```text
用户问题 / 最新 Diff
  → 校验项目与 revision
  → 变更 AST Chunk 作为种子
  → 关键词、符号名、文件路径召回
  → code_reference 扩展调用方/被调用方/配置/数据库关系
  → 内容哈希去重与稳定重排
  → Top-K + Token 预算
  → 返回采用证据与明确裁剪清单
```

当前 `lexical-graph-v1` 是检索基线，不冒充完整语义 RAG。下一子阶段会在相同接口和评测集上加入 Embedding 与向量召回。

## 2. API

### 项目检索

```http
POST /api/projects/{projectId}/retrieval/search
```

请求支持问题、revision、最多 20 个种子 Chunk、Top-K 和 Token 预算。种子 Chunk 必须属于同一项目和 revision，否则拒绝请求，避免跨项目泄露。

### 最新 Diff 上下文

```http
POST /api/projects/{projectId}/review-diffs/latest/context
```

系统从最近成功 Diff 的 `TARGET` 符号提取种子，再检索直接关系和文本相关上下文。删除文件只存在于基准版本，当前不会被错误地当成目标版本知识。

### 固定评测集

```http
POST /api/projects/{projectId}/retrieval/evaluation-cases
POST /api/projects/{projectId}/retrieval/evaluation-runs
GET  /api/projects/{projectId}/retrieval/evaluation-runs/latest
```

每条评测用例固定问题、预期文件、可选预期符号和 K。运行结果保存检索配置版本、项目 revision、逐用例结果以及宏平均指标，使后续算法比较可复现。

## 3. 排序与预算

- 符号完全匹配、符号词、路径词和内容词分别计分；内容词使用候选集合内的逆文档频率降低常见词权重。
- Diff 种子获得最高的确定性加权，`code_reference` 相邻节点获得关系加权，并在响应中标明原因。
- 相同 `content_hash` 只保留最高分候选；其余记录为 `DUPLICATE_CONTENT`。
- 超过 Top-K 或 Token 预算的候选分别记录为 `TOP_K`、`TOKEN_BUDGET`，禁止静默丢弃。
- Token 数目前是明确标注的字符近似值；接入具体模型后必须换成模型对应 tokenizer，不能把估算值当成计费数据。
- 候选与关系扫描都存在上限，命中上限时分别响应 `candidateLimitReached=true`、`referenceLimitReached=true`，不能声称检索覆盖完整索引或完整关系图。

## 4. 评测指标

- `Recall@K = 前 K 个结果中的相关项数量 / 全部预期相关项数量`，关注是否漏掉关键上下文。
- `Precision@K = 前 K 个结果中的相关项数量 / K`，关注给模型的上下文噪声。
- `HitRate@K`：至少命中一个预期项的用例比例。
- `MRR`：第一个正确结果排名倒数的平均值，越接近 1 表示正确证据越靠前。

未在当前 revision 建立索引的预期目标标记为未解析，不混入指标分母。若所有用例都无法解析，整次运行拒绝生成“0 分”，避免把数据集配置错误误认为检索效果差。

## 5. 安全与正确性边界

- 所有 Chunk、文档和关系查询同时过滤 `project_id + revision`。
- 外部请求提供的种子 ID 需要再次验证归属，不信任客户端。
- 响应只返回有限长度源码片段，日志和评测结果不重复保存完整私有源码。
- 评测预期文件必须是规范化的项目内相对路径，拒绝绝对路径和 `..` 逃逸。
- 当前关键词基线对中英文语义转换、同义表达和跨类动态调用能力有限，这是 Embedding 阶段需要用评测证明的改进点。
- 关系图只扩展已有确定性或候选引用，不把无法唯一解析的调用包装成事实。

## 6. 面试必须掌握

- 为什么先建立关键词/关系图基线，再接向量检索；
- 项目隔离和 revision 隔离分别防止什么问题；
- Diff 为什么适合作为检索种子，却不能代替完整上下文；
- Recall@K、Precision@K、HitRate@K 和 MRR 的差异；
- Token 预算为什么需要返回裁剪原因；
- 为什么字符估算不能替代真实模型 tokenizer；
- 为什么向量召回加入后仍需要关键词、符号和关系图混合检索。

## 7. 验收记录（2026-08-04）

- 后端全量测试：69 项通过，0 failure、0 error、0 skipped；包含项目/revision 隔离、跨项目种子拒绝、预算裁剪、最新 Diff 上下文和固定评测指标。
- 前端测试：3 个测试文件、14 项测试通过；生产构建通过。
- 后端生产打包通过。
- 真实 MySQL 26.7：Flyway V8 执行成功，`retrieval_evaluation_case`、`retrieval_evaluation_run` 已创建，健康检查为 `UP`。
- 浏览器联调：项目列表和检索弹窗正常显示，输入、Top-K、Token 预算、最新 Diff/项目检索入口与错误态可用，控制台无警告和错误。
- 真实库中的旧 revision 只有文档记录且 `chunk_count=0`，检索接口明确返回“尚未建立知识索引”，没有误报为空结果；重新导入因当时 GitHub 网络超时失败，项目状态已恢复，待网络可用后重新建索引。
