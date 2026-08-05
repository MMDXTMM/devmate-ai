package com.devmate.review.model;

import com.devmate.review.dto.ReviewEvaluationItemResultResponse;

import java.math.BigDecimal;
import java.util.List;

public record ReviewEvaluationCalculation(
        int expectedDefects,
        int predictedFindings,
        int truePositives,
        int falsePositives,
        int falseNegatives,
        int manualReviewCount,
        boolean partialMetrics,
        BigDecimal precision,
        BigDecimal recall,
        BigDecimal f1,
        List<ReviewEvaluationItemResultResponse> results
) {
    public ReviewEvaluationCalculation {
        results = List.copyOf(results);
    }
}
