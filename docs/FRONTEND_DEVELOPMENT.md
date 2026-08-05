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
- AI 审查弹窗打开时只读取历史任务，必须由用户显式点击后才调用模型和消耗额度。
- AI Finding 展示服务端证据位置、事实/推断/待验证、置信度、风险场景、建议和验证方法。
- AI 审查提供“固定流水线”和“Agent 智能取证”两个显式入口；Agent 结果展示脱敏工具调用顺序、状态和耗时。
- 每条 AI Finding 支持填写可选备注并标记采纳、驳回、误报或稍后处理；保存反馈不会重跑模型。

## 3. 目录职责

```text
frontend/
├── src/App.vue                         页面状态和业务交互
├── src/components/ProjectFormModal.vue 新建/编辑表单
├── src/components/AiReviewModal.vue    AI 审查入口与报告
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
- Agent 模式必须显式触发，且工具调用链可见；前端不发送模型密钥或项目 revision。
- Finding 反馈路径、字符串 ID、局部状态更新和后端失败提示。

执行：

```bash
cd frontend
npm test
npm run build
npm audit
```

2026-08-05 自动化回归通过：24 项前端测试与生产构建成功；两种 AI 审查均需显式触发，Agent 工具链可见，反馈不会重新消费模型额度，失败任务不会被展示为无风险。真实模型和 MySQL 联调结果记录在对应阶段文档。

## 6. 后续演进

- 将当前项目表格上的审查弹窗演进为独立任务列表和结果工作台。
- 登录后在请求层统一携带 Token。
- 生产部署时由 Nginx 将 `/api` 转发给后端，不依赖 Vite 开发代理。
