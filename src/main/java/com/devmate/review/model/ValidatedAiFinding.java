package com.devmate.review.model;

import com.devmate.agent.model.AiConclusionType;
import com.devmate.agent.model.AiFindingCategory;
import com.devmate.agent.model.AiFindingSeverity;
import com.devmate.knowledge.dto.RetrievalHitResponse;

import java.math.BigDecimal;

public record ValidatedAiFinding(
        RetrievalHitResponse evidenceChunk,
        AiFindingCategory category,
        AiFindingSeverity severity,
        AiConclusionType conclusionType,
        BigDecimal confidence,
        String title,
        String evidence,
        String riskScenario,
        String suggestion,
        String verification
) {
}
