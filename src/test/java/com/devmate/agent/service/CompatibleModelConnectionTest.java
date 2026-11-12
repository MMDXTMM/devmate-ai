package com.devmate.agent.service;

import com.devmate.agent.dto.ModelConnectionUpdateRequest;
import com.devmate.agent.entity.UserModelConnection;
import com.devmate.agent.mapper.UserModelConnectionMapper;
import com.devmate.common.error.BusinessException;
import com.devmate.user.entity.AppUser;
import com.devmate.user.mapper.AppUserMapper;
import com.devmate.user.security.AuthenticatedUser;
import com.devmate.user.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "devmate.model-connections.compatible.enabled=true",
        "devmate.model-connections.compatible.display-name=测试兼容服务",
        "devmate.model-connections.compatible.base-url=https://compatible.example/v1",
        "devmate.model-connections.compatible.models=test-model",
        "devmate.model-connections.compatible.allowed-base-urls=https://second.example/v1"
})
@ActiveProfiles("test")
@Transactional
class CompatibleModelConnectionTest {
    @Autowired private ModelConnectionService service;
    @Autowired private AppUserMapper users;
    @Autowired private UserModelConnectionMapper connections;
    @MockitoBean private CurrentUserService currentUser;
    @MockitoBean private SpringAiChatClientFactory client;

    private void login(String username) {
        var user = new AppUser();
        user.setUsername(username); user.setPasswordHash("not-used"); user.setStatus("ACTIVE"); user.setDeleted(0);
        users.insert(user);
        when(currentUser.getRequiredUser()).thenReturn(new AuthenticatedUser(user.getId(), user.getUsername()));
    }

    @Test
    void savesNormalizedAccountEndpointAndCustomModelWithLegacyFallback() {
        login("relay-format");
        service.update(new ModelConnectionUpdateRequest("COMPATIBLE", "vendor/model-2", "test-only-key",
                " HTTPS://COMPATIBLE.EXAMPLE:443/v1/chat/completions/ ", "CHAT_COMPLETIONS"));
        var selected = service.requireActiveConnection();
        assertThat(selected.baseUrl()).isEqualTo("https://compatible.example/v1");
        assertThat(selected.model()).isEqualTo("vendor/model-2");
        assertThat(service.list()).filteredOn(p -> p.provider().equals("COMPATIBLE")).singleElement().satisfies(p -> {
            assertThat(p.protocol()).isEqualTo("CHAT_COMPLETIONS");
            assertThat(p.models()).contains("vendor/model-2", "test-model");
            assertThat(p.allowedBaseUrls()).containsExactly("https://compatible.example/v1", "https://second.example/v1");
        });
        // Old requests omit new fields and must preserve the account endpoint and encrypted key.
        service.update(new ModelConnectionUpdateRequest("COMPATIBLE", "vendor/model-3", ""));
        assertThat(service.requireSavedConnection("COMPATIBLE", "vendor/model-3").baseUrl()).isEqualTo(selected.baseUrl());
        assertThat(service.requireActiveConnection().apiKey()).isEqualTo("test-only-key");
        service.update(new ModelConnectionUpdateRequest("COMPATIBLE", "vendor/model-3", "new-test-key",
                "https://second.example/v1/", "CHAT_COMPLETIONS"));
        assertThat(service.requireActiveConnection().baseUrl()).isEqualTo("https://second.example/v1");
        service.update(new ModelConnectionUpdateRequest("COMPATIBLE", "vendor/model-4", ""));
        assertThat(service.requireActiveConnection().baseUrl()).isEqualTo("https://second.example/v1");
        assertThat(service.requireActiveConnection().apiKey()).isEqualTo("new-test-key");
    }

    @Test
    void rejectsUnsafeUnapprovedAddressProtocolMismatchAndKeyReuseAcrossEndpointsAtomically() {
        login("relay-invalid");
        service.update(new ModelConnectionUpdateRequest("COMPATIBLE", "test-model", "test-only-key"));
        for (String url : java.util.List.of("http://127.0.0.1/v1", "https://user:password@compatible.example/v1",
                "https://compatible.example/v1?key=private", "https://compatible.example/v1#secret", "https://unapproved.example/v1")) {
            assertThatThrownBy(() -> service.update(new ModelConnectionUpdateRequest("COMPATIBLE", "test-model", "x", url, "CHAT_COMPLETIONS")))
                    .isInstanceOf(BusinessException.class).hasMessageNotContaining("password").hasMessageNotContaining("private").hasMessageNotContaining("secret").hasMessageNotContaining("unapproved.example");
        }
        assertThatThrownBy(() -> service.update(new ModelConnectionUpdateRequest("COMPATIBLE", "test-model", "",
                "https://second.example/v1", "CHAT_COMPLETIONS"))).hasMessageContaining("重新填写");
        assertThatThrownBy(() -> service.update(new ModelConnectionUpdateRequest("COMPATIBLE", "test-model", "",
                null, "RESPONSES"))).hasMessageContaining("仅支持 Chat Completions");
        assertThat(service.requireActiveConnection().apiKey()).isEqualTo("test-only-key");
        assertThat(service.requireActiveConnection().baseUrl()).isEqualTo("https://compatible.example/v1");
    }

    @Test
    void persistsAccountKeyAndUsesOnlyAdministratorEndpointAndModel() {
        AppUser user = new AppUser();
        user.setUsername("compatible-model-test");
        user.setPasswordHash("not-used");
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        users.insert(user);
        when(currentUser.getRequiredUser()).thenReturn(new AuthenticatedUser(user.getId(), user.getUsername()));
        assertThat(service.list()).hasSize(4).anySatisfy(item -> {
            assertThat(item.provider()).isEqualTo("COMPATIBLE");
            assertThat(item.displayName()).isEqualTo("测试兼容服务");
            assertThat(item.baseUrl()).isEqualTo("https://compatible.example/v1");
        });
        service.update(new ModelConnectionUpdateRequest("COMPATIBLE", "test-model", "test-only-key"));
        assertThat(service.requireActiveConnection().baseUrl()).isEqualTo("https://compatible.example/v1");
        assertThat(connections.selectList(null)).filteredOn(item -> item.getUserId().equals(user.getId()))
                .singleElement().extracting(UserModelConnection::getEncryptedApiKey)
                .asString().startsWith("v1:").doesNotContain("test-only-key");
        assertThatThrownBy(() -> service.update(new ModelConnectionUpdateRequest(
                "COMPATIBLE", "invalid model with spaces", "test-only-key")))
                .isInstanceOf(BusinessException.class).hasMessage("所选模型不属于当前提供方");
        when(client.chat("https://compatible.example/v1", "test-only-key", "test-model",
                "你是连接检查器。", "只回复 OK", null)).thenReturn("OK");
        assertThat(service.test().provider()).isEqualTo("COMPATIBLE");
        when(currentUser.getRequiredUser()).thenReturn(new AuthenticatedUser(user.getId() + 1, "another-user"));
        assertThat(service.list()).allMatch(item -> !item.configured() && !item.active());
        assertThatThrownBy(service::requireActiveConnection).isInstanceOf(BusinessException.class);
    }
}
