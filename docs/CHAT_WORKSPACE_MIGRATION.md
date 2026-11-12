# 项目聊天工作台迁移设计

## 目标与来源

借鉴本机 Ragent 的独立对话布局、分组历史、输入框、Markdown 和停止交互，在 DevMate 的 Vue 3、Spring AI 与 MySQL 上实现。参考来源为 https://github.com/nageoffer/ragent （Apache-2.0）；本轮采用独立 Vue 实现，不搬运 React/AgentScope、凭据或示例数据库，也不改变现有 RAG 与项目权限边界。

## 数据与兼容

复用 conversation / conversation_message，无新表、列或迁移。保留所有旧消息、固定 revision 与模型快照、现有创建/查询/SSE 协议。消息状态列为 VARCHAR(32)，允许 CANCELLED，无需修改已执行的 V25。

- 创建对话的现有 POST 可选增加 provider/model，两者同时指定；不指定仍使用账号当前启用连接。只从当前用户已保存凭据和管理员模型白名单解析，不能输入 URL 或密钥。会话固定所选模型；切换账号全局启用模型后，旧会话仍可使用本账号已保存的原提供方凭据。界面换模型开启新对话，保留输入草稿和原有历史。

- 会话重命名：PUT /api/projects/{projectId}/agent-conversations/{conversationId}，请求 {title}，trim 后 1–100 字，返回 ConversationResponse。
- 会话删除：DELETE 同一路径，逻辑删除，返回统一 ApiResponse<Void>；用户在页面确认后才发送。历史记录不物理删除，已删除返回 404。
- 停止回答：POST /api/projects/{projectId}/agent-conversations/{conversationId}/messages/{messageId}/cancellations，返回 {messageId, cancellationRequested}。202 表示停止请求已受理，不代表已保存；SSE done 与历史结果才是最终状态。
- 增加消息终态 CANCELLED：保存已生成内容、证据及耗时，清除 running_key，取消的半段回答不进入后续模型历史。重放同一 attemptKey 返回已保存终态，不再次付费。

所有操作验证当前用户是项目成员且是会话所有者。会话管理用短事务与会话行锁，生成中重命名/删除返回 409；非法标题 400；越权 403。取消支持重复操作，已结束时返回 cancellationRequested=false，完成与取消竞态以服务端最终消息状态为准。

## 执行边界

单实例模块化单体内用有界任务池与活动任务登记，将停止信号传给 Reactor 上游取消订阅。登记覆盖已入队未执行任务；finally 移除，执行器拒绝也清理。不声称可撤销供应商已经计算的费用。关闭窗口仍是脱离浏览器读取，后台继续保存；停止按钮才取消模型订阅。

## 页面

全屏项目聊天工作台，左侧新对话、搜索、日期分组与会话管理；中间为宽度受限的回答和底部自适应输入框；右侧按本轮展示证据，可收起。移动端改为可切换面板。提供欢迎问题、纯文本用户消息、安全 Markdown（含表格/引用/列表/代码）、复制回答/代码、停止按钮与失败恢复。显示真实检索/生成状态，不伪造推理或工具调用轨迹。旧对话打开无需连接模型。

## 验收

先保存并推送备份 a567101。实现后执行 ./mvnw test、npm test -- --run、npm run build、git diff --check。覆盖身份隔离、标题边界、软删除、取消及重复取消、排队取消、后续可继续问、终态重放、前端停止/管理/复制/历史恢复与危险 Markdown。使用本机 MySQL 和当前账户模型进行最小真实两轮流式及停止验收；不改动原会话。浏览器访问若被拒绝，不绕过或宣称视觉验收通过。

本轮不迁移 Ragent 的长期记忆、AgentScope、MCP 执行器、Redis/MQ 或通用知识库，不把这些标记为已实现。模型连接配置沿用账号的“大模型连接”，聊天顶部在已配置提供方的白名单模型中选择；会话保留模型快照，换模型新建对话。

## 已执行验收（2026-11-11）

- 备份已推送到 origin/codex/chat-workspace-migration-20261111，提交 a567101。
- 后端 218 项、前端 106 项测试全部通过；TypeScript / Vue 生产构建及 git diff --check 通过。
- 当前 MySQL、原账号、真实公司兼容模型 gpt-6.1-sol 经 Vite 5174 代理完成三轮：首轮 COMPLETED（首 token 3750 ms / 总计 5485 ms），次轮 CANCELLED（29 字、18 个 token 事件、3671 ms），末轮 COMPLETED（15505 ms）；每轮有 6 条证据。6 条消息重读一致；取消消息重放不新增记录，取消后 running_key 为 NULL，仍能继续提问。
- 真实接口验证重命名、非法标题 400、逻辑删除及删除后 404、未知消息 404、跨账号管理和读取 403、未登录 401；原有对话仍在列表。只逻辑删除本轮创建的临时空会话，数据库 deleted=1。
- 运行时 OpenAPI 核对 PUT/DELETE 成功码与停止 202，均有 bearerAuth 和字符串 ID。Flyway 成功迁移仍为 25 条，无新增迁移。
- 浏览器工具对 Ragent 页面访问被权限策略拒绝，没有绕过；未完成页面点击或视觉验收，当前证据为源码、组件测试与真实 HTTP。功能对照基于实际读取的 Ragent 源码。
- 引入 markdown-it 作为解析器，通过固定标签创建 Vue 节点；禁用模型 HTML、远程图片请求及危险链接，不使用 v-html。生产依赖审计中新增 Markdown 依赖没有命中公告，现有 Vue 依赖链仍有历史公告，未在本轮进行无关框架升级。

临时测试脚本、凭据和原始模型回答不进入 Git。效果验证仅覆盖上述样例，不代表任意业务问题的检索准确率。取消订阅后不保证撤销供应商已经产生的费用。
