package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.prompt.FiveLayerPromptBuilder;
import com.unispeaking.domain.dto.scene.FreeChatSceneRequest;
import com.unispeaking.domain.dto.scene.FreeChatSceneResult;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.profile.ProfileService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FreeChatSceneServiceTest {

	@Test
	void preparesFreeChatWithTrimmedPromptAndGeneratesScene() {
		AuthService auth = mock(AuthService.class);
		ProfileService profiles = mock(ProfileService.class);
		SceneRepository scenes = mock(SceneRepository.class);
		FiveLayerPromptBuilder prompts = mock(FiveLayerPromptBuilder.class);
		AiProviderRegistry providers = mock(AiProviderRegistry.class);
		when(auth.requireUserId(null)).thenReturn("user-1");
		UserProfile profile = mock(UserProfile.class);
		SceneConfig config = mock(SceneConfig.class);
		when(profiles.getProfile("user-1")).thenReturn(profile);
		when(scenes.findByType(SceneType.FREE_CHAT)).thenReturn(Optional.of(config));
		when(prompts.compose(
				profile, config, SceneType.FREE_CHAT, "hello", null,
				List.of(), List.of(), List.of()))
				.thenReturn(List.of("system", "hello"));

		FreeChatSceneService service = new FreeChatSceneService(
				auth, profiles, scenes, prompts, providers);
		FreeChatSceneResult result = service.generate(new FreeChatSceneRequest("  hello  "));

		assertTrue(result.sceneId().startsWith("freechat_"));
		assertEquals("system\n\nhello", result.dialoguePrompt());
		verify(prompts).compose(
				profile, config, SceneType.FREE_CHAT, "hello", null,
				List.of(), List.of(), List.of());
	}

	@Test
	void preparesFreeChatWithNullRequestAndRejectsMissingConfiguration() {
		AuthService auth = mock(AuthService.class);
		SceneRepository scenes = mock(SceneRepository.class);
		when(auth.requireUserId(null)).thenReturn("user-1");
		when(scenes.findByType(SceneType.FREE_CHAT)).thenReturn(Optional.empty());
		FreeChatSceneService service = new FreeChatSceneService(
				auth, mock(ProfileService.class), scenes,
				mock(FiveLayerPromptBuilder.class), mock(AiProviderRegistry.class));

		assertThrows(RuntimeException.class, () -> service.prepare(null));
	}

	@Test
	void translationValidatesInputAndStripsModelOutput() {
		AuthService auth = mock(AuthService.class);
		AiProviderRegistry providers = mock(AiProviderRegistry.class);
		when(providers.executeLlmTask(anyString(), isNull())).thenReturn("  你好  ");
		FreeChatSceneService service = new FreeChatSceneService(
				auth, mock(ProfileService.class), mock(SceneRepository.class),
				mock(FiveLayerPromptBuilder.class), providers);

		assertEquals("你好", service.translate("  Hello  ").translatedText());
		assertEquals("Hello", service.translate("  Hello  ").sourceText());
		assertEquals("TRANSLATION_TEXT_REQUIRED", assertThrows(
				BusinessException.class, () -> service.translate(" \t ")).code());
		assertEquals("TRANSLATION_TEXT_REQUIRED", assertThrows(
				BusinessException.class, () -> service.translate(null)).code());
		assertEquals("TRANSLATION_TEXT_TOO_LONG", assertThrows(
				BusinessException.class, () -> service.translate("x".repeat(4001))).code());
	}

	@Test
	void translationRejectsEmptyModelOutput() {
		AiProviderRegistry providers = mock(AiProviderRegistry.class);
		when(providers.executeLlmTask(anyString(), isNull())).thenReturn(" \n ");
		FreeChatSceneService service = new FreeChatSceneService(
			mock(AuthService.class), mock(ProfileService.class), mock(SceneRepository.class),
			mock(FiveLayerPromptBuilder.class), providers);

		BusinessException exception = assertThrows(
				BusinessException.class, () -> service.translate("Hello"));
		assertEquals("TRANSLATION_EMPTY", exception.code());
	}
}
