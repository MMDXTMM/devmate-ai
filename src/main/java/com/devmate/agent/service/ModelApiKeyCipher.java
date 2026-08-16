package com.devmate.agent.service;

import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ModelApiKeyCipher {
    private static final String VERSION = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String encryptionSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public ModelApiKeyCipher(@Value("${devmate.model-connections.encryption-secret:}") String encryptionSecret) {
        this.encryptionSecret = encryptionSecret;
    }

    public String encrypt(Long userId, String provider, String apiKey) {
        requireEncryptionSecret();
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(userId, provider));
            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            return VERSION + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "API Key加密失败");
        }
    }

    public String decrypt(Long userId, String provider, String encryptedApiKey) {
        requireEncryptionSecret();
        if (!StringUtils.hasText(encryptedApiKey) || !encryptedApiKey.startsWith(VERSION)) {
            throw unreadableKey();
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedApiKey.substring(VERSION.length()));
            if (payload.length <= IV_BYTES) throw unreadableKey();
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(userId, provider));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (BusinessException exception) {
            throw exception;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw unreadableKey();
        }
    }

    private SecretKeySpec key() throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(encryptionSecret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    private byte[] aad(Long userId, String provider) {
        return (userId + ":" + provider).getBytes(StandardCharsets.UTF_8);
    }

    private void requireEncryptionSecret() {
        if (!StringUtils.hasText(encryptionSecret) || encryptionSecret.length() < 32) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "服务端未配置模型密钥加密密钥");
        }
    }

    private BusinessException unreadableKey() {
        return new BusinessException(ErrorCode.CONFLICT, "已保存的API Key无法解密，请重新填写");
    }
}
