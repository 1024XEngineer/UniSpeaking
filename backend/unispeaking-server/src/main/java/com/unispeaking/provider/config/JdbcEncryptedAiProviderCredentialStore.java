package com.unispeaking.provider.config;

import com.unispeaking.common.exception.BusinessException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcEncryptedAiProviderCredentialStore implements AiProviderCredentialStore {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int IV_BYTES = 12;
	private final JdbcTemplate jdbc;
	private final byte[] masterKey;

	public JdbcEncryptedAiProviderCredentialStore(
			JdbcTemplate jdbc,
			@Value("${AI_PROVIDER_CREDENTIAL_MASTER_KEY:}") String configuredKey) {
		this.jdbc = jdbc;
		this.masterKey = decodeKey(configuredKey);
	}

	@Override
	public String credentialOrFallback(String providerId, String fallback) {
		if (masterKey == null) return fallback;
		try {
			List<String> values = jdbc.query(
					"select secret_ciphertext from ai_providers where provider_id=? and secret_ciphertext is not null",
					(rs, row) -> rs.getString(1), providerId);
			return values.isEmpty() ? fallback : decrypt(providerId, values.getFirst());
		}
		catch (DataAccessException exception) {
			return fallback;
		}
	}

	@Override
	public CredentialStatus status(String providerId) {
		try {
			List<String> fingerprints = jdbc.query(
					"select secret_fingerprint from ai_providers where provider_id=? and secret_fingerprint is not null",
					(rs, row) -> rs.getString(1), providerId);
			return new CredentialStatus(!fingerprints.isEmpty(), fingerprints.isEmpty() ? null : fingerprints.getFirst(), masterKey != null);
		}
		catch (DataAccessException exception) {
			return new CredentialStatus(false, null, masterKey != null);
		}
	}

	@Override
	public CredentialStatus replace(String providerId, String plaintext) {
		if (masterKey == null) {
			throw new BusinessException("AI_CREDENTIAL_MASTER_KEY_REQUIRED", "请先配置 AI_PROVIDER_CREDENTIAL_MASTER_KEY");
		}
		String secret = plaintext == null ? "" : plaintext.trim();
		if (secret.length() < 8 || secret.length() > 4096) {
			throw new BusinessException("AI_CREDENTIAL_INVALID", "Provider 密钥长度无效");
		}
		String fingerprint = fingerprint(secret);
		String ciphertext = encrypt(providerId, secret);
		if (jdbc.update("update ai_providers set secret_ciphertext=?, secret_fingerprint=?, "
				+ "updated_at=current_timestamp where provider_id=?",
				ciphertext, fingerprint, providerId) == 0) {
			throw new BusinessException("AI_PROVIDER_NOT_FOUND", "Provider 不存在: " + providerId);
		}
		return new CredentialStatus(true, fingerprint, true);
	}

	private String encrypt(String providerId, String value) {
		try {
			byte[] iv = new byte[IV_BYTES];
			RANDOM.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(128, iv));
			cipher.updateAAD(providerId.getBytes(StandardCharsets.UTF_8));
			byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
		}
		catch (Exception exception) {
			throw new IllegalStateException("AI credential encryption failed", exception);
		}
	}

	private String decrypt(String providerId, String value) {
		try {
			byte[] payload = Base64.getDecoder().decode(value);
			ByteBuffer buffer = ByteBuffer.wrap(payload);
			byte[] iv = new byte[IV_BYTES];
			buffer.get(iv);
			byte[] encrypted = new byte[buffer.remaining()];
			buffer.get(encrypted);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(128, iv));
			cipher.updateAAD(providerId.getBytes(StandardCharsets.UTF_8));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		}
		catch (Exception exception) {
			throw new BusinessException("AI_CREDENTIAL_DECRYPTION_FAILED", "Provider 密钥无法解密");
		}
	}

	private static byte[] decodeKey(String configured) {
		if (configured == null || configured.isBlank()) return null;
		try {
			byte[] decoded = Base64.getDecoder().decode(configured.trim());
			if (decoded.length != 32) throw new IllegalArgumentException();
			return decoded;
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalStateException("AI_PROVIDER_CREDENTIAL_MASTER_KEY must be a Base64 encoded 32-byte key");
		}
	}

	private static String fingerprint(String secret) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
			return "sha256:" + java.util.HexFormat.of().formatHex(digest, 0, 6);
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

}
