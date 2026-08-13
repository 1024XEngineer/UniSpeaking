package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.prompt.FiveLayerPromptBuilder;
import com.unispeaking.component.scene.CustomSceneGenerator;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.profile.ProfileService;
import com.unispeaking.service.scene.impl.CustomSceneServiceImpl;
import com.unispeaking.service.scene.impl.FreeChatSceneServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LlmTranslationRoutingTest {

	@Test
	void freeChatTranslationUsesTheConfiguredLlmRoute() {
		AuthService authService = mock(AuthService.class);
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTask(anyString(), isNull())).thenReturn("你好");
		FreeChatSceneServiceImpl service = new FreeChatSceneServiceImpl(
				authService,
				mock(ProfileService.class),
				mock(SceneRepository.class),
				mock(FiveLayerPromptBuilder.class),
				registry);

		var response = service.translate("Hello");

		assertEquals("你好", response.translatedText());
		verify(registry).executeLlmTask(anyString(), isNull());
	}

	@Test
	void customSceneTranslationUsesTheConfiguredLlmRoute() {
		String userId = "11111111-1111-4111-8111-111111111111";
		String sceneId = "custom_translation";
		AuthService authService = mock(AuthService.class);
		SceneRepository repository = mock(SceneRepository.class);
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(authService.requireUserId(null)).thenReturn(userId);
		when(repository.findCustomDefinitionById(sceneId)).thenReturn(Optional.of(
				new CustomSceneDefinition(
						sceneId,
						userId,
						"title",
						"background",
						"assistant",
						"learner",
						"goal",
						"instruction",
						"{}",
						List.of(),
						List.of(),
						List.of())));
		when(registry.executeLlmTask(anyString(), isNull())).thenReturn("你好");
		CustomSceneServiceImpl service = new CustomSceneServiceImpl(
				authService,
				mock(ProfileService.class),
				repository,
				mock(FiveLayerPromptBuilder.class),
				mock(CustomSceneGenerator.class),
				registry,
				new ObjectMapper());

		var response = service.translate(sceneId, "Hello");

		assertEquals("你好", response.translatedText());
		verify(registry).executeLlmTask(anyString(), isNull());
	}
}
