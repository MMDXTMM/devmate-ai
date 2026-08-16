# 大模型连接中心

第一版支持 DeepSeek、通义千问和 OpenAI。页面可以选择提供方与模型、填写 API Key、保存并启用连接，以及显式执行最小连接测试。

## Spring AI 接入边界

项目使用 Spring AI 1.1.8 的 `ChatModel` 与 `ChatClient` 统一 OpenAI 兼容模型调用。该版本与当前 Spring Boot 3.5 基线兼容；不为追求新版本而把整个项目升级到 Spring Boot 4。

这里没有使用只能在启动时绑定一套全局 Key 的自动配置，而是由 `SpringAiChatClientFactory` 根据当前登录账户的已解密配置创建短生命周期客户端。这样仍然满足：

- 一个账户只能启用一个提供方连接；
- 提供方地址由后端白名单决定，用户不能提交任意请求地址；
- API Key 只在模型调用边界短暂使用，不进入响应、日志或 Prompt；
- 连接超时为 5 秒、读取超时为 30 秒，连接测试不自动重试；
- DeepSeek、通义千问和 OpenAI 通过同一 Spring AI 调用契约接入。

账户模型已经接入固定流水线审查、Agent 审查和中文项目深度报告，不再只用于连接测试：

- 固定流水线通过 Spring AI `ChatClient.responseEntity(...)` 把模型输出转换为 `AiReviewFinding` 结构；
- Agent 通过 Spring AI `ChatModel` 发送工具定义和多轮消息，并关闭框架内部工具执行，由现有 Java 编排器继续执行权限、参数、超时、循环上限和审计；
- 远程 DashScope Embedding 通过 Spring AI `EmbeddingModel` 调用；本地确定性 Embedding 作为无网络降级能力继续保留；
- 每次审查固定使用任务开始时解析出的账户模型快照，provider 和 model 同步写入现有任务审计。
- 项目理解报告通过 `ChatClient.responseEntity(...)` 获得结构化业务流程，只允许模型引用 Java 服务提供的证据 ID；真实路径、行号和代码由服务端校验并回填。

因此模型协议、结构化输出、Token 元数据和 Tool Calling 已进入 Spring AI 契约；业务权限、证据校验、任务状态和持久化仍由 DevMate Java 服务负责。

连接按登录用户隔离。API Key 使用服务端环境变量 `DEVMATE_MODEL_ENCRYPTION_SECRET` 派生的 AES-GCM 密钥加密后保存到 MySQL，页面和接口永远不回显明文；后端重启后仍可读取，其他账户或提供方不能复用该密文。加密主密钥不进入数据库、日志、响应或 Git，部署时必须保持稳定并通过环境注入。提供方地址由服务端白名单固定，前端不能提交任意 URL。401/403、429 和网络失败会转换为可读错误。

当前模型包括：

- DeepSeek：`deepseek-v4-flash`、`deepseek-v4-pro`；
- 通义千问：`qwen-plus`、`qwen3.7-plus`、`qwen3-coder-plus`、`qwen-flash`；
- OpenAI：`gpt-5.1`、`gpt-5-mini`、`gpt-4.1`。

模型名称与兼容接口以官方文档为准：

- [DeepSeek API 文档](https://api-docs.deepseek.com/)
- [阿里云百炼兼容 OpenAI 接口](https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope)
- [OpenAI API 模型文档](https://platform.openai.com/docs/models)

如果服务端未配置加密主密钥，新 API Key 会拒绝保存并返回可读错误；如果主密钥发生变化，历史密文不能解密，用户需要重新填写 Key。

项目深度报告只有用户显式点击时才调用模型；同一个 `attemptKey` 幂等返回，同项目同 revision 同时只运行一项，超时任务自动释放重试入口。真实厂商输出效果需要用户使用自己的 API Key 页面验收，Mock 测试结果不作为模型准确性证明。
