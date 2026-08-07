# 持续集成与远端质量门禁

## 1. 目的

本地测试通过并不能证明推送到 GitHub 的提交仍然完整，也不能防止开发者漏跑某一组检查。`.github/workflows/ci.yml` 在每个 Pull Request 和 `main` 推送上执行三路独立检查：

| 检查 | 运行环境 | 命令 | 证明范围 |
| --- | --- | --- | --- |
| Backend | Temurin Java 21 | `./mvnw test` | Spring、业务状态、H2/Flyway、Mock/Fake 外部依赖 |
| Frontend | Node 22 | `npm ci`、测试、构建 | 锁定依赖、Vue 契约、TypeScript 和生产打包 |
| Benchmark tools | Node 22 | 两个 Node test 文件 | 真实样本验收器与 A/B 执行器的确定性行为 |

三路任务并行执行，单路失败能直接显示责任范围。相同分支推送新提交时取消旧运行，避免无意义消耗 GitHub Actions 分钟数。

## 2. 安全边界

- Workflow 只有 `contents: read` 权限。
- 不使用 `pull_request_target`，避免在高权限上下文执行不可信 PR 代码。
- 不注入数据库密码、Git Token、DashScope Key 或其他仓库 Secret。
- 后端测试使用 `test` Profile、H2 和 Mock/Fake，不访问本机 MySQL、GitHub 私有仓库或付费模型。
- `npm ci` 严格使用 `package-lock.json`，不会在 CI 中隐式改写依赖版本。

CI 日志和产物不得输出源码全文、Prompt、Token 或密码。未来若必须增加真实外部验收，应使用受保护环境、最小权限凭证和人工审批，不能直接放入普通 PR Workflow。

## 3. CI 不能证明什么

绿色检查不能替代：

- MySQL 方言、索引、锁和历史迁移的真实验收；
- GitHub 网络、私有仓库权限和大仓库资源限制；
- DashScope 模型/Embedding 的额度、延迟和实际效果；
- Docker、Nginx 与生产部署运行，因为当前开发机尚无可验证的 Docker 运行时。

这些结果继续在对应设计文档和 `PROJECT_LOG.md` 中单独记录。Mock/Fake 通过只能证明工程编排，不代表模型准确率。

## 4. 失败处理

1. 先判断失败属于 Backend、Frontend 还是 Benchmark tools。
2. 在本地使用同一命令复现，不通过反复重跑掩盖确定性失败。
3. 网络下载瞬时失败可以重跑一次；代码、测试和依赖错误必须修复后推送新提交。
4. 三路全部成功且 PR 无冲突后才合并；合并后的 `main` 会再次运行同一 Workflow。

### H2 测试隔离

CI 首次运行曾暴露测试顺序依赖：多个配置不同的 `@SpringBootTest` 上下文连接固定名称的
`jdbc:h2:mem:devmate`，非事务测试写入的数据会被另一个上下文看到。本机 Java 23 的执行顺序恰好没有触发，
而 CI Java 21 先执行了写入测试，导致后续空表断言失败。

`test` Profile 现在通过 `${random.uuid}` 为每个 Spring 测试上下文分配独立的 H2 数据库。同一上下文仍由
Spring Test 缓存并复用，事务测试继续在用例结束时回滚；不同上下文不再共享隐式全局状态。验证时除默认顺序外，
还应使用 Surefire 随机顺序运行，避免只在单一 JVM、JDK 或文件顺序下通过。

## 5. 面试需要掌握

- 为什么 PR 检查和 `main` 推送都要运行？
- 为什么三个 Job 并行，而不是一个脚本顺序执行全部命令？
- `npm ci` 与 `npm install` 在 CI 中有什么差别？
- 为什么 Workflow 只给 `contents: read`，为什么避免 `pull_request_target`？
- 为什么 CI 的 H2/Mock 结果不能代替真实 MySQL 和真实模型验收？
- 为什么不同 Spring 测试上下文不能共用固定名称的 H2 内存库？
- 并发取消如何节省资源，又为什么不能取消已经进入生产发布的任务？
