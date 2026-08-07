package com.devmate.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.service.SourceStructureUsageChecker;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.mapper.CodeReviewTaskMapper;
import org.springframework.stereotype.Component;

@Component
public class ReviewSourceStructureUsageChecker implements SourceStructureUsageChecker {

    private final CodeReviewTaskMapper taskMapper;

    public ReviewSourceStructureUsageChecker(CodeReviewTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public void assertImportAllowed(Long projectId) {
        long runningReviews = taskMapper.selectCount(Wrappers.lambdaQuery(CodeReviewTask.class)
                .eq(CodeReviewTask::getProjectId, projectId)
                .eq(CodeReviewTask::getStatus, "RUNNING"));
        if (runningReviews > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目正在生成Diff，暂不能导入源码");
        }
    }

    @Override
    public void assertRebuildAllowed(Long projectId, String revision) {
        long reviews = taskMapper.selectCount(Wrappers.lambdaQuery(CodeReviewTask.class)
                .eq(CodeReviewTask::getProjectId, projectId)
                .eq(CodeReviewTask::getTargetRevision, revision));
        if (reviews > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前源码结构已被Diff或审查记录引用，不能原地重建");
        }
    }
}
