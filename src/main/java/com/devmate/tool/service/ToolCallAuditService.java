package com.devmate.tool.service;

import com.devmate.tool.entity.ToolCallLog;
import com.devmate.tool.mapper.ToolCallLogMapper;
import com.devmate.tool.model.AgentToolExecutionException;
import com.devmate.tool.model.ReviewAgentContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ToolCallAuditService {

    private final ToolCallLogMapper mapper;

    public ToolCallAuditService(ToolCallLogMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public Long start(
            ReviewAgentContext context,
            String toolCallId,
            int stepNo,
            String toolName,
            String argumentsHash,
            String argumentsSummary
    ) {
        ToolCallLog log = new ToolCallLog();
        log.setInvocationId(context.invocationId());
        log.setProjectId(context.projectId());
        log.setToolCallId(truncate(toolCallId, 128));
        log.setStepNo(stepNo);
        log.setToolName(truncate(toolName, 100));
        log.setArgumentsHash(argumentsHash);
        log.setArgumentsSummary(truncate(argumentsSummary, 2000));
        log.setStatus("RUNNING");
        log.setLatencyMs(0L);
        log.setCreatedAt(LocalDateTime.now());
        try {
            mapper.insert(log);
        } catch (DataIntegrityViolationException exception) {
            throw new AgentToolExecutionException("模型重复使用工具调用ID", exception);
        }
        return log.getId();
    }

    @Transactional
    public void succeed(Long id, String resultSummary, long latencyMs) {
        ToolCallLog log = requireLog(id);
        log.setStatus("SUCCEEDED");
        log.setResultSummary(truncate(resultSummary, 4000));
        log.setLatencyMs(latencyMs);
        mapper.updateById(log);
    }

    @Transactional
    public void fail(Long id, String errorCode, String errorMessage, long latencyMs) {
        ToolCallLog log = requireLog(id);
        log.setStatus("FAILED");
        log.setErrorCode(truncate(errorCode, 64));
        log.setErrorMessage(truncate(errorMessage, 1000));
        log.setLatencyMs(latencyMs);
        mapper.updateById(log);
    }

    private ToolCallLog requireLog(Long id) {
        ToolCallLog log = mapper.selectById(id);
        if (log == null) {
            throw new IllegalStateException("工具调用日志不存在");
        }
        return log;
    }

    private String truncate(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
