# 向量索引与混合 RAG

## 1. 本阶段完成的业务闭环

```text
项目当前 revision 的 knowledge_chunk
  → 选择 Embedding Provider
  → 分批生成向量
  → 按项目、revision、模型版本持久化
  → 用户问题生成查询向量
  → 关键词 / 关系图 / 向量三路召回
  → RRF 融合排序
  → Top-K 与 Token 预算裁剪
  → 使用固定评测集比较检索模式
```

向量索引和源码导入是两个独立步骤。源码重新导入产生新 revision 后，旧向量不会参与新版本检索，必须为新版本重新建立索引。

## 2. 两种 Embedding Provider

### `LOCAL`

- 默认启用，不需要外部密钥，适合自动化测试和离线开发。
- 使用词项与字符三元组哈希生成确定性向量。
- 它只能验证索引、版本隔离、余弦相似度和融合流程，**不是语义模型，不能把测试结果包装成真实语义 RAG 效果**。

### `DASHSCOPE`

- 通过 OpenAI 兼容的 HTTPS Embedding 接口调用真实模型。
- 默认模型为 `text-embedding-v4`，默认维度为 1024，批次上限为 10。
- API Key 只从环境变量读取，不写入数据库、任务错误或 Git。
- 项目源码片段会发送给所配置的远端服务；私有代码必须先获得数据合规授权。

本地运行真实模型时使用进程环境变量：

```bash
DEVMATE_EMBEDDING_PROVIDER=DASHSCOPE
DEVMATE_EMBEDDING_MODEL=text-embedding-v4
DEVMATE_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
DASHSCOPE_API_KEY=<仅保存在本机环境>
DEVMATE_EMBEDDING_DIMENSIONS=1024
```

## 3. 接口

建立或续建当前版本索引：

```http
POST /api/projects/{projectId}/embeddings/index
```

查询最近索引任务：

```http
GET /api/projects/{projectId}/embeddings/tasks/latest
```

检索请求增加 `retrievalMode`：

- `LEXICAL`：关键词、符号、Diff 种子和关系图基线。
- `VECTOR`：向量召回，同时保留 Diff/关系图的确定性证据。
- `HYBRID`：关键词与向量采用 RRF 融合，再执行统一预算裁剪。

如果请求 `VECTOR/HYBRID` 但当前 revision 没有兼容向量，接口明确返回 `LEXICAL_FALLBACK` 和降级原因，不会把关键词结果伪装成混合检索。

## 4. 幂等与失败恢复

- `vector_id` 由项目、Chunk、provider、模型、维度和内容哈希共同计算。
- 已成功保存的批次再次执行时会跳过，失败任务可以续建。
- 远端请求不占用长数据库事务；每个批次使用短事务保存向量并更新进度。
- `embedding_index_task` 保存总数、成功数、跳过数、失败数和脱敏错误。
- `knowledge_chunk.vector_id` 只在向量记录保存成功后更新。
- 当前 revision 的全部批次成功后，相关 `knowledge_document.status` 才从 `PARSED` 更新为 `INDEXED`。

## 5. 当前向量存储取舍

第一版使用 MySQL `embedding_vector.vector_json` 保存向量，在 Java 内执行余弦相似度，目的是以最低基础设施成本完成可测试的端到端适配层。

限制：

- 它是有扫描上限的精确线性搜索，不是 ANN 索引。
- 适合个人项目和小型评测集，不适合大规模生产代码库。
- `EmbeddingProvider` 和向量检索服务已经隔离存储细节；数据规模增长后可替换为 Elasticsearch、pgvector 或 Milvus，而不改 Agent 和审查流程。

## 6. 评测约束

`retrieval_evaluation_run` 记录本次实际检索模式和配置版本。评测请求 `HYBRID` 时如果发生关键词降级，运行会直接拒绝，避免把 fallback 指标当作混合检索成绩。

必须使用同一个数据集分别运行 `LEXICAL`、`VECTOR` 和 `HYBRID`，比较 Recall@K、Precision@K、HitRate@K 与 MRR，并人工分析失败案例。没有固定标注数据时，不在简历中声称准确率提升。

## 7. 面试需要掌握

- Embedding 为什么能支持语义检索，本地哈希向量为什么不能冒充语义模型。
- 余弦相似度、向量维度、归一化和 Top-K 的含义。
- 为什么必须按 `project_id + revision + provider + model + dimensions` 隔离。
- 为什么混合检索通常比只用关键词或只用向量更稳。
- RRF 如何融合不同量纲的排序结果。
- 为什么外部模型调用不能包在数据库长事务里。
- 幂等向量 ID、分批保存和失败续建如何工作。
- MySQL 线性扫描方案的边界，以及未来替换专业向量库的条件。
