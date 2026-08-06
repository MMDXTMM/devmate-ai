# 前端开发与联调

## 1. 技术方案

DevMate AI 前端位于根目录下的 `frontend`，采用独立的 Vue 3、TypeScript 和 Vite 工程。前后端在开发时分别运行：

```text
浏览器 http://localhost:5173
              ↓ /api
Vite 开发代理
              ↓
Spring Boot http://localhost:8080
              ↓
MySQL 或 H2
```

选择独立前端而不是把静态页面放进 Spring Boot，原因是前后端可以独立构建、测试和部署，后续增加代码审查工作台时也更容易维护。

## 2. 当前页面能力

- 展示项目总数、分页信息和项目列表。
- 按项目名称和状态筛选。
- 创建 Git、Local 和 Upload 类型项目。
- 编辑项目名称、描述、源码位置和默认分支。
- 二次确认后逻辑删除项目。
- 展示后端参数校验、业务异常和连接失败信息。
- 使用字符串保存项目 ID，避免雪花 ID 在 JavaScript 中丢失精度。
- 支持加载、空数据、成功和失败状态。
- 支持源码结构、Diff、静态分析、向量化、RAG 检索和 AI 审查报告。
- “基础分析”按源码导入、Diff、静态分析和向量索引的依赖顺序执行，失败即停，成功后打开 AI 审查工作台。
- AI 审查弹窗打开时只读取历史任务，必须由用户显式点击后才调用模型和消耗额度。
- 用户触发 AI 审查后，前端先读取最近 Diff 并要求状态为 `SUCCEEDED`，再把字符串 Diff ID 和对应的 40 位目标 revision 作为创建请求体发送；读取失败、状态失败或缺少目标 revision 时不会调用创建接口。
- AI 审查请求固定触发时的项目 ID，并使用视图代次和运行标识忽略项目切换后的旧响应；加载或运行期间的重复触发会在代码入口被拒绝。
- AI Finding 展示服务端证据位置、事实/推断/待验证、置信度、风险场景、建议和验证方法。
- AI 审查提供“固定流水线”和“Agent 智能取证”两个显式入口；Agent 结果展示脱敏工具调用顺序、状态和耗时。
- 每条 AI Finding 支持填写可选备注并标记采纳、驳回、误报或稍后处理；保存反馈不会重跑模型。
- 支持为最近成功 Diff 录入版本化的缺陷/无缺陷标准答案，并评测最近一次已完成 AI 审查。
- 支持并排比较 FIXED/AGENT 的质量、Token、耗时和工具成功率；部分指标会显式提示，前端不能指定或篡改任务执行模式。

## 3. 目录职责

```text
frontend/
├── src/App.vue                         页面状态和业务交互
├── src/components/ProjectFormModal.vue 新建/编辑表单
├── src/components/AiReviewModal.vue    AI 审查入口与报告
├── src/components/ReviewEvaluationModal.vue 固定评测集与 A/B 对比
├── src/services/projectApi.ts          HTTP 请求和统一异常
├── src/types/project.ts                接口类型
├── src/style.css                       页面样式和响应式布局
└── vite.config.ts                      后端代理和测试配置
```

组件不直接拼接后端响应逻辑，所有请求统一经过 `projectApi`。后续若增加认证 Token、请求追踪 ID 或全局错误码，只需要修改请求层。

## 4. 启动方式

先启动 Spring Boot：

```bash
./mvnw spring-boot:run
```

再启动 Vue：

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。不要直接访问 `http://localhost:8080` 寻找前端页面，8080 当前只提供后端 API。

## 5. 测试与验收

前端自动化测试覆盖：

- 分页和筛选参数拼接。
- 创建项目后雪花 ID 保持字符串。
- 后端业务异常展示。
- 后端无法连接时的明确提示。
- Git 项目仓库地址表单校验。
- 表单数据提交前去除首尾空格。
- AI 审查必须显式触发，且结构化证据和验证步骤可见。
- Agent 模式必须显式触发，且工具调用链可见；前端不发送模型密钥，创建 FIXED/AGENT 审查时会发送字符串 Diff ID 和对应的 40 位目标 revision。
- AI 审查前置 Diff 查询失败、Diff 未成功或缺少 revision 时保持零创建；后端拒绝已经漂移的 Diff 时只重载历史记录，不自动重试模型调用。
- AI 审查在项目切换后忽略旧历史和旧创建响应，Diff 查询期间切换项目不会继续创建，重复点击不会形成并发创建请求。
- Finding 反馈路径、字符串 ID、局部状态更新和后端失败提示。
- 评测标准答案字段约束、显式运行、FIXED/AGENT 快照对比，以及评测请求不发送执行模式。
- 基础分析严格按四阶段顺序执行，并在任一阶段失败后停止后续调用。

执行：

```bash
cd frontend
npm test
npm run build
npm audit
```

2026-08-06 自动化回归结果：46 项前端测试与生产构建成功；新增登录/注册页面，JWT 只存放在 `sessionStorage`，统一 API 客户端添加 Bearer Token 并在 401 时清理会话。两种 AI 审查均需显式触发并绑定最近成功 Diff 的字符串 ID、40 位 revision 和每次新生成的 UUID v4 attemptKey，前置失败保持零创建，项目切换后的旧响应和重复触发不会覆盖当前审查状态。Agent 工具链可见，反馈不会重新消费模型额度，失败任务不会被展示为无风险。评测页只计算已保存任务，不会自动调用模型。真实模型和 MySQL 联调结果记录在对应阶段文档。

## 6. 后续演进

- 将当前项目表格上的审查弹窗演进为独立任务列表和结果工作台。
- 登录后在请求层统一携带 Token。
- 生产部署时由 Nginx 将 `/api` 转发给后端，不依赖 Vite 开发代理。
