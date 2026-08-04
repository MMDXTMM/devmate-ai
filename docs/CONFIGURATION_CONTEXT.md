# 配置上下文解析与关联

## 1. 目标

代码审查不能只看 Java 方法。超时、线程池、缓存、数据库连接池和功能开关等配置会直接改变代码在生产环境中的行为。本阶段把 `application.yml`、`application.yaml` 和 `.properties` 纳入项目上下文，并把 Java 配置引用关联到真实定义。

## 2. 处理流程

```text
Git 仓库
  → 受限扫描 Java / YAML / Properties
  → Java 使用 JDK AST 提取 @Value 与 @ConfigurationProperties
  → YAML/Properties 展平为 CONFIG_PROPERTY
  → 保存配置文档和脱敏配置 Chunk
  → 按精确键或前缀建立 code_reference
  → API 和 Vue 展示“代码 → 配置项 → 配置文件”证据
```

示例：

```java
@Value("${review.limit:20}")
private int reviewLimit;
```

```yaml
review:
  limit: 50
```

会生成 `CONFIG_KEY(review.limit)`，并解析到 `CONFIG_PROPERTY(review.limit)`。`@ConfigurationProperties(prefix = "review")` 会关联 `review.*` 下的所有配置项。

## 3. 数据模型复用

本阶段没有新增表：

- `knowledge_document.source_kind = CONFIGURATION`；
- `knowledge_document.file_type = YAML/PROPERTIES`；
- `knowledge_chunk.chunk_type = CONFIG_PROPERTY`；
- `knowledge_chunk.symbol_name` 保存展平后的配置键；
- `code_reference.target_chunk_id` 指向配置定义。

同一键可能在多个 Profile 文件或多个 YAML 文档中出现，因此一个 Java 引用可以对应多个候选定义。系统保留全部候选和文件路径，不擅自判断运行时最终生效值。

## 4. 安全边界

- 使用 SnakeYAML `SafeConstructor`，关闭递归键，限制别名、嵌套深度和输入长度；
- 主动拒绝同一映射或 Properties 文件中的重复键，避免歧义；
- 延续符号链接、目录、文件数量、单文件和总容量限制；
- 数据库只保存配置键、值类型、行号和脱敏摘要，不保存原始配置值；
- `password`、`secret`、`token`、`credential`、`api-key` 等敏感键统一标记为 `<redacted>`；
- 不执行目标项目，也不解析 Spring 运行时环境变量中的实际秘密。

## 5. 已知边界

- Spring Profile、环境变量和配置中心会影响最终值，静态文件只能提供候选定义；
- 当前不解析 `@Bean` 自定义绑定、SpEL 运算和动态拼接配置键；
- 前缀关联用于构建上下文，不能单独证明某个字段在运行时一定读取了该配置；
- 配置值变化由文档内容哈希识别，但为避免秘密泄露，配置 Chunk 的内容哈希基于脱敏摘要。

## 6. 面试需要掌握

- 为什么代码审查需要配置上下文，而不只是源码和 Diff；
- `@Value` 精确键与 `@ConfigurationProperties` 前缀绑定的区别；
- 为什么同一配置键可能有多个定义，不能静态断言最终生效值；
- 为什么秘密不能写入知识库、向量库、日志或 Prompt；
- 为什么 YAML 必须使用安全构造器并限制别名和嵌套深度；
- 为什么本阶段复用知识表和关系表，而不是再建一套配置专用表。

## 7. 验收证据

- 单元测试覆盖嵌套 YAML、数组、Properties 续行、重复键和敏感值脱敏；
- 接口测试覆盖配置文档入库、配置 Chunk、精确键/前缀关联和目标文件路径；
- 后端 54 项测试、前端 11 项测试和生产构建通过；
- 使用本机 MySQL 26.7 验证 Flyway V7 状态、应用启动、健康检查和只读项目查询均正常。
