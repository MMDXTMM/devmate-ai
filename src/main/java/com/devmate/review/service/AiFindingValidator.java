package com.devmate.review.service;

import com.devmate.agent.config.AiReviewProperties;
import com.devmate.agent.model.AiConclusionType;
import com.devmate.agent.model.AiFindingCategory;
import com.devmate.agent.model.AiFindingSeverity;
import com.devmate.agent.model.AiReviewFinding;
import com.devmate.knowledge.dto.RetrievalHitResponse;
import com.devmate.review.model.AiFindingValidationResult;
import com.devmate.review.model.ValidatedAiFinding;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AiFindingValidator {

    private final AiReviewProperties properties;

    public AiFindingValidator(AiReviewProperties properties) {
        this.properties = properties;
    }

    public AiFindingValidationResult validate(
            List<AiReviewFinding> candidates,
            List<RetrievalHitResponse> evidence
    ) {
        Map<Long, RetrievalHitResponse> evidenceById = evidence.stream()
                .collect(Collectors.toMap(RetrievalHitResponse::chunkId, hit -> hit));
        List<ValidatedAiFinding> accepted = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        int rejected = 0;
        for (AiReviewFinding candidate : candidates) {
            if (accepted.size() >= properties.getMaxFindings()) {
                rejected++;
                continue;
            }
            ValidatedAiFinding validated = validateOne(candidate, evidenceById);
            if (validated == null) {
                rejected++;
            } else if (!fingerprints.add(validationKey(validated))) {
                rejected++;
            } else {
                accepted.add(validated);
            }
        }
        return new AiFindingValidationResult(accepted, rejected);
    }

    private ValidatedAiFinding validateOne(
            AiReviewFinding candidate,
            Map<Long, RetrievalHitResponse> evidenceById
    ) {
        if (candidate == null || !requiredText(candidate.chunkId(), 30)
                || !requiredText(candidate.title(), 1000)
                || !requiredText(candidate.evidence(), 5000)
                || !requiredText(candidate.riskScenario(), 5000)
                || !requiredText(candidate.suggestion(), 5000)
                || !requiredText(candidate.verification(), 5000)
                || candidate.confidence() == null
                || !Double.isFinite(candidate.confidence())
                || candidate.confidence() < 0.0
                || candidate.confidence() > 1.0) {
            return null;
        }
        try {
            long chunkId = Long.parseLong(candidate.chunkId());
            RetrievalHitResponse hit = evidenceById.get(chunkId);
            if (hit == null || !StringUtils.hasText(hit.filePath())) {
                return null;
            }
            AiFindingCategory category = AiFindingCategory.valueOf(normalize(candidate.category()));
            AiFindingSeverity severity = AiFindingSeverity.valueOf(normalize(candidate.severity()));
            AiConclusionType conclusionType = AiConclusionType.valueOf(normalize(candidate.conclusionType()));
            double confidence = candidate.confidence();
            if (conclusionType == AiConclusionType.NEEDS_VERIFICATION) {
                if (severity.ordinal() > AiFindingSeverity.MEDIUM.ordinal()) {
                    severity = AiFindingSeverity.MEDIUM;
                }
                confidence = Math.min(confidence, 0.85);
            }
            return new ValidatedAiFinding(
                    hit,
                    category,
                    severity,
                    conclusionType,
                    BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP),
                    candidate.title().trim(),
                    candidate.evidence().trim(),
                    candidate.riskScenario().trim(),
                    candidate.suggestion().trim(),
                    candidate.verification().trim()
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("枚举值为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean requiredText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.trim().length() <= maxLength;
    }

    private String validationKey(ValidatedAiFinding finding) {
        return finding.evidenceChunk().chunkId() + "\n" + finding.category() + "\n" + finding.title();
    }
}
