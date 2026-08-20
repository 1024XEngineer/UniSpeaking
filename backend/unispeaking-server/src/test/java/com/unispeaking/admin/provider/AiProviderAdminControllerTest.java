package com.unispeaking.admin.provider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AiProviderAdminControllerTest {

	@Test
	void forwardsStructuredCredentialValues() {
		AiProviderAdminService service = mock(AiProviderAdminService.class);
		var controller = new AiProviderAdminController(service);
		Map<String, String> values = Map.of("apiKey", "new-key", "workspaceId", "workspace-123");

		controller.replaceCredential(
				"qwen", new AiProviderAdminService.UpdateCredentialRequest(values, null));

		verify(service).replaceCredential("qwen", values);
	}

	@Test
	void keepsLegacySingleSecretRequestsCompatible() {
		AiProviderAdminService service = mock(AiProviderAdminService.class);
		var controller = new AiProviderAdminController(service);

		controller.replaceCredential(
				"deepseek", new AiProviderAdminService.UpdateCredentialRequest(null, "legacy-key"));

		verify(service).replaceCredential("deepseek", Map.of("apiKey", "legacy-key"));
	}
}
