package com.devmate.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.dto.ModelConnectionUpdateRequest;
import com.devmate.agent.entity.UserModelConnection;
import com.devmate.agent.mapper.UserModelConnectionMapper;
import com.devmate.common.error.BusinessException;
import com.devmate.user.entity.AppUser;
import com.devmate.user.mapper.AppUserMapper;
import com.devmate.user.security.AuthenticatedUser;
import com.devmate.user.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ModelConnectionServiceTest {

    @Autowired private ModelConnectionService service;
    @Autowired private UserModelConnectionMapper connectionMapper;
    @Autowired private AppUserMapper appUserMapper;
    @MockitoBean private CurrentUserService currentUserService;
    @MockitoBean private SpringAiChatClientFactory chatClientFactory;

    @BeforeEach
    void insertUsers() {
        connectionMapper.delete(Wrappers.lambdaQuery(UserModelConnection.class)
                .in(UserModelConnection::getUserId, 8101L, 8102L));
        insertUser(8101L, "model-user-a");
        insertUser(8102L, "model-user-b");
    }

    @Test
    void persistsEncryptedKeysPerUserAndRestoresProviderSelectionFromDatabase() {
        loginAs(8101L, "model-user-a");
        assertThat(service.list()).hasSize(3).allMatch(item -> !item.configured() && !item.active());

        service.update(new ModelConnectionUpdateRequest("DEEPSEEK", "deepseek-v4-flash", "secret-deepseek"));
        service.update(new ModelConnectionUpdateRequest("DASHSCOPE", "qwen-plus", "secret-qwen"));
        var switchedBack = service.update(new ModelConnectionUpdateRequest("DEEPSEEK", "deepseek-v4-pro", ""));

        assertThat(switchedBack).filteredOn(item -> item.provider().equals("DEEPSEEK"))
                .singleElement().satisfies(item -> {
                    assertThat(item.configured()).isTrue();
                    assertThat(item.active()).isTrue();
                    assertThat(item.selectedModel()).isEqualTo("deepseek-v4-pro");
                    assertThat(item.toString()).doesNotContain("secret-deepseek");
                });
        UserModelConnection stored = connectionMapper.selectOne(Wrappers.lambdaQuery(UserModelConnection.class)
                .eq(UserModelConnection::getUserId, 8101L)
                .eq(UserModelConnection::getProvider, "DEEPSEEK"));
        assertThat(stored.getEncryptedApiKey()).startsWith("v1:").doesNotContain("secret-deepseek");

        loginAs(8102L, "model-user-b");
        assertThat(service.list()).allMatch(item -> !item.configured() && !item.active());

        loginAs(8101L, "model-user-a");
        assertThat(service.list()).filteredOn(item -> item.provider().equals("DEEPSEEK"))
                .singleElement().matches(item -> item.configured() && item.active());
    }

    @Test
    void rejectsUnknownModelAndMissingKey() {
        loginAs(8101L, "model-user-a");
        assertThatThrownBy(() -> service.update(new ModelConnectionUpdateRequest(
                "DEEPSEEK", "old-model", "secret"
        ))).isInstanceOf(BusinessException.class).hasMessage("所选模型不属于当前提供方");
        assertThatThrownBy(() -> service.update(new ModelConnectionUpdateRequest(
                "OPENAI", "gpt-5.1", ""
        ))).isInstanceOf(BusinessException.class).hasMessage("请先填写该提供方的API Key");
    }

    @Test
    void databasePreventsTwoActiveProvidersForOneUser() {
        loginAs(8101L, "model-user-a");
        service.update(new ModelConnectionUpdateRequest("DEEPSEEK", "deepseek-v4-flash", "first-secret"));

        UserModelConnection duplicateActive = new UserModelConnection();
        duplicateActive.setUserId(8101L);
        duplicateActive.setProvider("DASHSCOPE");
        duplicateActive.setModelName("qwen-plus");
        duplicateActive.setEncryptedApiKey("v1:not-a-real-secret");
        duplicateActive.setIsActive(1);
        duplicateActive.setActiveUserId(8101L);
        assertThatThrownBy(() -> connectionMapper.insert(duplicateActive))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void testsActiveConnectionThroughSpringAiWithoutExposingTheKey() {
        loginAs(8101L, "model-user-a");
        service.update(new ModelConnectionUpdateRequest("DEEPSEEK", "deepseek-v4-flash", "secret-deepseek"));
        when(chatClientFactory.chat(
                "https://api.deepseek.com", "secret-deepseek", "deepseek-v4-flash",
                "你是连接检查器。", "只回复 OK", null
        )).thenReturn("OK");

        var response = service.test();

        assertThat(response.provider()).isEqualTo("DEEPSEEK");
        assertThat(response.model()).isEqualTo("deepseek-v4-flash");
        assertThat(response.message()).isEqualTo("连接成功");
        assertThat(response.toString()).doesNotContain("secret-deepseek");
    }

    @Test
    void convertsProviderErrorsToReadableMessages() {
        loginAs(8101L, "model-user-a");
        service.update(new ModelConnectionUpdateRequest("OPENAI", "gpt-5-mini", "invalid-secret"));
        when(chatClientFactory.chat(
                "https://api.openai.com/v1", "invalid-secret", "gpt-5-mini",
                "你是连接检查器。", "只回复 OK", null
        )).thenThrow(new RuntimeException(new HttpClientErrorException(HttpStatus.UNAUTHORIZED)));

        assertThatThrownBy(service::test)
                .isInstanceOf(BusinessException.class)
                .hasMessage("API Key无效或没有模型权限")
                .hasMessageNotContaining("invalid-secret");
    }

    @Test
    void distinguishesRateLimitsAndEmptyResponses() {
        loginAs(8101L, "model-user-a");
        service.update(new ModelConnectionUpdateRequest("DASHSCOPE", "qwen-plus", "quota-secret"));
        when(chatClientFactory.chat(
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "quota-secret", "qwen-plus",
                "你是连接检查器。", "只回复 OK", null
        )).thenThrow(new RuntimeException(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)));

        assertThatThrownBy(service::test)
                .isInstanceOf(BusinessException.class)
                .hasMessage("模型额度不足或请求过于频繁");

        doThrow(new SpringAiChatClientFactory.EmptyModelResponseException()).when(chatClientFactory).chat(
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "quota-secret", "qwen-plus",
                "你是连接检查器。", "只回复 OK", null
        );

        assertThatThrownBy(service::test)
                .isInstanceOf(BusinessException.class)
                .hasMessage("模型连接成功但没有返回内容");
    }

    private void loginAs(long id, String username) {
        when(currentUserService.getRequiredUser()).thenReturn(new AuthenticatedUser(id, username));
    }

    private void insertUser(long id, String username) {
        if (appUserMapper.selectById(id) != null) return;
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("not-used");
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        appUserMapper.insert(user);
    }
}
