package com.devmate.review.service;

import com.devmate.review.dto.ReviewEvaluationItemResultResponse;
import com.devmate.review.entity.ReviewEvaluationCase;
import com.devmate.review.entity.ReviewFinding;
import com.devmate.review.model.ReviewEvaluationCalculation;
import com.devmate.review.model.ReviewEvaluationOutcome;
import com.devmate.review.model.ReviewExpectationType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ReviewFindingMatcher {

    public ReviewEvaluationCalculation calculate(
            List<ReviewEvaluationCase> cases,
            List<ReviewFinding> findings
    ) {
        List<ReviewEvaluationCase> expectedDefects = cases.stream()
                .filter(value -> ReviewExpectationType.DEFECT.name().equals(value.getExpectationType()))
                .toList();
        ReviewEvaluationCase cleanCase = cases.stream()
                .filter(value -> ReviewExpectationType.CLEAN.name().equals(value.getExpectationType()))
                .findFirst()
                .orElse(null);

        Map<Long, List<ReviewFinding>> findingsByCase = new HashMap<>();
        Map<Long, List<ReviewEvaluationCase>> casesByFinding = new HashMap<>();
        for (ReviewEvaluationCase expected : expectedDefects) {
            List<ReviewFinding> candidates = findings.stream()
                    .filter(finding -> matches(expected, finding))
                    .toList();
            findingsByCase.put(expected.getId(), candidates);
            for (ReviewFinding finding : candidates) {
                casesByFinding.computeIfAbsent(finding.getId(), ignored -> new ArrayList<>())
                        .add(expected);
            }
        }

        int truePositives = 0;
        int falseNegatives = 0;
        int falsePositives = 0;
        List<ReviewEvaluationItemResultResponse> results = new ArrayList<>();
        Set<Long> matchedFindingIds = new HashSet<>();
        Set<Long> manualFindingIds = new HashSet<>();

        for (ReviewEvaluationCase expected : expectedDefects) {
            List<ReviewFinding> candidates = findingsByCase.getOrDefault(expected.getId(), List.of());
            if (candidates.isEmpty()) {
                falseNegatives++;
                results.add(result(
                        expected.getId(), null, ReviewEvaluationOutcome.FALSE_NEGATIVE,
                        "没有类别、路径和行范围都匹配的Finding"
                ));
                continue;
            }
            ReviewFinding onlyCandidate = candidates.size() == 1 ? candidates.getFirst() : null;
            if (onlyCandidate != null
                    && casesByFinding.getOrDefault(onlyCandidate.getId(), List.of()).size() == 1) {
                truePositives++;
                matchedFindingIds.add(onlyCandidate.getId());
                results.add(result(
                        expected.getId(), onlyCandidate.getId(), ReviewEvaluationOutcome.TRUE_POSITIVE,
                        "类别、文件路径和行范围唯一匹配"
                ));
                continue;
            }
            candidates.forEach(candidate -> manualFindingIds.add(candidate.getId()));
            results.add(result(
                    expected.getId(), null, ReviewEvaluationOutcome.MANUAL_REVIEW,
                    "存在多个候选关系，需要人工确认标准缺陷与Finding的对应关系"
            ));
        }

        for (ReviewFinding finding : findings) {
            if (matchedFindingIds.contains(finding.getId())) {
                continue;
            }
            List<ReviewEvaluationCase> candidates = casesByFinding.getOrDefault(finding.getId(), List.of());
            if (manualFindingIds.contains(finding.getId()) || !candidates.isEmpty()) {
                results.add(result(
                        null, finding.getId(), ReviewEvaluationOutcome.MANUAL_REVIEW,
                        "Finding与一个或多个标准缺陷存在歧义候选"
                ));
                continue;
            }
            falsePositives++;
            results.add(result(
                    null, finding.getId(), ReviewEvaluationOutcome.FALSE_POSITIVE,
                    "没有类别、路径和行范围都匹配的标准缺陷"
            ));
        }

        if (cleanCase != null && findings.isEmpty()) {
            results.add(result(
                    cleanCase.getId(), null, ReviewEvaluationOutcome.CLEAN_PASS,
                    "无缺陷对照未产生Finding"
            ));
        }

        int manualReviewCount = (int) results.stream()
                .filter(value -> ReviewEvaluationOutcome.MANUAL_REVIEW.name().equals(value.outcome()))
                .count();
        BigDecimal precision = ratio(truePositives, truePositives + falsePositives);
        BigDecimal recall = ratio(truePositives, truePositives + falseNegatives);
        BigDecimal f1 = f1(precision, recall);
        return new ReviewEvaluationCalculation(
                expectedDefects.size(), findings.size(), truePositives, falsePositives,
                falseNegatives, manualReviewCount, manualReviewCount > 0,
                precision, recall, f1, results
        );
    }

    private boolean matches(ReviewEvaluationCase expected, ReviewFinding finding) {
        return expected.getCategory().equals(finding.getCategory())
                && expected.getFilePath().equals(normalizePath(finding.getFilePath()))
                && expected.getStartLine() <= finding.getEndLine()
                && finding.getStartLine() <= expected.getEndLine();
    }

    private String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    private ReviewEvaluationItemResultResponse result(
            Long expectedCaseId,
            Long findingId,
            ReviewEvaluationOutcome outcome,
            String reason
    ) {
        return new ReviewEvaluationItemResultResponse(
                expectedCaseId, findingId, outcome.name(), reason
        );
    }

    private BigDecimal ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal f1(BigDecimal precision, BigDecimal recall) {
        if (precision.signum() == 0 || recall.signum() == 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return precision.multiply(recall).multiply(BigDecimal.TWO)
                .divide(precision.add(recall), 6, RoundingMode.HALF_UP);
    }
}
