package com.unispeaking.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderCredentialOverrideTest {

	@Test
	void scopesMultipleCredentialFieldsToOneProviderInvocation() {
		String result = ProviderCredentialOverride.call(
				Map.of("apiKey", "dynamic-key", "workspaceId", "workspace-123"),
				() -> ProviderCredentialOverride.currentOr("apiKey", "fallback")
						+ ":" + ProviderCredentialOverride.currentOr("workspaceId", "fallback"));

		assertThat(result).isEqualTo("dynamic-key:workspace-123");
		assertThat(ProviderCredentialOverride.currentOr("apiKey", "fallback")).isEqualTo("fallback");
	}
}
