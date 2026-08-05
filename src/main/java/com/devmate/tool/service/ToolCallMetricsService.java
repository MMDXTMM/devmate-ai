package com.devmate.tool.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.tool.entity.ToolCallLog;
import com.devmate.tool.mapper.ToolCallLogMapper;
import com.devmate.tool.model.ToolCallMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolCallMetricsService {

    private final ToolCallLogMapper mapper;

    public ToolCallMetricsService(ToolCallLogMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ToolCallMetrics metrics(Long invocationId, Long projectId) {
        long total = mapper.selectCount(Wrappers.lambdaQuery(ToolCallLog.class)
                .eq(ToolCallLog::getInvocationId, invocationId)
                .eq(ToolCallLog::getProjectId, projectId));
        long succeeded = mapper.selectCount(Wrappers.lambdaQuery(ToolCallLog.class)
                .eq(ToolCallLog::getInvocationId, invocationId)
                .eq(ToolCallLog::getProjectId, projectId)
                .eq(ToolCallLog::getStatus, "SUCCEEDED"));
        return new ToolCallMetrics(Math.toIntExact(total), Math.toIntExact(succeeded));
    }
}
