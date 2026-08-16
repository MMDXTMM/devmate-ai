# 本地开发与多端同步

## 代码和数据库如何同步

Git 同步：

- Java 源码
- 构建文件
- Flyway 数据库迁移脚本
- 公共配置
- 本地配置模板

Git 不同步：

- 数据库物理文件
- 本机测试产生的数据
- 数据库密码
- API Key
- 本地源码路径
- 上传文件和日志

每台电脑使用自己的本地数据库。数据库结构由 Flyway 脚本自动升级，必要的演示数据也通过迁移脚本或专用 seed 机制维护。

## 推荐工作方式

### 快速开发

默认启动会激活 `local` Profile 并连接本机 MySQL：

```bash
./mvnw spring-boot:run
```

数据会持久化到 `devmate` 数据库，重启后仍然存在。自动化测试另行使用临时 H2。

### MySQL 联调

复制 `application-local.yml.example` 为 `application-local.yml` 并填写当前电脑的账号密码。配置完成后直接启动即可，不需要每次手动指定 Profile。

自动化测试使用显式的 `test` Profile，数据源仍是 H2 内存数据库，因此执行测试不会修改本地 MySQL。

不要提交 `application-local.yml`。

### 云端环境

项目稳定后，可以使用云服务器或托管数据库作为测试/演示环境，但不建议让日常开发直接共用生产数据库。

合理分层：

- 本地：每位开发者自己的 MySQL；自动化测试使用 H2。
- 测试/演示：云端共享测试数据库。
- 生产：独立网络、最小权限、备份和监控。

## 数据库变更规则

1. 已在其他环境执行过的迁移脚本不可修改。
2. 每次结构变更新增脚本，例如 `V2__add_project_status.sql`。
3. 不直接手工修改共享数据库后忘记补迁移。
4. 提交代码前运行完整测试。

## 两台电脑的首次准备

```bash
git clone <repository-url>
cd devmate-ai
./mvnw test
./mvnw spring-boot:run
```

如果使用 MySQL，两台电脑分别创建 `devmate` 数据库并维护自己的 `application-local.yml`。

## 私有 Git 仓库凭证

源码导入需要访问私有仓库时，在启动 Spring Boot 的同一个终端会话中设置 `DEVMATE_GIT_USERNAME` 和 `DEVMATE_GIT_TOKEN`。Token 只存在于当前进程环境，不写入数据库。

每台电脑需要分别配置自己的凭证。不要通过 Git、聊天记录或共享配置同步 Token。详细操作见 [Git 源码导入闭环](SOURCE_IMPORT.md#私有-github-仓库)。

## AI 审查模型密钥

需要执行真实 AI 审查时，先为后端配置稳定的 `DEVMATE_MODEL_ENCRYPTION_SECRET`，再在页面“大模型连接”中由当前账户保存并测试 DeepSeek、通义千问或 OpenAI Key。Key 以密文持久化且不回显。

只有把远程向量 Provider 切换为 DashScope 时，才需要在启动后端的同一终端设置：

```bash
export DASHSCOPE_API_KEY='<your-key>'
./mvnw spring-boot:run
```

该环境密钥只供 DashScope Embedding 使用。开发和自动化测试不提交密钥；没有远程 Embedding Key 时可以继续使用本地确定性 Embedding。
