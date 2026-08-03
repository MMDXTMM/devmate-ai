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

## 3. 目录职责

```text
frontend/
├── src/App.vue                         页面状态和业务交互
├── src/components/ProjectFormModal.vue 新建/编辑表单
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

执行：

```bash
cd frontend
npm test
npm run build
npm audit
```

2026-08-03 联调验收通过：Vue 代理完成列表查询、创建、修改、删除和删除后 404 验证；页面及弹窗完成浏览器视觉检查，控制台无错误。联调使用临时 H2 数据，不影响本地 MySQL。

## 6. 后续演进

- 项目详情页和源码导入进度。
- 代码审查任务创建与结果工作台。
- 登录后在请求层统一携带 Token。
- 生产部署时由 Nginx 将 `/api` 转发给后端，不依赖 Vite 开发代理。
