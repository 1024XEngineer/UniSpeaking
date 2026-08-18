package com.unispeaking.provider.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unispeaking.common.exception.BusinessException;
import java.util.Base64;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcEncryptedAiProviderCredentialStoreTest {

	@Test
	void encryptsCredentialAtRestAndExposesOnlyItsFingerprint() {
		JdbcTemplate jdbc = database("credential-encryption");
		String key = Base64.getEncoder().encodeToString(new byte[32]);
		var store = new JdbcEncryptedAiProviderCredentialStore(jdbc, key);
		String secret = "provider-secret-value";

		var status = store.replace("qwen", secret);

		String persisted = jdbc.queryForObject(
				"select secret_ciphertext from ai_providers where provider_id='qwen'", String.class);
		assertThat(persisted).isNotEqualTo(secret).doesNotContain(secret);
		assertThat(status.configured()).isTrue();
		assertThat(status.fingerprint()).startsWith("sha256:");
		assertThat(store.status("qwen").fingerprint()).isEqualTo(status.fingerprint());
		assertThat(store.credentialOrFallback("qwen", "fallback")).isEqualTo(secret);
	}

	@Test
	void replacesTheExistingCredentialImmediately() {
		JdbcTemplate jdbc = database("credential-replacement");
		String key = Base64.getEncoder().encodeToString(new byte[32]);
		var store = new JdbcEncryptedAiProviderCredentialStore(jdbc, key);

		var first = store.replace("qwen", "first-provider-secret");
		var replacement = store.replace("qwen", "replacement-provider-secret");

		assertThat(replacement.fingerprint()).isNotEqualTo(first.fingerprint());
		assertThat(store.credentialOrFallback("qwen", "fallback"))
				.isEqualTo("replacement-provider-secret");
	}

	@Test
	void rejectsDatabaseCredentialWritesWithoutAMasterKey() {
		var store = new JdbcEncryptedAiProviderCredentialStore(database("credential-no-key"), "");

		assertThatThrownBy(() -> store.replace("qwen", "provider-secret-value"))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo("AI_CREDENTIAL_MASTER_KEY_REQUIRED");
	}

	private static JdbcTemplate database(String name) {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("create table ai_providers (provider_id varchar(64) primary key, "
				+ "secret_ciphertext varchar(10000), secret_fingerprint varchar(32), "
				+ "updated_at timestamp with time zone not null)");
		jdbc.update("insert into ai_providers (provider_id, updated_at) values ('qwen', current_timestamp)");
		return jdbc;
	}
}
