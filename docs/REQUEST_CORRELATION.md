# HTTP 请求追踪与日志关联

## 1. 解决的问题

前端只看到“资源状态冲突”或“系统内部错误”时，后端可能同时处理多个项目和任务。若日志没有稳定请求标识，排障只能按时间猜测，容易把不同用户或任务的日志拼在一起。

当前实现为每个 HTTP 请求建立一个 `requestId`：

```text
浏览器请求
  → 校验或生成 X-Request-Id
  → 写入请求属性、响应头和 MDC
  → Controller / Security / Service 处理
  → 记录方法、路径、状态、结果和耗时
  → finally 清理 MDC
```

前端在业务错误信息中显示安全的请求 ID，用户可以把它提供给维护人员；维护人员用同一个 ID 定位后端日志。

## 2. 输入、输出与失败路径

- 输入头：`X-Request-Id`，只接受 1-64 位字母、数字、点、下划线和连字符，首位必须是字母或数字。
- 合法输入会沿用，便于经过可信入口代理后保持一次请求的标识。
- 缺失、含空白/换行或超长输入会被替换为服务端 UUID，防止日志注入和无界日志字段。
- 输出头始终包含最终 `X-Request-Id`，正常响应、业务异常和 Spring Security 401/403 都遵守同一约定。
- 下游直接抛出未处理异常时，完成日志使用 `outcome=FAILED`；不能只依赖尚未更新的 HTTP 状态判断成功。
- 无论成功或失败都在 `finally` 删除 MDC，避免线程池复用时把上一个用户的 ID 带入下一个请求。

完成日志只记录 HTTP 方法、请求路径、响应状态、结果和耗时，不记录查询参数、Authorization、Cookie、请求体、源码或 Prompt。

## 3. 代码入口

- 后端入口：`src/main/java/com/devmate/common/web/RequestCorrelationFilter.java`
- 日志格式：`src/main/resources/application.yml`
- 前端错误关联：`frontend/src/services/apiClient.ts`
- 后端单元测试：`src/test/java/com/devmate/common/web/RequestCorrelationFilterTest.java`
- Spring MVC/Security 集成测试：`HealthControllerTest`、`AuthenticationAndProjectAccessTest`
- 前端契约测试：`frontend/src/services/apiClient.test.ts`

## 4. 设计边界

这是单体应用的 HTTP 请求关联，不等同于 OpenTelemetry 分布式追踪。当前 Git、Embedding 和模型任务已有各自的项目、任务和 attempt 标识，但没有把 W3C Trace Context 传播到所有外部服务。

只有真正拆分服务或需要跨进程追踪时，才引入 OpenTelemetry、采样和 Trace/Span。现在先解决“页面错误能否精确关联应用日志”的实际问题，避免为了展示技术栈增加没有消费者的追踪基础设施。

## 5. 面试需要掌握

1. MDC 是线程上下文，不是业务数据库；为什么请求结束必须清理？
2. 为什么不能无条件相信客户端传入的请求 ID？
3. 为什么响应状态可能仍为 200，但过滤器链实际已经失败？
4. `requestId`、AI `attemptKey` 和业务 `taskId` 分别解决什么问题？
5. 单体请求关联和分布式 Trace/Span 的差别是什么，何时值得引入 OpenTelemetry？

推荐回答重点：`requestId` 负责一次 HTTP 请求，`taskId/attemptKey` 负责可跨请求恢复的长业务任务。请求 ID 不能替代幂等键，幂等键也不能替代日志关联。
