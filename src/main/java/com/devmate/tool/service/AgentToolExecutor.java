package com.devmate.tool.service;

import com.devmate.agent.config.ReviewAgentProperties;
import com.devmate.agent.model.ReviewAgentToolCall;
import com.devmate.common.error.BusinessException;
import com.devmate.tool.AgentTool;
import com.devmate.tool.AgentToolRegistry;
import com.devmate.tool.model.AgentToolExecutionException;
import com.devmate.tool.model.AgentToolResult;
import com.devmate.tool.model.ReviewAgentContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AgentToolExecutor {

    private final AgentToolRegistry registry;
    private final ToolCallAuditService auditService;
    private final ReviewAgentProperties properties;
    private final ObjectMapper objectMapper;

    public AgentToolExecutor(
            AgentToolRegistry registry,
            ToolCallAuditService auditService,
            ReviewAgentProperties properties,
            ObjectMapper objectMapper
    ) {
        this.registry = registry;
        this.auditService = auditService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AgentToolResult execute(
            ReviewAgentContext context,
            ReviewAgentToolCall call,
            int stepNo
    ) {
        validateCall(call);
        String toolName = call.function().name();
        String arguments = normalizeArguments(call.function().arguments());
        String argumentsHash = sha256(arguments);
        Long auditId = auditService.start(
                context,
                call.id(),
                stepNo,
                toolName,
                argumentsHash,
                argumentsSummary(arguments)
        );
        long startedAt = System.nanoTime();
        try {
            AgentTool tool = registry.find(toolName).orElse(null);
            if (tool == null) {
                return fail(auditId, "UNKNOWN_TOOL", "Agent请求了未授权工具", startedAt);
            }
            JsonNode argumentsNode = objectMapper.readTree(arguments);
            if (!argumentsNode.isObject()) {
                return fail(auditId, "INVALID_ARGUMENTS", "工具参数必须是JSON对象", startedAt);
            }
            AgentToolResult result = runWithTimeout(tool, context, argumentsNode);
            if (!result.succeeded()) {
                return fail(auditId, "TOOL_FAILED", result.resultSummary(), startedAt);
            }
            if (result.content() == null
                    || result.content().length() > properties.getMaxToolOutputCharacters()) {
                return fail(auditId, "OUTPUT_LIMIT", "工具输出超过允许范围", startedAt);
            }
            auditService.succeed(auditId, result.resultSummary(), elapsedMillis(startedAt));
            return result;
        } catch (JsonProcessingException exception) {
            return fail(auditId, "INVALID_ARGUMENTS", "工具参数不是合法JSON", startedAt);
        } catch (AgentToolExecutionException exception) {
            return fail(auditId, "TOOL_EXECUTION", safeMessage(exception), startedAt);
        } catch (RuntimeException exception) {
            return fail(auditId, exception.getClass().getSimpleName(), safeMessage(exception), startedAt);
        }
    }

    public String signature(ReviewAgentToolCall call) {
        validateCall(call);
        return call.function().name() + ":" + sha256(normalizeArguments(call.function().arguments()));
    }

    private AgentToolResult runWithTimeout(
            AgentTool tool,
            ReviewAgentContext context,
            JsonNode arguments
    ) {
        FutureTask<AgentToolResult> task = new FutureTask<>(() -> tool.execute(context, arguments));
        Thread worker = Thread.ofVirtual().name("devmate-agent-tool").start(task);
        try {
            return task.get(properties.getToolTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            worker.interrupt();
            throw new AgentToolExecutionException("工具执行超时", exception);
        } catch (InterruptedException exception) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new AgentToolExecutionException("工具执行被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AgentToolExecutionException("工具执行失败", cause);
        }
    }

    private AgentToolResult fail(Long auditId, String code, String message, long startedAt) {
        String safe = StringUtils.hasText(message) ? message : "工具执行失败";
        auditService.fail(auditId, code, safe, elapsedMillis(startedAt));
        return AgentToolResult.failure(
                "{\"status\":\"FAILED\",\"message\":\"" + escapeJson(safe) + "\"}",
                safe
        );
    }

    private void validateCall(ReviewAgentToolCall call) {
        if (call == null || !StringUtils.hasText(call.id()) || call.id().length() > 128
                || call.function() == null || !StringUtils.hasText(call.function().name())
                || call.function().name().length() > 100) {
            throw new AgentToolExecutionException("模型返回的工具调用格式无效");
        }
    }

    private String normalizeArguments(String arguments) {
        return StringUtils.hasText(arguments) ? arguments.trim() : "{}";
    }

    private String argumentsSummary(String arguments) {
        try {
            JsonNode node = objectMapper.readTree(arguments);
            if (!node.isObject()) {
                return "jsonType=" + node.getNodeType();
            }
            StringBuilder keys = new StringBuilder();
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                if (!keys.isEmpty()) {
                    keys.append(',');
                }
                keys.append(fields.next());
            }
            return "keys=" + keys + ";characters=" + arguments.length();
        } catch (JsonProcessingException exception) {
            return "invalidJson;characters=" + arguments.length();
        }
    }

    private String safeMessage(RuntimeException exception) {
        if (exception instanceof BusinessException || exception instanceof AgentToolExecutionException) {
            return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "工具执行失败";
        }
        return "工具执行失败";
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
