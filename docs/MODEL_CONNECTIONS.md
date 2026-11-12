# 大模型连接中心

支持 DeepSeek、通义千问和 OpenAI；管理员还可启用一个受信任的兼容模型服务。页面可以选择提供方与模型、填写 API Key、保存并启用连接，以及显式执行最小连接测试。

## Spring AI 接入边界

项目使用 Spring AI 1.1.8 的 `ChatModel` 与 `ChatClient` 统一 OpenAI 兼容模型调用。该版本与当前 Spring Boot 3.5 基线兼容；不为追求新版本而把整个项目升级到 Spring Boot 4。

这里没有使用只能在启动时绑定一套全局 Key 的自动配置，而是由 `SpringAiChatClientFactory` 根据当前登录账户的已解密配置创建短生命周期客户端。这样仍然满足：

- 一个账户只能启用一个提供方连接；
- 官方地址固定，中转站地址可按账户选择和保存，必须属于服务端批准的 HTTPS 基础地址名单；
- API Key 只在模型调用边界短暂使用，不进入响应、日志或 Prompt；
- 连接超时为 5 秒、读取超时为 90 秒，连接测试不自动重试；读取超时与网络连接失败分别返回可读提示；
- DeepSeek、通义千问和 OpenAI 通过同一 Spring AI 调用契约接入。

账户模型已经接入固定流水线审查、Agent 审查、中文项目深度报告和项目多轮对话，不再只用于连接测试：

- 固定流水线通过 Spring AI `ChatClient.responseEntity(...)` 把模型输出转换为 `AiReviewFinding` 结构；
- Agent 通过 Spring AI `ChatModel` 发送工具定义和多轮消息，并关闭框架内部工具执行，由现有 Java 编排器继续执行权限、参数、超时、循环上限和审计；
- 远程 DashScope Embedding 通过 Spring AI `EmbeddingModel` 调用；本地确定性 Embedding 作为无网络降级能力继续保留；
- 每次审查固定使用任务开始时解析出的账户模型快照，provider 和 model 同步写入现有任务审计。
- 项目理解报告通过 `ChatClient.responseEntity(...)` 获得结构化业务流程，只允许模型引用 Java 服务提供的证据 ID；真实路径、行号和代码由服务端校验并回填。
- 项目多轮对话通过 `ChatClient.stream().content()` 获得增量文本；每轮由 Java 重新执行 Hybrid RAG，SSE 断开后最终回答仍会保存。

因此模型协议、结构化输出、Token 元数据和 Tool Calling 已进入 Spring AI 契约；业务权限、证据校验、任务状态和持久化仍由 DevMate Java 服务负责。

连接按登录用户隔离。API Key 使用服务端环境变量 `DEVMATE_MODEL_ENCRYPTION_SECRET` 派生的 AES-GCM 密钥加密后保存到 MySQL，页面和接口永远不回显明文；后端重启后仍可读取，其他账户或提供方不能复用该密文。加密主密钥不进入数据库、日志、响应或 Git，部署时必须保持稳定并通过环境注入。中转站允许提交受信任名单内的基础地址并按账户保存，官方提供方地址固定。401/403、429 和网络失败会转换为可读错误。

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

## 新人配置引导（2026-11-11）

连接中心提供“厂商开放平台创建 Key → 保存并启用 → 显式测试”三步引导。页面说明网页会员与 API 额度分别管理、配置绑定当前账户、聊天模型与 Embedding 模型分别配置；测试成功后可返回项目继续报告或提问。关闭页面会清除未保存的明文 Key，下次打开重新读取服务端的已保存模型，不回显 Key。

问答和一键审查前读取账户配置；没有启用且已配置的连接时引导进入连接中心。该检查没有调用厂商 API，也没有以“已配置”替代真实连接成功。实际额度、模型权限和网络结果仍由用户显式测试和真实任务验证。

## 通用 OpenAI 兼容服务／中转站（2026-11-11）

这是通用入口，统一显示为“OpenAI 兼容／中转站”，用于兼容 Chat Completions 协议的公司网关或第三方中转服务。公司当前使用的地址和模型只属于本地配置，代码不绑定公司名称、域名或模型；更换服务时修改配置并更新当前账户的 Key 即可。仅支持 Responses 协议的服务目前不能直接接入。

`devmate.model-connections.compatible` 默认关闭，启用后返回第四个提供方 `COMPATIBLE`。管理员可用环境变量配置：

