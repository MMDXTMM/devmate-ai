# DevMate AI API 契约治理

## 1. 契约来源

DevMate AI 使用 Springdoc 从 Controller、DTO、Bean Validation 和 OpenAPI 注解生成契约：

- JSON：`GET /v3/api-docs`
- 调试页面：`GET /swagger-ui.html`

OpenAPI 描述请求和响应边界；Service 仍是业务规则、权限、状态流转和数据隔离的权威实现。

## 2. 全局约定

- 业务接口统一返回 `ApiResponse<T>`；成功业务码为 `0`。
- 分页统一返回 `PageResponse<T>`，页码从 `1` 开始，`size` 最大为 `100`。
- HTTP 与业务错误保持一致：400 参数、401 未认证、403 无权、404 不存在、409 冲突、500 内部错误。
- 数据库 `BIGINT/Long` ID 在 JSON 和前端类型中统一为 `string`，避免 JavaScript 精度丢失。
- 受保护接口使用 `bearerAuth` JWT 安全方案；前端不得把登录保护当成后端授权的替代品。
- Entity 不进入请求或响应；创建、更新、查询和响应使用独立 DTO。

## 3. 契约变更流程

每个 Java + Vue 纵向闭环按以下顺序执行：

1. 明确角色、目标、前置条件、正常流程、失败路径、权限和非目标。
2. 定义请求、响应、校验、错误、幂等或并发语义。
3. 实现后端并生成 `/v3/api-docs`。
4. 使用契约测试检查关键路径、状态、字段类型、枚举、必填项和认证声明。
5. 再调整 `frontend/src/types` 与 `frontend/src/services`，覆盖加载、成功、空数据和失败状态。
6. 运行后端、前端、生产构建与差异检查，记录未验证边界。

以下变化视为可能破坏兼容：删除或重命名字段、修改字段类型、把可选输入改为必填、收窄枚举、修改路径或方法、改变认证和错误语义。此类变化必须先说明影响，再修改前端。

## 4. 当前覆盖范围

当前 20 个 Controller、54 个 HTTP 操作均已纳入契约治理，并按业务职责分组：

- 认证、模型连接与健康检查。
- 项目管理、源码导入、源码结构与业务地图。
- 向量索引、Hybrid RAG 检索与检索评测。
- Git Diff、审查上下文、静态分析、AI 审查、反馈与审查评测。
- 一键代码审查工作流、AI 项目理解报告与项目多轮对话。
- 冻结但保留兼容的需求生成会话。

每个操作必须有中文业务摘要；受保护的 `/api/**` 操作统一声明 `bearerAuth`、401 和 403。注册、登录和健康检查保持公开。全局契约门禁会验证这些规则，并将 Java `Long` 类型的 `id/*Id/*Ids` 发布为 JSON 字符串，与 Jackson 和 Vue 的真实约定保持一致。

其中项目管理与一键审查还包含更细的端点级业务响应说明。

项目管理 CRUD：

- `POST /api/projects`
- `GET /api/projects`
- `GET /api/projects/{projectId}`
- `PUT /api/projects/{projectId}`
- `DELETE /api/projects/{projectId}`

一键代码审查工作流：

- `POST /api/projects/{projectId}/review-workflows`
- `GET /api/projects/{projectId}/review-workflows/latest`

创建接口同步执行 `SOURCE_IMPORT → DIFF → STATIC_ANALYSIS → EMBEDDING → AGENT_REVIEW → COMPLETED`。同一 `attemptKey` 幂等返回原运行；同项目已有不同键任务运行时返回 409。任一阶段失败后停止，并返回 `FAILED`、失败阶段、脱敏原因和恢复建议。最近运行不存在时返回 404。`attemptKey` 必须为小写 UUID v4。

契约测试固定验证 JWT、安全响应、全部操作摘要、字符串 ID、敏感 API Key 只写、幂等键格式、项目创建状态，以及审查工作流的状态、阶段和主要错误响应。

当前 Vue 仍使用集中式手写 API Client。全量核心契约稳定前不同时维护手写和生成两套客户端；后续会先固定 OpenAPI 快照和生成脚本，再迁移调用方。

## 5. 环境与安全

本地默认开启文档。生产或公开部署设置：

```bash
export DEVMATE_OPENAPI_ENABLED=false
```

Swagger UI 只用于查看和调试契约，不证明接口已经通过授权、并发、事务或真实数据库验收。API Key、JWT、私有仓库地址和真实源码不得写入示例、测试快照或提交记录。
