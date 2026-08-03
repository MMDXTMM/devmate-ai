# 项目管理模块

## 1. 模块职责

`project` 代表一个接入 DevMate AI、等待建立知识库和执行代码审查的 Java 项目。它是用户、源码、索引任务、会话和审查结果之间的核心业务对象。

## 2. `project` 表字段

| 字段 | 含义 | 创建时处理方式 |
|---|---|---|
| `id` | 项目唯一标识 | MyBatis-Plus 自动生成 |
| `owner_id` | 项目所有者 | 认证模块完成后从当前用户写入 |
| `name` | 项目名称 | 必填，最长 100 个字符 |
| `description` | 项目说明 | 可选，最长 500 个字符 |
| `source_type` | 源码来源 | `LOCAL`、`GIT` 或 `UPLOAD`，默认 `LOCAL` |
| `source_location` | 本地路径、仓库地址或上传文件位置 | Git 项目必填 |
| `default_branch` | 默认分支 | 可选，例如 `main` |
| `current_revision` | 当前已索引提交 | 建立代码索引后写入 |
| `status` | 项目状态 | 创建时为 `CREATED` |
| `deleted` | 逻辑删除标记 | 创建时为 `0` |
| `created_at` | 创建时间 | Service 写入当前时间 |
| `updated_at` | 最后更新时间 | Service 写入当前时间 |
| `last_indexed_at` | 最后索引时间 | 完成代码索引后写入 |

## 3. 创建项目接口

```http
POST /api/projects
Content-Type: application/json
```

请求示例：

```json
{
  "name": "demo-service",
  "description": "用于代码审查的示例项目",
  "sourceType": "GIT",
  "sourceLocation": "https://github.com/example/demo-service.git",
  "defaultBranch": "main"
}
```

成功时返回 HTTP `201 Created`，统一响应中的 `code` 为 `0`，项目状态为 `CREATED`。

## 4. 请求执行链路

```text
HTTP JSON
   ↓
CreateProjectRequest：格式和长度校验
   ↓
ProjectController：接收请求并返回 201
   ↓
ProjectService：默认值和业务规则校验
   ↓
ProjectMapper：生成 SQL 并写入 project 表
   ↓
ProjectResponse：只向客户端暴露需要的字段
```

DTO 不直接复用 `Project` 实体，原因是接口参数、数据库字段和响应内容会分别演进。这样可以避免客户端修改 `status`、`deleted` 等只能由服务端控制的字段。

## 5. 当前边界

- 暂未实现登录认证，`owner_id` 当前允许为空。
- 暂未拉取 Git 仓库；创建接口只保存项目元数据。
- 暂未建立知识库和执行代码审查。
- 项目管理基础 CRUD 已完成，下一步补充 OpenAPI 文档并进入源码导入阶段。

## 6. 查询项目详情

```http
GET /api/projects/{projectId}
```

执行链路：

```text
路径中的 projectId
   ↓
Controller：校验 ID 必须大于 0
   ↓
Service：开启只读事务
   ↓
Mapper：根据主键查询，并自动过滤 deleted = 1
   ↓
找到项目：返回 200
没有找到：抛出 BusinessException，返回 404
```

查询 SQL 类似：

```sql
SELECT *
FROM project
WHERE id = ?
  AND deleted = 0;
```

只读事务用于表达该方法只查询、不修改数据。项目不存在属于可预期的业务结果，因此返回 HTTP `404 Not Found`，而不是系统内部错误 `500`。

## 7. 真实 MySQL 验收记录

已使用 `local` Profile 完成以下闭环验证：

1. 通过 `POST /api/projects` 创建项目。
2. 在 DataGrip 的 MySQL `devmate.project` 表中确认数据已提交。
3. 使用返回的项目 ID 调用 `GET /api/projects/{projectId}`。
4. 接口成功返回与 MySQL 一致的项目数据。

排查过程中发现，IDEA 未启用 `local` Profile 时会使用 `application.yml` 中的 H2 内存数据库，而 DataGrip 查询的是 MySQL，因此会出现“DataGrip 有数据、接口返回 404”的现象。开发时必须确认应用与数据库客户端连接的是同一数据源。

## 8. 分页查询项目

```http
GET /api/projects?page=1&size=20&name=devmate&status=CREATED
```

参数规则：

| 参数 | 默认值 | 规则 |
|---|---:|---|
| `page` | `1` | 必须大于 0 |
| `size` | `20` | 1–100 |
| `name` | 无 | 对项目名称进行包含匹配 |
| `status` | 无 | `CREATED`、`INDEXING`、`READY` 或 `FAILED` |

MyBatis-Plus 分页插件会先查询符合条件的总数，再生成带 `LIMIT` 的分页 SQL。返回结果包含当前页、每页数量、总记录数、总页数和项目列表。

查询使用动态条件：没有传入名称或状态时，不把对应条件加入 SQL。所有查询仍会自动增加 `deleted = 0`。

## 9. 修改项目元数据

```http
PUT /api/projects/{projectId}
Content-Type: application/json
```

PUT 请求完整提供可编辑的项目元数据：

```json
{
  "name": "devmate-ai",
  "description": "智能代码审查 Agent",
  "sourceType": "GIT",
  "sourceLocation": "https://github.com/MMDXTMM/devmate-ai.git",
  "defaultBranch": "main"
}
```

客户端只能修改名称、描述、源码类型、源码位置和默认分支。以下字段由系统管理，不能通过该接口修改：

- `owner_id`
- `status`
- `current_revision`
- `deleted`
- `created_at`
- `last_indexed_at`

更新 SQL 使用字段白名单，而不是直接对查询出的完整实体执行 `updateById`。原因是完整实体还包含状态和索引版本；如果后台索引任务并发修改了这些字段，元数据更新可能把新状态覆盖为旧值。当前 SQL 只写入允许修改的列和 `updated_at`，缩小并发覆盖范围。

## 10. 逻辑删除项目

```http
DELETE /api/projects/{projectId}
```

`Project.deleted` 使用 MyBatis-Plus 的 `@TableLogic`，所以 `deleteById` 生成的实际 SQL 是：

```sql
UPDATE project
SET deleted = 1
WHERE id = ?
  AND deleted = 0;
```

删除后数据库记录仍存在，但详情和分页查询都会自动过滤它。重复删除时更新行数为 0，接口返回 HTTP 404。自动化测试同时使用 Mapper 验证业务查询不可见，并使用原生 SQL 验证 `deleted` 已变为 1。

## 11. 大整数 ID 的 JSON 处理

项目 ID 使用 MyBatis-Plus 生成的 `BIGINT` 雪花 ID，数值可能超过 JavaScript 的安全整数上限 `2^53 - 1`。如果直接作为 JSON 数字返回，浏览器可能改变末尾数字，导致后续查询错误。

因此数据库和 Java 内部仍使用 `BIGINT/Long`，但 `ProjectResponse.id` 在 JSON 中序列化为字符串：

```json
{
  "id": "2084116785588305922"
}
```

客户端传回路径参数时，Spring 再把字符串形式的数字转换成 `Long`。
