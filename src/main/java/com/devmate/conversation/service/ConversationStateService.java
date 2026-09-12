package com.devmate.conversation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.conversation.entity.Conversation;
import com.devmate.conversation.entity.ConversationMessage;
import com.devmate.conversation.mapper.ConversationMapper;
import com.devmate.conversation.mapper.ConversationMessageMapper;
import com.devmate.user.service.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class ConversationStateService {
    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final CurrentUserService currentUserService;

    public ConversationStateService(ConversationMapper conversationMapper,
                                    ConversationMessageMapper messageMapper,
                                    CurrentUserService currentUserService) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public Conversation create(Long projectId, String revision, String provider,
                               String modelName, String promptVersion, String requestedTitle) {
        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = new Conversation();
        conversation.setUserId(currentUserService.getRequiredUser().id());
        conversation.setProjectId(projectId);
        conversation.setTitle(readableTitle(requestedTitle));
        conversation.setStatus("ACTIVE");
        conversation.setDeleted(0);
        conversation.setRevision(revision);
        conversation.setProvider(provider);
        conversation.setModelName(modelName);
        conversation.setPromptVersion(promptVersion);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return conversation;
    }

    @Transactional(readOnly = true)
    public List<Conversation> list(Long projectId) {
        return conversationMapper.selectList(Wrappers.lambdaQuery(Conversation.class)
                .eq(Conversation::getProjectId, projectId)
                .eq(Conversation::getUserId, currentUserService.getRequiredUser().id())
                .eq(Conversation::getDeleted, 0)
                .orderByDesc(Conversation::getUpdatedAt)
                .orderByDesc(Conversation::getId)
                .last("LIMIT 50"));
    }

    @Transactional(readOnly = true)
    public Conversation requireOwned(Long conversationId, Long projectId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        validateOwned(conversation, projectId);
        return conversation;
    }

    @Transactional(readOnly = true)
    public List<ConversationMessage> messages(Long conversationId, Long projectId) {
        requireOwned(conversationId, projectId);
        return messageMapper.selectList(Wrappers.lambdaQuery(ConversationMessage.class)
                .eq(ConversationMessage::getConversationId, conversationId)
                .orderByAsc(ConversationMessage::getSequenceNo));
    }

    @Transactional(readOnly = true)
    public ConversationMessage findAttempt(Long conversationId, Long projectId,
                                           String attemptKey, String requestHash) {
        requireOwned(conversationId, projectId);
        ConversationMessage message = byAttempt(conversationId, attemptKey);
        if (message != null && !Objects.equals(message.getRequestHash(), requestHash)) {
            throw new BusinessException(ErrorCode.CONFLICT, "请求标识已用于不同问题");
        }
        return message;
    }

    @Transactional
    public PreparedTurn prepareTurn(Long conversationId, Long projectId, String question,
                                    String attemptKey, String requestHash, String evidenceJson,
                                    int historyLimit) {
        Conversation conversation = conversationMapper.selectByIdForUpdate(conversationId);
        validateOwned(conversation, projectId);
        ConversationMessage previous = byAttempt(conversationId, attemptKey);
        if (previous != null) {
            if (!Objects.equals(previous.getRequestHash(), requestHash)) {
                throw new BusinessException(ErrorCode.CONFLICT, "请求标识已用于不同问题");
            }
            return new PreparedTurn(conversation, previous, List.of(), true);
        }
        if (!"ACTIVE".equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "对话已关闭，不能继续提问");
        }
        Long runningCount = messageMapper.selectCount(Wrappers.lambdaQuery(ConversationMessage.class)
                .eq(ConversationMessage::getConversationId, conversationId)
                .eq(ConversationMessage::getStatus, "RUNNING"));
        if (runningCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前对话已有回答正在生成");
        }
        List<ConversationMessage> history = recentCompleted(conversationId, historyLimit);
        int nextSequence = nextSequence(conversationId);
        LocalDateTime now = LocalDateTime.now();
        ConversationMessage user = new ConversationMessage();
        user.setConversationId(conversationId);
        user.setSequenceNo(nextSequence);
        user.setMessageRole("USER");
        user.setContent(question);
        user.setStatus("COMPLETED");
        user.setCreatedAt(now);

        ConversationMessage assistant = new ConversationMessage();
        assistant.setConversationId(conversationId);
        assistant.setSequenceNo(nextSequence + 1);
        assistant.setMessageRole("ASSISTANT");
        assistant.setContent("");
        assistant.setModelName(conversation.getModelName());
        assistant.setStatus("RUNNING");
        assistant.setAttemptKey(attemptKey);
        assistant.setRequestHash(requestHash);
        assistant.setRunningKey(conversationId);
        assistant.setEvidenceJson(evidenceJson);
        assistant.setCreatedAt(now);
        // The locked conversation row serializes sequence allocation and attempt checks.
        // The database running_key constraint remains the final protection against concurrent turns.
        messageMapper.insert(user);
        messageMapper.insert(assistant);
        if ("新对话".equals(conversation.getTitle())) {
            conversation.setTitle(question.length() <= 40 ? question : question.substring(0, 40));
        }
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);
        List<ConversationMessage> promptHistory = new ArrayList<>(history);
        promptHistory.add(user);
        return new PreparedTurn(conversation, assistant, List.copyOf(promptHistory), false);
    }

    @Transactional
    public ConversationMessage complete(Long messageId, String content, long latencyMs) {
        int updated = messageMapper.update(null, Wrappers.lambdaUpdate(ConversationMessage.class)
                .eq(ConversationMessage::getId, messageId)
                .eq(ConversationMessage::getStatus, "RUNNING")
                .set(ConversationMessage::getStatus, "COMPLETED")
                .set(ConversationMessage::getContent, content)
                .set(ConversationMessage::getLatencyMs, latencyMs)
                .set(ConversationMessage::getRunningKey, null));
        if (updated != 1) throw new BusinessException(ErrorCode.CONFLICT, "回答状态已变化");
        ConversationMessage completed = messageMapper.selectById(messageId);
        conversationMapper.update(null, Wrappers.lambdaUpdate(Conversation.class)
                .eq(Conversation::getId, completed.getConversationId())
                .set(Conversation::getUpdatedAt, LocalDateTime.now()));
        return completed;
    }

    @Transactional
    public void fail(Long messageId, String errorCode, String errorMessage, long latencyMs) {
        messageMapper.update(null, Wrappers.lambdaUpdate(ConversationMessage.class)
                .eq(ConversationMessage::getId, messageId)
                .eq(ConversationMessage::getStatus, "RUNNING")
                .set(ConversationMessage::getStatus, "FAILED")
                .set(ConversationMessage::getErrorCode, limited(errorCode, 100))
                .set(ConversationMessage::getErrorMessage, limited(errorMessage, 500))
                .set(ConversationMessage::getLatencyMs, latencyMs)
                .set(ConversationMessage::getRunningKey, null));
    }

    private List<ConversationMessage> recentCompleted(Long conversationId, int limit) {
        List<ConversationMessage> loaded = messageMapper.selectList(
                Wrappers.lambdaQuery(ConversationMessage.class)
                        .eq(ConversationMessage::getConversationId, conversationId)
                        .eq(ConversationMessage::getStatus, "COMPLETED")
                        .in(ConversationMessage::getMessageRole, List.of("USER", "ASSISTANT"))
                        .orderByDesc(ConversationMessage::getSequenceNo)
                        .last("LIMIT " + Math.max(1, limit))
        );
        Collections.reverse(loaded);
        return loaded;
    }

    private int nextSequence(Long conversationId) {
        ConversationMessage latest = messageMapper.selectOne(Wrappers.lambdaQuery(ConversationMessage.class)
                .eq(ConversationMessage::getConversationId, conversationId)
                .orderByDesc(ConversationMessage::getSequenceNo)
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getSequenceNo() + 1;
    }

    private ConversationMessage byAttempt(Long conversationId, String attemptKey) {
        return messageMapper.selectOne(Wrappers.lambdaQuery(ConversationMessage.class)
                .eq(ConversationMessage::getConversationId, conversationId)
                .eq(ConversationMessage::getAttemptKey, attemptKey));
    }

    private void validateOwned(Conversation conversation, Long projectId) {
        Long userId = currentUserService.getRequiredUser().id();
        if (conversation == null || Integer.valueOf(1).equals(conversation.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "对话不存在");
        }
        if (!Objects.equals(conversation.getProjectId(), projectId)
                || !Objects.equals(conversation.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该对话");
        }
    }

    private String readableTitle(String value) {
        if (value == null || value.isBlank()) return "新对话";
        return value.trim();
    }

    private String limited(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    public record PreparedTurn(Conversation conversation, ConversationMessage assistant,
                               List<ConversationMessage> history, boolean reused) { }
}
