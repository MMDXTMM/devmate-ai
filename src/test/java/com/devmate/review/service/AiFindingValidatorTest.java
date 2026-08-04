package com.devmate.review.service;

import com.devmate.agent.config.AiReviewProperties;
import com.devmate.agent.model.AiReviewFinding;
import com.devmate.knowledge.dto.RetrievalHitResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiFindingValidatorTest {

    private final AiReviewProperties properties = new AiReviewProperties();
    private final AiFindingValidator validator = new AiFindingValidator(properties);

    @Test
    void mapsOnlyServerProvidedChunkAndKeepsServerLocation() {
        var result = validator.validate(
                List.of(finding("42", "HIGH", "INFERENCE", 0.8)),
                List.of(hit(42L))
        );

        assertThat(result.rejectedCount()).isZero();
        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().getFirst().evidenceChunk().filePath())
                .isEqualTo("src/main/java/OrderService.java");
        assertThat(result.findings().getFirst().evidenceChunk().startLine()).isEqualTo(21);
    }

    @Test
    void rejectsInventedChunkAndInvalidEnum() {
        var result = validator.validate(
                List.of(
                        finding("999", "HIGH", "INFERENCE", 0.8),
                        new AiReviewFinding(
                                "42", "UNKNOWN", "HIGH", "INFERENCE", 0.8,
                                "问题", "证据", "场景", "建议", "验证"
                        )
                ),
                List.of(hit(42L))
        );

        assertThat(result.findings()).isEmpty();
        assertThat(result.rejectedCount()).isEqualTo(2);
    }

    @Test
    void capsUnverifiedFindingSeverityAndConfidence() {
        var result = validator.validate(
                List.of(finding("42", "CRITICAL", "NEEDS_VERIFICATION", 0.99)),
                List.of(hit(42L))
        );

        assertThat(result.findings().getFirst().severity().name()).isEqualTo("MEDIUM");
        assertThat(result.findings().getFirst().confidence()).isEqualByComparingTo("0.8500");
    }

    @Test
    void rejectsDuplicateModelFindingsBeforeDatabasePersistence() {
        AiReviewFinding duplicate = finding("42", "HIGH", "INFERENCE", 0.8);

        var result = validator.validate(List.of(duplicate, duplicate), List.of(hit(42L)));

        assertThat(result.findings()).hasSize(1);
        assertThat(result.rejectedCount()).isEqualTo(1);
    }

    private AiReviewFinding finding(
            String chunkId,
            String severity,
            String conclusionType,
            double confidence
    ) {
        return new AiReviewFinding(
                chunkId, "CONCURRENCY", severity, conclusionType, confidence,
                "检查后执行不是原子操作", "读取库存后再写入", "两个请求同时通过检查",
                "使用条件更新", "并发压测并校验最终库存"
        );
    }

    private RetrievalHitResponse hit(Long chunkId) {
        return new RetrievalHitResponse(
                chunkId, 7L, "src/main/java/OrderService.java", "SOURCE_CODE", "METHOD",
                "OrderService.reserve", 21, 35, 0.91, 120,
                List.of("DIFF_SEED", "VECTOR_MATCH"), "void reserve() {}"
        );
    }
}
