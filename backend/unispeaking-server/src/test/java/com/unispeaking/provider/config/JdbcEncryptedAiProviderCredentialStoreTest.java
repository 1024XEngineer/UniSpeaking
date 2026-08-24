package com.unispeaking.provider.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unispeaking.common.exception.BusinessException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class JdbcEncryptedAiProviderCredentialStoreTest {

	@Test
	void encryptsStructuredCredentialsAndExposesOnlyFieldFingerprints() {
		JdbcTemplate jdbc = database("credential-encryption");
		String key = key();
		var store = store(jdbc, key);
		Map<String, String> values = Map.of(
				"apiKey", "provider-secret-value",
				"workspaceId", "workspace-123");

		var status = store.replace("qwen", values);

		String persisted = jdbc.queryForObject(
				"select secret_ciphertext from ai_providers where provider_id='qwen'", String.class);
		assertThat(persisted).doesNotContain("provider-secret-value", "workspace-123");
		assertThat(status.configured()).isTrue();
		assertThat(status.fields()).extracting(AiProviderCredentialStore.CredentialFieldStatus::key)
				.containsExactly("apiKey", "workspaceId");
		assertThat(status.fields()).allSatisfy(field -> {
			assertThat(field.configured()).isTrue();
			assertThat(field.fingerprint()).startsWith("sha256:");
		});
		assertThat(status.toString()).doesNotContain("provider-secret-value", "workspace-123");
		assertThat(store.credentialsOrFallback("qwen", Map.of())).containsAllEntriesOf(values);
	}

	@Test
	void mergesChangedFieldsWithoutErasingExistingValues() {
		var store = store(database("credential-merge"), key());
		store.replace("qwen", Map.of("apiKey", "first-provider-secret"));

		store.replace("qwen", Map.of("workspaceId", "workspace-next"));

		assertThat(store.credentialsOrFallback("qwen", Map.of()))
				.containsEntry("apiKey", "first-provider-secret")
				.containsEntry("workspaceId", "workspace-next");
	}

	@Test
	void readsLegacySingleSecretAsTheProviderPrimaryField() throws Exception {
		JdbcTemplate jdbc = database("credential-legacy");
		String key = key();
		jdbc.update("update ai_providers set secret_ciphertext=?, secret_fingerprint=? where provider_id='qwen'",
				encryptLegacy("qwen", "legacy-provider-secret", key), "sha256:legacy");
		var store = store(jdbc, key);

		assertThat(store.credentialOrFallback("qwen", "fallback"))
				.isEqualTo("legacy-provider-secret");
		assertThat(store.status("qwen").fields()).anySatisfy(field -> {
			assertThat(field.key()).isEqualTo("apiKey");
			assertThat(field.configured()).isTrue();
		});
	}

	@Test
	void rejectsIncompleteRequiredProviderConfiguration() {
		var store = store(database("credential-required"), key());

		assertThatThrownBy(() -> store.replace("aliyun", Map.of("apiKey", "provider-api-key")))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo("AI_CREDENTIAL_REQUIRED");
	}

	@Test
	void rejectsDatabaseCredentialWritesWithoutAMasterKey() {
		var store = store(database("credential-no-key"), "");

		assertThatThrownBy(() -> store.replace("qwen", Map.of("apiKey", "provider-secret-value")))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo("AI_CREDENTIAL_MASTER_KEY_REQUIRED");
	}

	@Test
	void usesFallbacksWhenTheMasterKeyIsMissing() {
		var store = store(database("credential-fallback-no-key"), "");

		assertThat(store.credentialOrFallback("qwen", "fallback")).isEqualTo("fallback");
		assertThat(store.credentialsOrFallback("qwen", Map.of("apiKey", "fallback")))
				.containsEntry("apiKey", "fallback");
		assertThat(store.status("qwen")).satisfies(status -> {
			assertThat(status.configured()).isFalse();
			assertThat(status.writable()).isFalse();
			assertThat(status.fingerprint()).isNull();
		});
	}

	@Test
	void reportsMissingRowsAndDatabaseFailuresAsUnconfiguredOrFallback() {
		var store = store(database("credential-missing-row"), key());
		assertThat(store.status("deepseek").configured()).isFalse();
		assertThat(store.credentialsOrFallback("deepseek", Map.of("apiKey", "fallback")))
				.containsEntry("apiKey", "fallback");

		var brokenJdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
		org.mockito.Mockito.when(brokenJdbc.query(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
				org.mockito.ArgumentMatchers.any(Object[].class)))
				.thenThrow(new org.springframework.dao.DataAccessResourceFailureException("database down"));
		var broken = store(brokenJdbc, key());
		assertThat(broken.credentialsOrFallback("qwen", Map.of("apiKey", "fallback")))
				.containsEntry("apiKey", "fallback");
		assertThat(broken.status("qwen").configured()).isFalse();
	}

	@Test
	void validatesProviderFieldsBlankUnknownAndOversizedChanges() {
		var store = store(database("credential-validation"), key());
		assertThatThrownBy(() -> store.replace("qwen", Map.of("unsupported", "value")))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo("AI_CREDENTIAL_FIELD_INVALID");
		assertThatThrownBy(() -> store.replace("qwen", Map.of("apiKey", " ")))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo("AI_CREDENTIAL_INVALID");
		assertThatThrownBy(() -> store.replace("qwen", Map.of("apiKey", "x".repeat(4097))))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo("AI_CREDENTIAL_INVALID");
	}

	@Test
	void rejectsEmptyChangesUnknownProvidersInvalidMasterKeysAndBadCiphertext() {
		var store = store(database("credential-invalid-inputs"), key());
		assertThatThrownBy(() -> store.replace("qwen", Map.of()))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo("AI_CREDENTIAL_INVALID");
		assertThatThrownBy(() -> store.replace("not-a-provider", Map.of("apiKey", "secret")))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo("AI_PROVIDER_CREDENTIAL_SCHEMA_MISSING");
		assertThatThrownBy(() -> store(database("credential-bad-key"), "not-base64"))
				.isInstanceOf(IllegalStateException.class);

		JdbcTemplate jdbc = database("credential-bad-cipher");
		jdbc.update("update ai_providers set secret_ciphertext='not-valid-ciphertext' where provider_id='qwen'");
		var corrupted = store(jdbc, key());
		assertThatThrownBy(() -> corrupted.credentialsOrFallback("qwen", Map.of()))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo("AI_CREDENTIAL_DECRYPTION_FAILED");
	}

	private static JdbcEncryptedAiProviderCredentialStore store(JdbcTemplate jdbc, String key) {
		return new JdbcEncryptedAiProviderCredentialStore(jdbc, new ObjectMapper(), key);
	}

	private static String key() {
		return Base64.getEncoder().encodeToString(new byte[32]);
	}

	private static String encryptLegacy(String providerId, String value, String configuredKey) throws Exception {
		byte[] iv = new byte[12];
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE,
				new SecretKeySpec(Base64.getDecoder().decode(configuredKey), "AES"),
				new GCMParameterSpec(128, iv));
		cipher.updateAAD(providerId.getBytes(StandardCharsets.UTF_8));
		byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
		return Base64.getEncoder().encodeToString(
				ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
	}

	private static JdbcTemplate database(String name) {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("create table ai_providers (provider_id varchar(64) primary key, "
				+ "secret_ciphertext varchar(10000), secret_fingerprint varchar(32), "
				+ "config_version bigint not null default 1, updated_at timestamp with time zone not null)");
		jdbc.update("insert into ai_providers (provider_id, updated_at) values ('qwen', current_timestamp)");
		jdbc.update("insert into ai_providers (provider_id, updated_at) values ('aliyun', current_timestamp)");
		return jdbc;
	}
}
