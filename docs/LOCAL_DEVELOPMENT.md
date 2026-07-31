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

默认启动使用 H2 内存数据库：

```bash
./mvnw spring-boot:run
```

每次重启数据会清空，适合开发接口和跑测试。

### MySQL 联调

复制 `application-local.yml.example` 为 `application-local.yml`，填写当前电脑的账号密码，使用 `local` Profile 启动。

不要提交 `application-local.yml`。

### 云端环境

项目稳定后，可以使用云服务器或托管数据库作为测试/演示环境，但不建议让日常开发直接共用生产数据库。

合理分层：

- 本地：每位开发者自己的 H2/MySQL。
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

