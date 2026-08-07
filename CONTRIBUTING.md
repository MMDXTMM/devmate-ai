# DevMate AI 开发检查清单

详细规则见 [工程开发与运维规范](docs/ENGINEERING_STANDARDS.md)。每次开发以一个可验证的小闭环为单位。

## 开始开发

1. 从最新 `main` 创建 `codex/<feature>` 或 `feature/<feature>` 分支。
2. 写清业务目标、接口、数据变化、异常和不做的范围。
3. 确认修改属于哪个模块，不跨模块直接使用内部 Mapper。
4. 确认是否涉及数据库迁移、私有数据或不可回滚操作。

## 提交前

```bash
./mvnw test
cd frontend
npm test -- --run
npm run build
```

然后确认：

- 新增逻辑包含正常、边界和失败测试。
- 没有密码、Token、`.env`、`application-local.yml`、日志或 `workspace/` 文件。
- Flyway 旧迁移没有被修改。
- API 中的雪花 ID 仍以字符串返回前端。
- 日志可以定位项目和任务，但不包含私有源码全文。
- 文档和实际实现保持一致。
- 推送后等待 GitHub Actions 的 Backend、Frontend 和 Benchmark tools 三路检查通过。

## Pull Request 描述

PR 至少说明：

- 为什么修改。
- 核心业务流程和状态变化。
- 主要文件。
- 如何测试以及测试结果。
- 风险、限制和回滚方法。
- 是否涉及数据库、配置、权限或密钥。

一个 PR 只解决一个主题。格式化、重构和功能修改尽量分开，方便审查和回滚。

CI 使用 Java 21、Node 22、锁定的 Maven/npm 依赖和最小只读权限。远端检查失败时先用相同命令本地复现；不能用反复重跑隐藏确定性失败。CI 的 H2/Mock/Fake 结果不代替真实 MySQL、Git 或模型验收。
