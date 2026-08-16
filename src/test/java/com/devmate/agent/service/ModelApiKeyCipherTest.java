package com.devmate.agent.service;

import com.devmate.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelApiKeyCipherTest {

    @Test
    void encryptsWithAccountAndProviderBinding() {
        ModelApiKeyCipher cipher = new ModelApiKeyCipher("test-model-encryption-secret-32-characters");
        String encrypted = cipher.encrypt(10L, "DEEPSEEK", "sk-private-value");

        assertThat(encrypted).startsWith("v1:").doesNotContain("sk-private-value");
        assertThat(cipher.decrypt(10L, "DEEPSEEK", encrypted)).isEqualTo("sk-private-value");
        assertThatThrownBy(() -> cipher.decrypt(11L, "DEEPSEEK", encrypted))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已保存的API Key无法解密，请重新填写");
    }

    @Test
    void rejectsMissingServerEncryptionSecret() {
        ModelApiKeyCipher cipher = new ModelApiKeyCipher("");
        assertThatThrownBy(() -> cipher.encrypt(10L, "DEEPSEEK", "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("服务端未配置模型密钥加密密钥");
    }
}
