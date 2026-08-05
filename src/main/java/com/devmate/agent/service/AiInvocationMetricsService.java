package com.devmate.agent.service;

import com.devmate.agent.entity.AiInvocationLog;
import com.devmate.agent.mapper.AiInvocationLogMapper;
import com.devmate.agent.model.AiInvocationMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiInvocationMetricsService {

    private final AiInvocationLogMapper mapper;

    public AiInvocationMetricsService(AiInvocationLogMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public AiInvocationMetrics requireMetrics(Long invocationId, Long projectId) {
        AiInvocationLog log = mapper.selectById(invocationId);
        if (log == null || !projectId.equals(log.getProjectId())) {
            throw new IllegalStateException("AI调用审计不存在或项目不匹配");
        }
        return new AiInvocationMetrics(
                log.getTotalTokens() == null ? 0 : log.getTotalTokens(),
                log.getLatencyMs() == null ? 0L : log.getLatencyMs()
        );
    }
}
