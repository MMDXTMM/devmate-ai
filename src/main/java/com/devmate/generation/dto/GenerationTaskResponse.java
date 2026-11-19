package com.devmate.generation.dto;
import java.time.LocalDateTime;
public record GenerationTaskResponse(String id,String kind,String referenceId,String status,String phase,
        int modelCalls,Long inputTokens,Long outputTokens,boolean usageComplete,long elapsedMs,
        LocalDateTime createdAt,LocalDateTime startedAt,LocalDateTime finishedAt) { }
