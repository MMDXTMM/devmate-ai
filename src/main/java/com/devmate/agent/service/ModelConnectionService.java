package com.devmate.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.dto.ModelConnectionTestResponse;
import com.devmate.agent.dto.ModelConnectionUpdateRequest;
import com.devmate.agent.dto.ModelProviderResponse;
import com.devmate.agent.entity.UserModelConnection;
import com.devmate.agent.mapper.UserModelConnectionMapper;
import com.devmate.agent.model.ModelConnectionSnapshot;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.user.service.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModelConnectionService {

    private static final Map<String, ProviderDefinition> PROVIDERS = providers();

    private final CurrentUserService currentUserService;
    private final SpringAiChatClientFactory chatClientFactory;
    private final UserModelConnectionMapper connectionMapper;
    private final ModelApiKeyCipher apiKeyCipher;

    public ModelConnectionService(CurrentUserService currentUserService, SpringAiChatClientFactory chatClientFactory,
                                  UserModelConnectionMapper connectionMapper, ModelApiKeyCipher apiKeyCipher) {
        this.currentUserService = currentUserService;
        this.chatClientFactory = chatClientFactory;
        this.connectionMapper = connectionMapper;
        this.apiKeyCipher = apiKeyCipher;
    }

    public List<ModelProviderResponse> list() {
        Long userId = currentUserService.getRequiredUser().id();
        Map<String, UserModelConnection> connections = connectionMapper.selectList(
                Wrappers.lambdaQuery(UserModelConnection.class).eq(UserModelConnection::getUserId, userId)
        ).stream().collect(java.util.stream.Collectors.toMap(UserModelConnection::getProvider, item -> item));
        return PROVIDERS.values().stream().map(provider -> new ModelProviderResponse(
                provider.id(), provider.displayName(), provider.baseUrl(), provider.models(),
                connections.containsKey(provider.id()),
                connections.containsKey(provider.id()) && Integer.valueOf(1).equals(connections.get(provider.id()).getIsActive()),
                connections.containsKey(provider.id())
                        ? connections.get(provider.id()).getModelName() : provider.models().getFirst()
        )).toList();
    }

    @Transactional
    public List<ModelProviderResponse> update(ModelConnectionUpdateRequest request) {
        ProviderDefinition provider = provider(request.provider());
        if (!provider.models().contains(request.model())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "所选模型不属于当前提供方");
        }
        Long userId = currentUserService.getRequiredUser().id();
        UserModelConnection existing = connectionMapper.selectOne(Wrappers.lambdaQuery(UserModelConnection.class)
                .eq(UserModelConnection::getUserId, userId)
                .eq(UserModelConnection::getProvider, provider.id()));
        if (!StringUtils.hasText(request.apiKey()) && existing == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请先填写该提供方的API Key");
        }
        connectionMapper.update(null, Wrappers.lambdaUpdate(UserModelConnection.class)
                .eq(UserModelConnection::getUserId, userId)
                .set(UserModelConnection::getIsActive, 0)
                .set(UserModelConnection::getActiveUserId, null));
        UserModelConnection connection = existing == null ? new UserModelConnection() : existing;
        connection.setUserId(userId);
        connection.setProvider(provider.id());
        connection.setModelName(request.model());
        connection.setIsActive(1);
        connection.setActiveUserId(userId);
        connection.setUpdatedAt(java.time.LocalDateTime.now());
        if (StringUtils.hasText(request.apiKey())) {
            connection.setEncryptedApiKey(apiKeyCipher.encrypt(userId, provider.id(), request.apiKey().trim()));
        }
        if (existing == null) connectionMapper.insert(connection); else connectionMapper.updateById(connection);
        return list();
    }

    public ModelConnectionTestResponse test() {
        ModelConnectionSnapshot connection = requireActiveConnection();
        long started = System.nanoTime();
        try {
            chatClientFactory.chat(connection.baseUrl(), connection.apiKey(), connection.model(),
                    "你是连接检查器。", "只回复 OK", null);
            return new ModelConnectionTestResponse(
                    connection.provider(), connection.model(), elapsedMs(started), "连接成功"
            );
        } catch (SpringAiChatClientFactory.EmptyModelResponseException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型连接成功但没有返回内容");
        } catch (RuntimeException exception) {
            Integer status = responseStatus(exception);
            if (Integer.valueOf(401).equals(status) || Integer.valueOf(403).equals(status)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "API Key无效或没有模型权限");
            }
            if (Integer.valueOf(429).equals(status)) {
                throw new BusinessException(ErrorCode.CONFLICT, "模型额度不足或请求过于频繁");
            }
            if (hasCause(exception, ResourceAccessException.class)
                    || hasCause(exception, java.net.ConnectException.class)
                    || hasCause(exception, java.net.SocketTimeoutException.class)) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法连接模型服务，请检查网络后重试");
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型连接测试失败，请稍后重试");
        }
    }

    public ModelConnectionSnapshot requireActiveConnection() {
        Long userId = currentUserService.getRequiredUser().id();
        UserModelConnection state = connectionMapper.selectOne(Wrappers.lambdaQuery(UserModelConnection.class)
                .eq(UserModelConnection::getUserId, userId)
                .eq(UserModelConnection::getIsActive, 1));
        if (state == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先在大模型连接中保存并启用一个模型");
        }
        ProviderDefinition provider = provider(state.getProvider());
        String apiKey = apiKeyCipher.decrypt(userId, state.getProvider(), state.getEncryptedApiKey());
        return new ModelConnectionSnapshot(provider.id(), state.getModelName(), provider.baseUrl(), apiKey);
    }

    private Integer responseStatus(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof RestClientResponseException responseException) {
                return responseException.getStatusCode().value();
            }
            if (current instanceof NonTransientAiException && current.getMessage() != null) {
                String message = current.getMessage().trim();
                if (message.matches("^[1-5][0-9]{2}\\s*-.*")) {
                    return Integer.parseInt(message.substring(0, 3));
                }
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }

    private ProviderDefinition provider(String id) {
        ProviderDefinition provider = PROVIDERS.get(id == null ? "" : id.trim().toUpperCase());
        if (provider == null) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "暂不支持该模型提供方");
        return provider;
    }

    private long elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000; }

    private static Map<String, ProviderDefinition> providers() {
        Map<String, ProviderDefinition> providers = new LinkedHashMap<>();
        providers.put("DEEPSEEK", new ProviderDefinition("DEEPSEEK", "DeepSeek",
                "https://api.deepseek.com", List.of("deepseek-v4-flash", "deepseek-v4-pro")));
        providers.put("DASHSCOPE", new ProviderDefinition("DASHSCOPE", "通义千问",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                List.of("qwen-plus", "qwen3.7-plus", "qwen3-coder-plus", "qwen-flash")));
        providers.put("OPENAI", new ProviderDefinition("OPENAI", "OpenAI",
                "https://api.openai.com/v1", List.of("gpt-5.1", "gpt-5-mini", "gpt-4.1")));
        return Collections.unmodifiableMap(providers);
    }

    private record ProviderDefinition(String id, String displayName, String baseUrl, List<String> models) { }
}
