package com.devmate.generation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.generation.dto.ConfirmGenerationSpecRequest;
import com.devmate.generation.dto.CreateGenerationSessionRequest;
import com.devmate.generation.dto.GenerationSessionResponse;
import com.devmate.generation.dto.GenerationSpecResponse;
import com.devmate.generation.dto.RequirementAnswerRequest;
import com.devmate.generation.dto.SubmitClarificationRequest;
import com.devmate.generation.entity.GenerationSession;
import com.devmate.generation.entity.GenerationSpecVersion;
import com.devmate.generation.mapper.GenerationSessionMapper;
import com.devmate.generation.mapper.GenerationSpecVersionMapper;
import com.devmate.generation.model.GenerationSessionStatus;
import com.devmate.generation.model.GenerationSpecStatus;
import com.devmate.generation.model.RequirementAnswer;
import com.devmate.generation.model.RequirementDecisionMode;
import com.devmate.generation.model.RequirementDraft;
import com.devmate.generation.model.RequirementInputType;
import com.devmate.generation.model.RequirementOption;
import com.devmate.generation.model.RequirementQuestion;
import com.devmate.user.config.SecurityProperties;
import com.devmate.user.service.CurrentUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GenerationSessionService {

    private final GenerationSessionMapper sessionMapper;
    private final GenerationSpecVersionMapper specVersionMapper;
    private final RequirementDraftProvider draftProvider;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    private final SecurityProperties securityProperties;
    private final TransactionTemplate transactions;

    public GenerationSessionService(
            GenerationSessionMapper sessionMapper,
            GenerationSpecVersionMapper specVersionMapper,
            RequirementDraftProvider draftProvider,
            ObjectMapper objectMapper,
            CurrentUserService currentUserService,
            SecurityProperties securityProperties,
            PlatformTransactionManager transactionManager
    ) {
        this.sessionMapper = sessionMapper;
        this.specVersionMapper = specVersionMapper;
        this.draftProvider = draftProvider;
        this.objectMapper = objectMapper;
        this.currentUserService = currentUserService;
        this.securityProperties = securityProperties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public GenerationSessionResponse createSession(CreateGenerationSessionRequest request) {
        String requirement = request.requirement().trim();
        currentOwnerId();
        RequirementDraft draft = draftProvider.createInitialDraft(requirement);
        validateQuestionContract(draft.questions());
        return transactions.execute(ignored -> {
            LocalDateTime now = LocalDateTime.now();

            GenerationSession session = new GenerationSession();
            session.setOwnerId(currentOwnerId());
            session.setOriginalRequirement(requirement);
            session.setStatus(GenerationSessionStatus.CLARIFYING.name());
            session.setLatestVersionNo(1);
            session.setDeleted(0);
            session.setCreatedAt(now);
            session.setUpdatedAt(now);
            requireSingleRow(sessionMapper.insert(session), "需求会话创建失败");

            GenerationSpecVersion version = buildVersion(session.getId(), 1, draft, List.of(), now);
            requireSingleRow(specVersionMapper.insert(version), "需求草案保存失败");
            return toResponse(session, version);
        });
    }

    public GenerationSessionResponse getSession(Long sessionId) {
        GenerationSession session = findOwnedSession(sessionId);
        if ("THINKING".equals(session.getStatus()) && session.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {
            releaseThinking(sessionId, session.getLatestVersionNo(), "上次追问已中断，请重新提交当前答案");
            session = findOwnedSession(sessionId);
        }
        return toResponse(session, findLatestVersion(session));
    }

    public List<GenerationSessionResponse> listSessions() {
        Long ownerId = currentOwnerId();
        return sessionMapper.selectList(Wrappers.lambdaQuery(GenerationSession.class)
                .eq(ownerId != null, GenerationSession::getOwnerId, ownerId)
                .orderByDesc(GenerationSession::getUpdatedAt).orderByDesc(GenerationSession::getId).last("LIMIT 20"))
                .stream().map(item -> toResponse(item, findLatestVersion(item))).toList();
    }

    public GenerationSessionResponse submitClarification(
            Long sessionId,
            SubmitClarificationRequest request
    ) {
        GenerationSession session = findOwnedSession(sessionId);
        requireClarifying(session);
        GenerationSpecVersion previousVersion = findLatestVersion(session);
        if (request.versionId() != null && !request.versionId().equals(previousVersion.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "需求方案已被更新，请刷新后重新提交");
        }
        List<RequirementQuestion> questions = readQuestions(previousVersion);
        List<RequirementAnswer> mergedAnswers = mergeAndValidateAnswers(
                questions,
                readAnswers(previousVersion),
                request.answers()
        );
        RequirementDraft previousDraft = toDraft(previousVersion, questions);
        if (!"LEGACY".equals(previousVersion.getStage())) validateRequiredAnswers(questions, mergedAnswers);
        if (session.getLatestVersionNo() >= 9) throw new BusinessException(ErrorCode.CONFLICT, "本会话已达到8轮追问上限，请整理需求后新建会话");
        int locked = sessionMapper.update(null, Wrappers.lambdaUpdate(GenerationSession.class)
                .eq(GenerationSession::getId, sessionId).eq(GenerationSession::getLatestVersionNo, session.getLatestVersionNo())
                .eq(GenerationSession::getStatus, "CLARIFYING").set(GenerationSession::getStatus, "THINKING")
                .set(GenerationSession::getLastError, null).set(GenerationSession::getUpdatedAt, LocalDateTime.now()));
        if (locked != 1) throw new BusinessException(ErrorCode.CONFLICT, "AI 正在处理这份需求，请稍后刷新");
        try {
            RequirementDraft revisedDraft = draftProvider.reviseDraft(session.getOriginalRequirement(), previousDraft, mergedAnswers);
            validateQuestionContract(revisedDraft.questions());
            return transactions.execute(ignored -> {

                int nextVersionNo = session.getLatestVersionNo() + 1;
                LocalDateTime now = LocalDateTime.now();
                int updated = sessionMapper.update(
                        null,
                        Wrappers.lambdaUpdate(GenerationSession.class)
                                .eq(GenerationSession::getId, sessionId)
                                .eq(GenerationSession::getLatestVersionNo, session.getLatestVersionNo())
                                .eq(GenerationSession::getStatus, "THINKING")
                                .set(GenerationSession::getStatus, "CLARIFYING")
                                .set(GenerationSession::getLatestVersionNo, nextVersionNo)
                                .set(GenerationSession::getUpdatedAt, now)
                );
                if (updated != 1) {
                    throw new BusinessException(ErrorCode.CONFLICT, "需求方案已被更新，请刷新后重新提交");
                }

                GenerationSpecVersion nextVersion = buildVersion(
                        sessionId, nextVersionNo, revisedDraft, mergedAnswers, now
                );
                requireSingleRow(specVersionMapper.insert(nextVersion), "需求方案版本保存失败");
                session.setLatestVersionNo(nextVersionNo);
                session.setStatus("CLARIFYING");
                session.setLastError(null);
                session.setUpdatedAt(now);
                return toResponse(session, nextVersion);
            });
        } catch (RuntimeException exception) {
            releaseThinking(sessionId, session.getLatestVersionNo(), "本轮追问失败，已保留上一版方案和答案，请重试");
            throw exception;
        }
    }

    private void releaseThinking(Long id, int version, String reason) {
        sessionMapper.update(null, Wrappers.lambdaUpdate(GenerationSession.class).eq(GenerationSession::getId, id)
                .eq(GenerationSession::getLatestVersionNo, version).eq(GenerationSession::getStatus, "THINKING")
                .set(GenerationSession::getStatus, "CLARIFYING").set(GenerationSession::getLastError, reason)
                .set(GenerationSession::getUpdatedAt, LocalDateTime.now()));
    }

    @Transactional
    public GenerationSessionResponse confirmSpec(
            Long sessionId,
            ConfirmGenerationSpecRequest request
    ) {
        GenerationSession session = findOwnedSession(sessionId);
        requireClarifying(session);
        GenerationSpecVersion latestVersion = findLatestVersion(session);
        if (!latestVersion.getId().equals(request.versionId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能确认当前最新的需求方案");
        }
        validateRequiredAnswers(readQuestions(latestVersion), readAnswers(latestVersion));
        if (!"LEGACY".equals(latestVersion.getStage()) && !Boolean.TRUE.equals(latestVersion.getReadyForGeneration())) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先完成业务、语言、框架架构和运行方式的选择");
        }

        LocalDateTime now = LocalDateTime.now();
        int updatedSession = sessionMapper.update(
                null,
                Wrappers.lambdaUpdate(GenerationSession.class)
                        .eq(GenerationSession::getId, sessionId)
                        .eq(GenerationSession::getStatus, GenerationSessionStatus.CLARIFYING.name())
                        .eq(GenerationSession::getLatestVersionNo, latestVersion.getVersionNo())
                        .set(GenerationSession::getStatus, GenerationSessionStatus.CONFIRMED.name())
                        .set(GenerationSession::getConfirmedVersionId, latestVersion.getId())
                        .set(GenerationSession::getUpdatedAt, now)
        );
        if (updatedSession != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "需求方案状态已变化，请刷新后重试");
        }
        requireSingleRow(
                specVersionMapper.update(
                        null,
                        Wrappers.lambdaUpdate(GenerationSpecVersion.class)
                                .eq(GenerationSpecVersion::getId, latestVersion.getId())
                                .eq(GenerationSpecVersion::getStatus, GenerationSpecStatus.DRAFT.name())
                                .set(GenerationSpecVersion::getStatus, GenerationSpecStatus.CONFIRMED.name())
                ),
                "需求方案确认失败"
        );

        session.setStatus(GenerationSessionStatus.CONFIRMED.name());
        session.setConfirmedVersionId(latestVersion.getId());
        session.setUpdatedAt(now);
        latestVersion.setStatus(GenerationSpecStatus.CONFIRMED.name());
        return toResponse(session, latestVersion);
    }

    private GenerationSpecVersion buildVersion(
            Long sessionId,
            int versionNo,
            RequirementDraft draft,
            List<RequirementAnswer> answers,
            LocalDateTime createdAt
    ) {
        GenerationSpecVersion version = new GenerationSpecVersion();
        version.setSessionId(sessionId);
        version.setVersionNo(versionNo);
        version.setRequirementSummary(draft.requirementSummary());
        version.setArchitectureSummary(draft.architectureSummary());
        version.setAssumptionsJson(writeJson(draft.assumptions()));
        version.setQuestionsJson(writeJson(draft.questions()));
        version.setAnswersJson(writeJson(answers));
        version.setStatus(GenerationSpecStatus.DRAFT.name());
        version.setPromptVersion(draft.promptVersion());
        version.setStage(draft.stage());
        version.setReadyForGeneration(draft.readyForGeneration());
        version.setCreatedAt(createdAt);
        return version;
    }

    private List<RequirementAnswer> mergeAndValidateAnswers(
            List<RequirementQuestion> questions,
            List<RequirementAnswer> existingAnswers,
            List<RequirementAnswerRequest> submittedAnswers
    ) {
        Map<String, RequirementQuestion> questionsById = questions.stream()
                .collect(Collectors.toMap(RequirementQuestion::id, question -> question));
        Map<String, RequirementAnswer> merged = new LinkedHashMap<>();
        existingAnswers.forEach(answer -> merged.put(answer.questionId(), answer));
        Set<String> submittedQuestionIds = new HashSet<>();
        for (RequirementAnswerRequest submitted : submittedAnswers) {
            String questionId = submitted.questionId().trim();
            RequirementQuestion question = questionsById.get(questionId);
            if (question == null) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "存在无法识别的澄清问题");
            }
            if (!submittedQuestionIds.add(questionId)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "同一个澄清问题不能重复提交");
            }
            merged.put(questionId, toAnswer(question, submitted));
        }
        return new ArrayList<>(merged.values());
    }

    private RequirementAnswer toAnswer(
            RequirementQuestion question,
            RequirementAnswerRequest request
    ) {
        boolean hasLegacyAnswer = hasText(request.answer());
        boolean hasStructuredAnswer = request.decisionMode() != null
                || request.selectedOptionIds() != null && !request.selectedOptionIds().isEmpty()
                || hasText(request.customAnswer());
        if (hasLegacyAnswer) {
            if (hasStructuredAnswer) {
                throw invalidAnswer("旧版文本答案不能和选项式答案同时提交");
            }
            String answer = request.answer().trim();
            return new RequirementAnswer(question.id(), answer);
        }
        if (request.decisionMode() == null) {
            throw invalidAnswer("请选择一个方案或填写自定义答案");
        }

        List<String> selectedOptionIds = normalizeOptionIds(request.selectedOptionIds());
        String customAnswer = trimToEmpty(request.customAnswer());
        Map<String, RequirementOption> optionsById = question.options().stream()
                .collect(Collectors.toMap(RequirementOption::id, option -> option));
        for (String optionId : selectedOptionIds) {
            if (!optionsById.containsKey(optionId)) {
                throw invalidAnswer("存在无法识别的选项");
            }
        }

        return switch (request.decisionMode()) {
            case USER_SELECTED -> userSelectedAnswer(
                    question, selectedOptionIds, customAnswer, optionsById
            );
            case USER_ACCEPTED_RECOMMENDATION, AI_DEFAULTED -> recommendedAnswer(
                    question, request.decisionMode(), selectedOptionIds, customAnswer, optionsById
            );
            case CUSTOM -> customAnswer(question, selectedOptionIds, customAnswer);
            case LEGACY_TEXT -> throw invalidAnswer("新版答案不能直接使用 LEGACY_TEXT 模式");
        };
    }

    private RequirementAnswer userSelectedAnswer(
            RequirementQuestion question,
            List<String> selectedOptionIds,
            String customAnswer,
            Map<String, RequirementOption> optionsById
    ) {
        requireChoiceQuestion(question);
        validateSelectionCount(question, selectedOptionIds);
        String summary = "用户选择：" + selectedLabels(selectedOptionIds, optionsById);
        if (hasText(customAnswer)) {
            if (!question.allowCustomAnswer()) {
                throw invalidAnswer("当前问题不支持自定义补充");
            }
            summary += "；补充：" + customAnswer;
        }
        return new RequirementAnswer(
                question.id(), RequirementDecisionMode.USER_SELECTED,
                selectedOptionIds, customAnswer, summary
        );
    }

    private RequirementAnswer recommendedAnswer(
            RequirementQuestion question,
            RequirementDecisionMode decisionMode,
            List<String> submittedOptionIds,
            String customAnswer,
            Map<String, RequirementOption> optionsById
    ) {
        requireChoiceQuestion(question);
        if (hasText(customAnswer)) {
            throw invalidAnswer("采用推荐方案时不能同时提交自定义答案");
        }
        List<String> recommendedIds = question.options().stream()
                .filter(RequirementOption::recommended)
                .map(RequirementOption::id)
                .toList();
        if (recommendedIds.isEmpty()) {
            throw invalidAnswer("当前问题没有可采用的 AI 推荐项");
        }
        if (!submittedOptionIds.isEmpty()
                && (!new HashSet<>(submittedOptionIds).equals(new HashSet<>(recommendedIds))
                || submittedOptionIds.size() != recommendedIds.size())) {
            throw invalidAnswer("提交的选项与 AI 推荐方案不一致");
        }
        String prefix = decisionMode == RequirementDecisionMode.AI_DEFAULTED
                ? "由 AI 按推荐方案决定："
                : "用户采用 AI 推荐：";
        return new RequirementAnswer(
                question.id(), decisionMode, recommendedIds, "",
                prefix + selectedLabels(recommendedIds, optionsById)
        );
    }

    private RequirementAnswer customAnswer(
            RequirementQuestion question,
            List<String> selectedOptionIds,
            String customAnswer
    ) {
        if (!question.allowCustomAnswer()) {
            throw invalidAnswer("当前问题不支持自定义答案");
        }
        if (!selectedOptionIds.isEmpty()) {
            throw invalidAnswer("自定义方案不能同时提交预设选项");
        }
        if (!hasText(customAnswer)) {
            throw invalidAnswer("请填写自定义答案");
        }
        return new RequirementAnswer(
                question.id(), RequirementDecisionMode.CUSTOM,
                List.of(), customAnswer, "自定义：" + customAnswer
        );
    }

    private void requireChoiceQuestion(RequirementQuestion question) {
        if (question.inputType() == RequirementInputType.FREE_TEXT) {
            throw invalidAnswer("自由文本问题需要填写自定义答案");
        }
    }

    private void validateSelectionCount(
            RequirementQuestion question,
            List<String> selectedOptionIds
    ) {
        if (selectedOptionIds.isEmpty()) {
            throw invalidAnswer("请至少选择一个方案");
        }
        if (question.inputType() == RequirementInputType.SINGLE_CHOICE
                && selectedOptionIds.size() != 1) {
            throw invalidAnswer("单选问题只能选择一个方案");
        }
    }

    private List<String> normalizeOptionIds(List<String> optionIds) {
        if (optionIds == null) {
            return List.of();
        }
        List<String> normalized = optionIds.stream().map(String::trim).toList();
        if (normalized.stream().anyMatch(String::isBlank)) {
            throw invalidAnswer("选项ID不能为空");
        }
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw invalidAnswer("同一个选项不能重复提交");
        }
        return normalized;
    }

    private String selectedLabels(
            List<String> selectedOptionIds,
            Map<String, RequirementOption> optionsById
    ) {
        return selectedOptionIds.stream()
                .map(optionId -> optionsById.get(optionId).label())
                .collect(Collectors.joining("、"));
    }

    private void validateQuestionContract(List<RequirementQuestion> questions) {
        if (questions == null || questions.size() > 40) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 追问数量超出限制，请重试");
        Set<String> questionIds = new HashSet<>();
        for (RequirementQuestion question : questions) {
            if (!hasText(question.id()) || !questionIds.add(question.id())) {
                throw new IllegalStateException("需求问题 ID 为空或重复");
            }
            if (question.inputType() == RequirementInputType.FREE_TEXT) {
                if (!question.options().isEmpty()) {
                    throw new IllegalStateException("自由文本问题不能包含预设选项");
                }
                continue;
            }
            if (question.options().size() < 2 || question.options().size() > 4) {
                throw new IllegalStateException("选择题必须包含 2 到 4 个选项");
            }
            Set<String> optionIds = new HashSet<>();
            long recommendedCount = question.options().stream()
                    .peek(option -> {
                        if (!hasText(option.id()) || !optionIds.add(option.id())) {
                            throw new IllegalStateException("需求选项 ID 为空或重复");
                        }
                    })
                    .filter(RequirementOption::recommended)
                    .count();
            if (recommendedCount == 0) {
                throw new IllegalStateException("选择题必须包含 AI 推荐项");
            }
            if (question.inputType() == RequirementInputType.SINGLE_CHOICE
                    && recommendedCount != 1) {
                throw new IllegalStateException("单选问题必须且只能包含一个推荐项");
            }
        }
    }

    private BusinessException invalidAnswer(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private void validateRequiredAnswers(
            List<RequirementQuestion> questions,
            List<RequirementAnswer> answers
    ) {
        Set<String> answeredIds = answers.stream()
                .filter(answer -> answer.answer() != null && !answer.answer().isBlank())
                .map(RequirementAnswer::questionId)
                .collect(Collectors.toSet());
        List<String> missing = questions.stream()
                .filter(RequirementQuestion::required)
                .filter(question -> !answeredIds.contains(question.id()))
                .map(RequirementQuestion::question)
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "还有必答问题未完成：" + String.join("；", missing)
            );
        }
    }

    private GenerationSession findOwnedSession(Long sessionId) {
        GenerationSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生成会话不存在");
        }
        if (securityProperties.isEnabled()
                && !currentUserService.getRequiredUser().id().equals(session.getOwnerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该生成会话");
        }
        return session;
    }

    private GenerationSpecVersion findLatestVersion(GenerationSession session) {
        GenerationSpecVersion version = specVersionMapper.selectOne(
                Wrappers.lambdaQuery(GenerationSpecVersion.class)
                        .eq(GenerationSpecVersion::getSessionId, session.getId())
                        .eq(GenerationSpecVersion::getVersionNo, session.getLatestVersionNo())
        );
        if (version == null) {
            throw new IllegalStateException("生成会话缺少最新需求方案");
        }
        return version;
    }

    private void requireClarifying(GenerationSession session) {
        if ("THINKING".equals(session.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "AI 正在整理下一轮问题，请稍后刷新");
        if (!GenerationSessionStatus.CLARIFYING.name().equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已确认的需求方案不能继续修改");
        }
    }

    private RequirementDraft toDraft(
            GenerationSpecVersion version,
            List<RequirementQuestion> questions
    ) {
        return new RequirementDraft(
                version.getRequirementSummary(),
                version.getArchitectureSummary(),
                readStringList(version.getAssumptionsJson()),
                questions,
                version.getPromptVersion(), version.getStage(), Boolean.TRUE.equals(version.getReadyForGeneration())
        );
    }

    private GenerationSessionResponse toResponse(
            GenerationSession session,
            GenerationSpecVersion version
    ) {
        GenerationSpecResponse spec = new GenerationSpecResponse(
                version.getId().toString(),
                version.getVersionNo(),
                version.getRequirementSummary(),
                version.getArchitectureSummary(),
                readStringList(version.getAssumptionsJson()),
                readQuestions(version),
                readAnswers(version),
                version.getStatus(),
                version.getPromptVersion(),
                version.getStage(),
                Boolean.TRUE.equals(version.getReadyForGeneration()),
                version.getCreatedAt()
        );
        return new GenerationSessionResponse(
                session.getId().toString(),
                session.getOriginalRequirement(),
                session.getStatus(),
                session.getLatestVersionNo(),
                session.getConfirmedVersionId() == null ? null : session.getConfirmedVersionId().toString(),
                session.getLastError(),
                spec,
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private List<RequirementQuestion> readQuestions(GenerationSpecVersion version) {
        return readJson(version.getQuestionsJson(), new TypeReference<>() { });
    }

    private List<RequirementAnswer> readAnswers(GenerationSpecVersion version) {
        return readJson(version.getAnswersJson(), new TypeReference<>() { });
    }

    private List<String> readStringList(String json) {
        return readJson(json, new TypeReference<>() { });
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("需求方案数据无法读取", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("需求方案数据无法保存", exception);
        }
    }

    private Long currentOwnerId() {
        return securityProperties.isEnabled() ? currentUserService.getRequiredUser().id() : null;
    }

    private void requireSingleRow(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
