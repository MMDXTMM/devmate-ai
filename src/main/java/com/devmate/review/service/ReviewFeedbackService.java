package com.devmate.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.review.dto.ReviewFeedbackResponse;
import com.devmate.review.dto.UpsertReviewFeedbackRequest;
import com.devmate.review.entity.CodeReviewFeedback;
import com.devmate.review.entity.ReviewFinding;
import com.devmate.review.mapper.CodeReviewFeedbackMapper;
import com.devmate.review.mapper.ReviewFindingMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class ReviewFeedbackService {

    private final ProjectMapper projectMapper;
    private final ReviewFindingMapper findingMapper;
    private final CodeReviewFeedbackMapper feedbackMapper;

    public ReviewFeedbackService(
            ProjectMapper projectMapper,
            ReviewFindingMapper findingMapper,
            CodeReviewFeedbackMapper feedbackMapper
    ) {
        this.projectMapper = projectMapper;
        this.findingMapper = findingMapper;
        this.feedbackMapper = feedbackMapper;
    }

    @Transactional
    public ReviewFeedbackResponse upsert(
            Long projectId,
            Long findingId,
            UpsertReviewFeedbackRequest request
    ) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        ReviewFinding finding = findingMapper.selectOne(
                Wrappers.lambdaQuery(ReviewFinding.class)
                        .eq(ReviewFinding::getId, findingId)
                        .eq(ReviewFinding::getProjectId, projectId)
                        .last("LIMIT 1")
        );
        if (finding == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "审查结论不存在");
        }

        String comment = normalizeComment(request.comment());
        LocalDateTime now = LocalDateTime.now();
        int updated = updateExisting(findingId, request.feedbackType().name(), comment, now);
        if (updated == 0) {
            CodeReviewFeedback feedback = new CodeReviewFeedback();
            feedback.setProjectId(projectId);
            feedback.setFindingId(findingId);
            feedback.setFeedbackType(request.feedbackType().name());
            feedback.setComment(comment);
            feedback.setCreatedAt(now);
            feedback.setUpdatedAt(now);
            try {
                feedbackMapper.insert(feedback);
            } catch (DuplicateKeyException exception) {
                updateExisting(findingId, request.feedbackType().name(), comment, now);
            }
        }
        return ReviewFeedbackResponse.from(requireFeedback(findingId));
    }

    private int updateExisting(Long findingId, String feedbackType, String comment, LocalDateTime now) {
        return feedbackMapper.update(
                null,
                Wrappers.lambdaUpdate(CodeReviewFeedback.class)
                        .eq(CodeReviewFeedback::getFindingId, findingId)
                        .set(CodeReviewFeedback::getFeedbackType, feedbackType)
                        .set(CodeReviewFeedback::getComment, comment)
                        .set(CodeReviewFeedback::getUpdatedAt, now)
        );
    }

    private CodeReviewFeedback requireFeedback(Long findingId) {
        CodeReviewFeedback feedback = feedbackMapper.selectOne(
                Wrappers.lambdaQuery(CodeReviewFeedback.class)
                        .eq(CodeReviewFeedback::getFindingId, findingId)
                        .last("LIMIT 1")
        );
        if (feedback == null) {
            throw new IllegalStateException("审查反馈保存后不存在");
        }
        return feedback;
    }

    private String normalizeComment(String comment) {
        return StringUtils.hasText(comment) ? comment.trim() : null;
    }
}
