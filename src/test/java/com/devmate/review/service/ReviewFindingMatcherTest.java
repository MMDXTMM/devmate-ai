package com.devmate.review.service;

import com.devmate.review.entity.ReviewEvaluationCase;
import com.devmate.review.entity.ReviewFinding;
import com.devmate.review.model.ReviewEvaluationCalculation;
import com.devmate.review.model.ReviewEvaluationOutcome;
import com.devmate.review.model.ReviewExpectationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewFindingMatcherTest {

    private final ReviewFindingMatcher matcher = new ReviewFindingMatcher();

    @Test
    void calculatesTruePositiveFalsePositiveAndFalseNegative() {
        ReviewEvaluationCalculation result = matcher.calculate(
                List.of(
                        defect(1L, "CONCURRENCY", "src/OrderService.java", 20, 30),
                        defect(2L, "SQL", "src/OrderMapper.java", 40, 45)
                ),
                List.of(
                        finding(11L, "CONCURRENCY", "src/OrderService.java", 24, 26),
                        finding(12L, "SECURITY", "src/AuthService.java", 8, 8)
                )
        );

        assertThat(result.truePositives()).isEqualTo(1);
        assertThat(result.falsePositives()).isEqualTo(1);
        assertThat(result.falseNegatives()).isEqualTo(1);
        assertThat(result.precision()).isEqualByComparingTo(new BigDecimal("0.500000"));
        assertThat(result.recall()).isEqualByComparingTo(new BigDecimal("0.500000"));
        assertThat(result.f1()).isEqualByComparingTo(new BigDecimal("0.500000"));
        assertThat(result.partialMetrics()).isFalse();
    }

    @Test
    void sendsMultipleCandidatesToManualReviewWithoutInventingMatch() {
        ReviewEvaluationCalculation result = matcher.calculate(
                List.of(defect(1L, "CONCURRENCY", "src/OrderService.java", 20, 30)),
                List.of(
                        finding(11L, "CONCURRENCY", "src/OrderService.java", 22, 23),
                        finding(12L, "CONCURRENCY", "src/OrderService.java", 25, 26)
                )
        );

        assertThat(result.truePositives()).isZero();
        assertThat(result.falsePositives()).isZero();
        assertThat(result.falseNegatives()).isZero();
        assertThat(result.manualReviewCount()).isEqualTo(3);
        assertThat(result.partialMetrics()).isTrue();
        assertThat(result.results()).allMatch(value ->
                ReviewEvaluationOutcome.MANUAL_REVIEW.name().equals(value.outcome()));
    }

    @Test
    void treatsCleanCaseWithoutFindingsAsCompletePass() {
        ReviewEvaluationCalculation result = matcher.calculate(
                List.of(clean(1L)),
                List.of()
        );

        assertThat(result.expectedDefects()).isZero();
        assertThat(result.predictedFindings()).isZero();
        assertThat(result.precision()).isEqualByComparingTo("1.000000");
        assertThat(result.recall()).isEqualByComparingTo("1.000000");
        assertThat(result.f1()).isEqualByComparingTo("1.000000");
        assertThat(result.results()).singleElement().satisfies(value ->
                assertThat(value.outcome()).isEqualTo(ReviewEvaluationOutcome.CLEAN_PASS.name()));
    }

    private ReviewEvaluationCase defect(
            Long id,
            String category,
            String filePath,
            int startLine,
            int endLine
    ) {
        ReviewEvaluationCase value = new ReviewEvaluationCase();
        value.setId(id);
        value.setExpectationType(ReviewExpectationType.DEFECT.name());
        value.setCategory(category);
        value.setFilePath(filePath);
        value.setStartLine(startLine);
        value.setEndLine(endLine);
        return value;
    }

    private ReviewEvaluationCase clean(Long id) {
        ReviewEvaluationCase value = new ReviewEvaluationCase();
        value.setId(id);
        value.setExpectationType(ReviewExpectationType.CLEAN.name());
        return value;
    }

    private ReviewFinding finding(
            Long id,
            String category,
            String filePath,
            int startLine,
            int endLine
    ) {
        ReviewFinding value = new ReviewFinding();
        value.setId(id);
        value.setCategory(category);
        value.setFilePath(filePath);
        value.setStartLine(startLine);
        value.setEndLine(endLine);
        return value;
    }
}
