package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.prompt.FiveLayerPromptBuilder;
import com.unispeaking.component.scene.CustomSceneGenerator;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.profile.ProfileService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CustomSceneServiceTest {

	private static final String USER_ID = "11111111-1111-4111-8111-111111111111";
	private final AuthService authService = mock(AuthService.class);
	private final ProfileService profileService = mock(ProfileService.class);
	private final SceneRepository repository = mock(SceneRepository.class);
	private final FiveLayerPromptBuilder promptBuilder = mock(FiveLayerPromptBuilder.class);
	private final AiProviderRegistry providers = mock(AiProviderRegistry.class);
	private final CustomSceneService service = new CustomSceneService(
			authService, profileService, repository, promptBuilder,
			mock(CustomSceneGenerator.class), providers, new ObjectMapper());

	@BeforeEach
	void authenticate() {
		when(authService.requireUserId(null)).thenReturn(USER_ID);
	}

	@Test
	void speechRequiresOwnershipAndNonBlankText() {
		when(repository.findCustomDefinitionById("missing")).thenReturn(Optional.empty());
		assertCode("CUSTOM_SCENE_NOT_FOUND", () -> service.synthesizeSpeech("missing", "hello", null));

		CustomSceneDefinition foreign = definition("foreign", "another-user", "{}");
		when(repository.findCustomDefinitionById("foreign")).thenReturn(Optional.of(foreign));
		assertCode("CUSTOM_SCENE_ACCESS_DENIED", () -> service.synthesizeSpeech("foreign", "hello", null));

		when(repository.findCustomDefinitionById("owned")).thenReturn(Optional.of(definition("owned", USER_ID, "{}")));
		assertCode("TTS_TEXT_REQUIRED", () -> service.synthesizeSpeech("owned", "  ", null));
	}

	@Test
	void speechUsesProfileVoiceAndRejectsEmptyProviderAudio() {
		CustomSceneDefinition definition = definition("owned", USER_ID, "{}");
		when(repository.findCustomDefinitionById("owned")).thenReturn(Optional.of(definition));
		when(profileService.getProfile(USER_ID)).thenReturn(profile("Tina"));
		when(providers.generateSpeechAudioBytes(AiProviderRegistry.QWEN_TTS, "hello", null, "Tina"))
				.thenReturn(new byte[] {1, 2});

		assertArrayEquals(new byte[] {1, 2}, service.synthesizeSpeech("owned", " hello ", "ignored"));
		verify(providers).generateSpeechAudioBytes(AiProviderRegistry.QWEN_TTS, "hello", null, "Tina");

		when(providers.generateSpeechAudioBytes(AiProviderRegistry.QWEN_TTS, "empty", null, "Tina"))
				.thenReturn(new byte[0]);
		assertCode("TTS_AUDIO_EMPTY", () -> service.synthesizeSpeech("owned", "empty", null));
	}

	@Test
	void translationNormalizesTextAndValidatesProviderOutput() {
		when(repository.findCustomDefinitionById("owned")).thenReturn(Optional.of(definition("owned", USER_ID, "{}")));
		assertCode("TRANSLATION_TEXT_REQUIRED", () -> service.translate("owned", " "));
		assertCode("TRANSLATION_TEXT_TOO_LONG", () -> service.translate("owned", "x".repeat(4001)));
		when(providers.executeLlmTask(any(String.class), eq(null))).thenReturn("  你好，世界。  ");

		var result = service.translate("owned", "  Hello, world.  ");

		assertEquals("Hello, world.", result.sourceText());
		assertEquals("你好，世界。", result.translatedText());
		assertEquals("zh-CN", result.targetLanguage());
		when(providers.executeLlmTask(any(String.class), eq(null))).thenReturn(" ");
		assertCode("TRANSLATION_EMPTY", () -> service.translate("owned", "hello"));
	}

	@Test
	void generatedSceneAndDialogueRequireOwnedPersistedData() {
		CustomSceneDefinition definition = definition("owned", USER_ID, "{not-json}");
		when(repository.findCustomDefinitionById("owned")).thenReturn(Optional.of(definition));
		when(repository.findGeneratedById("owned")).thenReturn(Optional.empty());
		assertCode("CUSTOM_SCENE_NOT_FOUND", () -> service.getGeneratedScene("owned"));

		SceneGenerationResponse generated = new SceneGenerationResponse(
				"owned", List.of(item("word")), List.of(), List.of(), "");
		when(repository.findGeneratedById("owned")).thenReturn(Optional.of(generated));
		when(profileService.getProfile(USER_ID)).thenReturn(profile("Tina"));
		when(promptBuilder.compose(any(), any(), eq(SceneType.CUSTOM_SCENE), eq(definition.title()), eq(""),
				any(), any(), any(), eq(definition))).thenReturn(List.of("rebuilt prompt"));

		var context = service.prepareDialogue("owned");

		assertEquals("rebuilt prompt", context.prompt());
		assertEquals("owned", service.getGeneratedScene("owned").sceneId());
	}

	private void assertCode(String expected, org.junit.jupiter.api.function.Executable action) {
		assertEquals(expected, assertThrows(BusinessException.class, action).code());
	}

	private CustomSceneDefinition definition(String sceneId, String userId, String factors) {
		return new CustomSceneDefinition(sceneId, userId, "酒店入住", "旅行", "酒店", "前台", "住客",
				"完成入住", "", factors, List.of(item("word")), List.of(), List.of());
	}

	private UserProfile profile(String voiceId) {
		return new UserProfile(USER_ID, "B", voiceId, "NATURAL", "zh-CN", "旅行");
	}

	private LearningContentItem item(String id) {
		return new LearningContentItem(id, "hello", "你好", "");
	}
}
