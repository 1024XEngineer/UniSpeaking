package com.unispeaking.provider.config;

import com.unispeaking.common.exception.BusinessException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public final class JdbcEncryptedAiProviderCredentialStore implements AiProviderCredentialStore {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int IV_BYTES = 12;
	private static final int MAX_VALUE_LENGTH = 4096;
	private final JdbcTemplate jdbc;
	private final ObjectMapper objectMapper;
	private final byte[] masterKey;

	public JdbcEncryptedAiProviderCredentialStore(
			JdbcTemplate jdbc,
			ObjectMapper objectMapper,
			@Value("${AI_PROVIDER_CREDENTIAL_MASTER_KEY:}") String configuredKey) {
		this.jdbc = jdbc;
		this.objectMapper = objectMapper;
		this.masterKey = decodeKey(configuredKey);
	}

	@Override
	public String credentialOrFallback(String providerId, String fallback) {
		String primaryField = AiProviderCredentialSchema.definition(providerId).primaryField();
		return credentialOrFallback(providerId, primaryField, fallback);
	}

	@Override
	public String credentialOrFallback(String providerId, String field, String fallback) {
		return credentialsOrFallback(providerId, Map.of())
				.getOrDefault(field, fallback);
	}

	@Override
	public Map<String, String> credentialsOrFallback(
			String providerId, Map<String, String> fallback) {
		Map<String, String> resolved = new LinkedHashMap<>();
		if (fallback != null) resolved.putAll(fallback);
		if (masterKey == null) return Map.copyOf(resolved);
		try {
			StoredCredential stored = stored(providerId);
			if (stored != null && stored.ciphertext() != null) {
				resolved.putAll(decode(providerId, decrypt(providerId, stored.ciphertext())));
			}
			return Map.copyOf(resolved);
		}
		catch (DataAccessException exception) {
			return Map.copyOf(resolved);
		}
	}

	@Override
	public CredentialStatus status(String providerId) {
		var definition = AiProviderCredentialSchema.definition(providerId);
		try {
			StoredCredential stored = stored(providerId);
			if (stored == null || stored.ciphertext() == null) {
				return status(definition, Map.of(), null, false);
			}
			if (masterKey == null) {
				return status(definition, Map.of(), stored.fingerprint(), true);
			}
			Map<String, String> values = decode(providerId, decrypt(providerId, stored.ciphertext()));
			return status(definition, values, stored.fingerprint(), !values.isEmpty());
		}
		catch (DataAccessException exception) {
			return status(definition, Map.of(), null, false);
		}
	}

	@Override
	public CredentialStatus replace(String providerId, Map<String, String> changes) {
		if (masterKey == null) {
			throw new BusinessException("AI_CREDENTIAL_MASTER_KEY_REQUIRED",
					"请先配置 AI_PROVIDER_CREDENTIAL_MASTER_KEY");
		}
		var definition = AiProviderCredentialSchema.definition(providerId);
		StoredCredential stored = storedForUpdate(providerId);
		if (stored == null) {
			throw new BusinessException("AI_PROVIDER_NOT_FOUND", "Provider 不存在: " + providerId);
		}
		Map<String, String> values = new LinkedHashMap<>();
		if (stored.ciphertext() != null) {
			values.putAll(decode(providerId, decrypt(providerId, stored.ciphertext())));
		}
		Map<String, String> normalized = normalizeChanges(definition, changes);
		if (normalized.isEmpty()) {
			throw new BusinessException("AI_CREDENTIAL_INVALID", "请至少填写一个需要更新的配置项");
		}
		values.putAll(normalized);
		boolean legacyOnlyUpdate = definition.field(definition.primaryField()) == null
				&& normalized.size() == 1
				&& normalized.containsKey(definition.primaryField());
		if (!legacyOnlyUpdate) validateRequired(definition, values);
		String plaintext = encode(values);
		String fingerprint = fingerprint(plaintext);
		String ciphertext = encrypt(providerId, plaintext);
		jdbc.update("update ai_providers set secret_ciphertext=?, secret_fingerprint=?, "
				+ "config_version=config_version+1, updated_at=current_timestamp where provider_id=?",
				ciphertext, fingerprint, providerId);
		return status(definition, values, fingerprint, true);
	}

	private StoredCredential stored(String providerId) {
		List<StoredCredential> values = jdbc.query(
				"select secret_ciphertext, secret_fingerprint from ai_providers where provider_id=?",
				(rs, row) -> new StoredCredential(rs.getString(1), rs.getString(2)), providerId);
		return values.isEmpty() ? null : values.getFirst();
	}

	private StoredCredential storedForUpdate(String providerId) {
		List<StoredCredential> values = jdbc.query(
				"select secret_ciphertext, secret_fingerprint from ai_providers where provider_id=? for update",
				(rs, row) -> new StoredCredential(rs.getString(1), rs.getString(2)), providerId);
		return values.isEmpty() ? null : values.getFirst();
	}

	private CredentialStatus status(
			AiProviderCredentialSchema.ProviderDefinition definition,
			Map<String, String> values,
			String fingerprint,
			boolean configured) {
		List<CredentialFieldStatus> fields = definition.fields().stream().map(field -> {
			String value = values.get(field.key());
			boolean fieldConfigured = value != null && !value.isBlank();
			return new CredentialFieldStatus(
					field.key(), field.label(), field.required(), field.secret(), field.description(),
					fieldConfigured, fieldConfigured ? fingerprint(value) : null);
		}).toList();
		return new CredentialStatus(configured, fingerprint, masterKey != null, fields);
	}

	private Map<String, String> normalizeChanges(
			AiProviderCredentialSchema.ProviderDefinition definition,
			Map<String, String> changes) {
		Map<String, String> normalized = new LinkedHashMap<>();
		if (changes == null) return normalized;
		changes.forEach((key, supplied) -> {
			var field = definition.field(key);
			if (field == null && !definition.primaryField().equals(key)) {
				throw new BusinessException("AI_CREDENTIAL_FIELD_INVALID", "不支持的配置项: " + key);
			}
			String value = supplied == null ? "" : supplied.trim();
			if (value.isBlank()) return;
			if (value.length() > MAX_VALUE_LENGTH) {
				String label = field == null ? "Provider 密钥" : field.label();
				throw new BusinessException("AI_CREDENTIAL_INVALID", label + " 长度无效");
			}
			normalized.put(key, value);
		});
		return normalized;
	}

	private void validateRequired(
			AiProviderCredentialSchema.ProviderDefinition definition,
			Map<String, String> values) {
		List<String> missing = definition.fields().stream()
				.filter(AiProviderCredentialSchema.FieldDefinition::required)
				.filter(field -> values.getOrDefault(field.key(), "").isBlank())
				.map(AiProviderCredentialSchema.FieldDefinition::label)
				.toList();
		if (!missing.isEmpty()) {
			throw new BusinessException("AI_CREDENTIAL_REQUIRED", "请完整填写: " + String.join("、", missing));
		}
	}

	private String encode(Map<String, String> values) {
		try {
			return objectMapper.writeValueAsString(
					Map.of("version", 1, "credentials", new TreeMap<>(values)));
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("AI credential serialization failed", exception);
		}
	}

	private Map<String, String> decode(String providerId, String plaintext) {
		try {
			JsonNode root = objectMapper.readTree(plaintext);
			JsonNode credentials = root == null ? null : root.path("credentials");
			if (credentials != null && credentials.isObject()) {
				Map<String, String> values = new LinkedHashMap<>();
				credentials.properties().forEach(entry -> {
					if (entry.getValue().isTextual() && !entry.getValue().asString("").isBlank()) {
						values.put(entry.getKey(), entry.getValue().asString().trim());
					}
				});
				return Map.copyOf(values);
			}
		}
		catch (JacksonException ignored) {
			// Legacy rows contain the encrypted credential directly, not JSON.
		}
		String legacy = plaintext == null ? "" : plaintext.trim();
		if (legacy.isBlank()) return Map.of();
		return Map.of(AiProviderCredentialSchema.definition(providerId).primaryField(), legacy);
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
			throw new BusinessException("AI_CREDENTIAL_DECRYPTION_FAILED", "Provider 配置无法解密");
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

	private record StoredCredential(String ciphertext, String fingerprint) {}
}
