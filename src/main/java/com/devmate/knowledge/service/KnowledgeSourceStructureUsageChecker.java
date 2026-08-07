package com.devmate.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.entity.EmbeddingIndexTask;
import com.devmate.knowledge.entity.EmbeddingVector;
import com.devmate.knowledge.entity.RetrievalEvaluationRun;
import com.devmate.knowledge.mapper.EmbeddingIndexTaskMapper;
import com.devmate.knowledge.mapper.EmbeddingVectorMapper;
import com.devmate.knowledge.mapper.RetrievalEvaluationRunMapper;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeSourceStructureUsageChecker implements SourceStructureUsageChecker {

    private final EmbeddingIndexTaskMapper embeddingTaskMapper;
    private final EmbeddingVectorMapper vectorMapper;
    private final RetrievalEvaluationRunMapper evaluationRunMapper;

    public KnowledgeSourceStructureUsageChecker(
            EmbeddingIndexTaskMapper embeddingTaskMapper,
            EmbeddingVectorMapper vectorMapper,
            RetrievalEvaluationRunMapper evaluationRunMapper
    ) {
        this.embeddingTaskMapper = embeddingTaskMapper;
        this.vectorMapper = vectorMapper;
        this.evaluationRunMapper = evaluationRunMapper;
    }

    @Override
    public void assertImportAllowed(Long projectId) {
        long runningEmbeddings = embeddingTaskMapper.selectCount(
                Wrappers.lambdaQuery(EmbeddingIndexTask.class)
                        .eq(EmbeddingIndexTask::getProjectId, projectId)
                        .eq(EmbeddingIndexTask::getStatus, "RUNNING")
        );
        if (runningEmbeddings > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目正在建立向量索引，暂不能导入源码");
        }
    }

    @Override
    public void assertRebuildAllowed(Long projectId, String revision) {
        long vectors = vectorMapper.selectCount(Wrappers.lambdaQuery(EmbeddingVector.class)
                .eq(EmbeddingVector::getProjectId, projectId)
                .eq(EmbeddingVector::getRevision, revision));
        if (vectors > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前源码结构已建立向量索引，不能原地重建");
        }
        long evaluations = evaluationRunMapper.selectCount(
                Wrappers.lambdaQuery(RetrievalEvaluationRun.class)
                        .eq(RetrievalEvaluationRun::getProjectId, projectId)
                        .eq(RetrievalEvaluationRun::getRevision, revision)
        );
        if (evaluations > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前源码结构已有检索评测记录，不能原地重建");
        }
    }
}
