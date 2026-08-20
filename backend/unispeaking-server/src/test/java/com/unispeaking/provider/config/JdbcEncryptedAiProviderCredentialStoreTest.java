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
