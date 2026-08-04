package com.devmate.review.source;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.knowledge.entity.CodeReference;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.CodeReferenceMapper;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.review.model.LineRange;
import com.devmate.review.model.StaticAnalysisResult;
import com.devmate.review.model.StaticAnalysisTarget;
import com.devmate.review.model.StaticFinding;
import com.devmate.review.service.StaticAnalysisContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProjectRuleAnalyzer {

    public static final String TOOL_NAME = "DEVMATE_PROJECT_RULES";
    public static final String TOOL_VERSION = "1.0";

    private final CodeReferenceMapper referenceMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    public ProjectRuleAnalyzer(
            CodeReferenceMapper referenceMapper,
            KnowledgeChunkMapper chunkMapper,
            KnowledgeDocumentMapper documentMapper,
            ObjectMapper objectMapper
    ) {
        this.referenceMapper = referenceMapper;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.objectMapper = objectMapper;
    }

    public StaticAnalysisResult analyze(
            StaticAnalysisContext context,
            List<StaticAnalysisTarget> targets
    ) {
        Map<String, StaticAnalysisTarget> targetsByPath = targets.stream()
                .collect(Collectors.toMap(StaticAnalysisTarget::relativePath, Function.identity()));
        List<CodeReference> references = referenceMapper.selectList(
                Wrappers.lambdaQuery(CodeReference.class)
                        .eq(CodeReference::getProjectId, context.projectId())
                        .eq(CodeReference::getRevision, context.targetRevision())
                        .in(CodeReference::getReferenceKind, "METHOD_CALL", "DATA_ACCESS")
        );
        if (references.isEmpty()) {
            return new StaticAnalysisResult(TOOL_NAME, TOOL_VERSION, targets.size(), List.of());
        }

        Set<Long> chunkIds = references.stream()
                .flatMap(reference -> java.util.stream.Stream.of(
                        reference.getSourceChunkId(), reference.getTargetChunkId()
                ))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, KnowledgeChunk> chunks = chunkMapper.selectBatchIds(chunkIds).stream()
                .collect(Collectors.toMap(KnowledgeChunk::getId, Function.identity()));
        Set<Long> documentIds = chunks.values().stream()
                .map(KnowledgeChunk::getDocumentId)
                .collect(Collectors.toSet());
        Map<Long, KnowledgeDocument> documents = documentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));

        List<StaticFinding> findings = new ArrayList<>();
        for (CodeReference reference : references) {
            KnowledgeChunk source = chunks.get(reference.getSourceChunkId());
            if (source == null) {
                continue;
            }
            KnowledgeDocument document = documents.get(source.getDocumentId());
            if (document == null) {
                continue;
            }
            StaticAnalysisTarget target = targetsByPath.get(document.getFilePath());
            if (target == null || !intersects(reference, target.changedLines())) {
                continue;
            }
            if ("METHOD_CALL".equals(reference.getReferenceKind())) {
                addTransactionalSelfInvocation(reference, source, chunks, document, findings);
            } else if ("DATA_ACCESS".equals(reference.getReferenceKind())) {
                addDataAccessContextRules(reference, source, document, findings);
            }
        }
        return new StaticAnalysisResult(TOOL_NAME, TOOL_VERSION, targets.size(), findings);
    }

    private void addTransactionalSelfInvocation(
            CodeReference reference,
            KnowledgeChunk source,
            Map<Long, KnowledgeChunk> chunks,
            KnowledgeDocument document,
            List<StaticFinding> findings
    ) {
        KnowledgeChunk target = chunks.get(reference.getTargetChunkId());
        if (target == null || target.getId().equals(source.getId()) || !hasAnnotation(target, "Transactional")) {
            return;
        }
        findings.add(new StaticFinding(
                "TransactionalSelfInvocation",
                "TRANSACTION",
                "HIGH",
                document.getFilePath(),
                reference.getStartLine(),
                reference.getEndLine(),
                "当前类内部调用带@Transactional的方法，默认代理模式下事务增强可能不会生效",
                "调用方 " + source.getSymbolName() + " 在第" + reference.getStartLine()
                        + "行调用同类目标 " + target.getSymbolName() + "，目标方法声明@Transactional；"
                        + "请结合Spring事务代理模式验证"
        ));
    }

    private void addDataAccessContextRules(
            CodeReference reference,
            KnowledgeChunk source,
            KnowledgeDocument document,
            List<StaticFinding> findings
    ) {
        ReferenceMetadata metadata = readReferenceMetadata(reference.getMetadataJson());
        String call = (reference.getQualifier() == null ? "" : reference.getQualifier() + ".")
                + reference.getReferenceName();
        if (metadata.loopDepth() > 0) {
            findings.add(new StaticFinding(
                    "DataAccessInsideLoop",
                    "PERFORMANCE",
                    "MEDIUM",
                    document.getFilePath(),
                    reference.getStartLine(),
                    reference.getEndLine(),
                    "循环体内存在数据访问，数据量增长时可能形成N+1查询或请求放大",
                    source.getSymbolName() + " 的循环体内调用 " + call
                            + "；该调用按命名约定识别为数据访问，请验证批量查询或预加载方案"
            ));
        }
        if (metadata.synchronizedDepth() > 0) {
            findings.add(new StaticFinding(
                    "BlockingDataAccessUnderLock",
                    "CONCURRENCY",
                    "HIGH",
                    document.getFilePath(),
                    reference.getStartLine(),
                    reference.getEndLine(),
                    "同步锁范围内执行数据访问，慢IO可能延长持锁时间并降低并发吞吐",
                    source.getSymbolName() + " 在同步上下文内调用 " + call
                            + "；请通过压测和数据库延迟指标验证锁竞争"
            ));
        }
    }

    private boolean hasAnnotation(KnowledgeChunk chunk, String simpleName) {
        ChunkMetadata metadata;
        try {
            metadata = objectMapper.readValue(chunk.getMetadataJson(), ChunkMetadata.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return false;
        }
        return metadata.annotations() != null && metadata.annotations().stream()
                .anyMatch(annotation -> annotation.equals(simpleName)
                        || annotation.endsWith("." + simpleName));
    }

    private ReferenceMetadata readReferenceMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new ReferenceMetadata(null, 0, 0);
        }
        try {
            ReferenceMetadata metadata = objectMapper.readValue(metadataJson, ReferenceMetadata.class);
            return new ReferenceMetadata(
                    metadata.classification(),
                    metadata.loopDepth() == null ? 0 : metadata.loopDepth(),
                    metadata.synchronizedDepth() == null ? 0 : metadata.synchronizedDepth()
            );
        } catch (JsonProcessingException exception) {
            return new ReferenceMetadata(null, 0, 0);
        }
    }

    private boolean intersects(CodeReference reference, List<LineRange> changedLines) {
        return changedLines.stream().anyMatch(range ->
                reference.getStartLine() <= range.endLine()
                        && reference.getEndLine() >= range.startLine()
        );
    }

    private record ChunkMetadata(List<String> annotations, Integer parameterCount) {
    }

    private record ReferenceMetadata(
            String classification,
            Integer loopDepth,
            Integer synchronizedDepth
    ) {
    }
}