```sh
DEVMATE_COMPATIBLE_ENABLED=true
DEVMATE_COMPATIBLE_DISPLAY_NAME=OpenAI兼容中转站
DEVMATE_COMPATIBLE_BASE_URL=https://models.example.com/v1
DEVMATE_COMPATIBLE_MODELS=example-chat
```

以上地址和模型仅为示例。地址必须为不含凭证、查询或片段的 HTTPS URL，模型名单必须非空且无重复。中转站模型名单用作建议，页面可填写实际模型 ID（字母、数字、点、下划线、冒号、斜线、短横线，最多 100 字符）；地址必须在管理员批准的基础地址名单内。Key 继续通过当前账户连接接口加密保存，配置文件不包含 Key。未启用时提交 `COMPATIBLE` 返回 400。官方入口和已保存数据继续保留，V26 为账号新增可空 base_url，旧记录回退服务端默认地址。

接口仍使用 Spring AI 的 Chat Completions、结构化输出与工具调用契约；没有新增 Responses 协议客户端。当前用户指定的兼容服务已实测支持 Chat Completions。对于兼容服务流式最后一包使用 `message`、省略 `delta` 的情况，适配器仅补上空增量并保留结束原因及用量，不重复输出完整回答；未结束却缺少增量的异常包会失败。官方提供方保留原解析路径。流式 WebClient 显式配置连接和读取超时。

真实连接、业务报告、审查和对话的具体观察及限制见 [本地联调记录](LOCAL_ACCEPTANCE_2026_11_11.md)，不能把连接成功视为模型准确性证明。


### 项目聊天的模型选择

聊天顶部可列出当前账号已配置提供方的建议模型及已保存的当前自定义模型，创建对话时可选传 `provider/model`，省略保持原有“当前启用模型”行为。二者必须同时指定。服务端仅解密当前账号已保存的该提供方 Key，不能由请求输入任意地址；模型权限以供应商实际响应为准。切换顶部模型会新建草稿，旧会话/消息保留原模型。其他报告与审查功能仍使用账号的当前启用连接。


## API 配置提醒与规范化表单（2026-11-12）

- 登录后点击“导入新项目”会只读检查当前账户是否存在 configured=true 且 active=true 的连接；未配置时打开独立提醒窗口，包含“暂不配置”和“去配置 API”。取消不创建项目、不调用模型；点击配置直接打开连接中心。检查失败显示实际读取错误，不误判为未配置。
- 生成/重新生成项目概要、发送聊天消息、固定/Agent 审查与一键完整审查都复用同一配置提醒。读取已有导览、聊天历史、静态源码及审查报告仍可使用，不要求再次配置。未发送的问题在取消提醒时保留。
- 中转站表单显示接口协议、API 请求基础地址、模型 ID 和只写 Key，并预览最终 POST 地址。完整 /chat/completions 或 /responses 地址可规范化为 API 根地址；协议不会随粘贴地址自动转换，当前实际请求仍是 Chat Completions。
- 截图中 base_url 对应基础地址，model 对应模型 ID；wire_api=responses 属于另一种接口，本项目当前不支持仅 Responses 的服务。连接名称、备注、官网、子代理和自动审批模型、本地 MCP 配置不参与本项目调用，不要求粘贴整份客户端配置。
- 地址必须为不含用户凭据、查询、片段和转义路径的 HTTPS URL；规范化首尾空白、域名大小写、默认 443、末尾斜线和操作路径，然后与受信任地址精确匹配。当前默认地址自动受信任；扩展地址由服务端 `devmate.model-connections.compatible.allowed-base-urls` 列表配置，最多 20 个，不允许浏览器提交任意 URL。
- 更换请求地址必须重新填写对应 Key，防止把旧站点密钥发送到新站点；不变地址可留空保留加密 Key。旧请求省略 baseUrl/protocol 时保持已保存地址；旧记录无 base_url 时使用当前服务端默认值。地址移出信任名单后真实调用会被拒绝。
- PUT /api/model-connections 兼容新增可选 baseUrl、protocol，protocol 仅接受 CHAT_COMPLETIONS，其他协议返回 400；GET 新增 protocol、allowedBaseUrls，并返回账号的 baseUrl、当前模型，永不返回 Key。配置完成后不会自动调用厂商，用户可显式测试。
- 验证：后端 265 项、前端 154 项和生产构建通过；本地 MySQL V26 迁移成功。原账号规范化保存与回读、旧 Key 密文不变、非法地址/协议 400、未认证 401、运行时 OpenAPI 均通过；原有兼容连接真实测试成功，约 3 秒。浏览器视觉效果仍待用户手动验收。
