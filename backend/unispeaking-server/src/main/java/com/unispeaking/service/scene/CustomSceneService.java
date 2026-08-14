package com.unispeaking.service.scene;

import com.unispeaking.common.util.SceneIdGenerator;
import com.unispeaking.component.scene.CustomSceneGenerator;
import com.unispeaking.domain.dto.scene.CustomSceneGenerationResponse;
import com.unispeaking.domain.dto.scene.CustomDialogueSceneContext;
import com.unispeaking.domain.dto.scene.CustomSceneRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.SceneNotFoundException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.profile.ProfileService;
import com.unispeaking.common.prompt.FiveLayerPromptBuilder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class CustomSceneService {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			CustomSceneService.class);

	private final AuthService authService;
	private final ProfileService profileService;
	private final SceneRepository sceneRepository;
	private final FiveLayerPromptBuilder promptService;
	private final CustomSceneGenerator customSceneGenerator;
	private final AiProviderRegistry providerRegistry;
	private final ObjectMapper objectMapper;

	public CustomSceneService(
			AuthService authService,
			ProfileService profileService,
			SceneRepository sceneRepository,
			FiveLayerPromptBuilder promptService,
			CustomSceneGenerator customSceneGenerator,
			AiProviderRegistry providerRegistry,
			ObjectMapper objectMapper) {
		this.authService = authService;
		this.profileService = profileService;
		this.sceneRepository = sceneRepository;
		this.promptService = promptService;
		this.customSceneGenerator = customSceneGenerator;
		this.providerRegistry = providerRegistry;
		this.objectMapper = objectMapper;
	}
	public CustomSceneGenerationResponse generate(
			CustomSceneRequest request) {
		String userId = authService.requireUserId(request.userId());
		SceneConfig config = sceneRepository.findByType(SceneType.CUSTOM_SCENE)
				.orElseThrow(() -> new SceneNotFoundException(
						SceneType.CUSTOM_SCENE.name()));
		UserProfile profile = profileService.getProfile(userId);
		SceneGenerationResponse generated = generateCustomScene(
				SceneIdGenerator.generate(SceneType.CUSTOM_SCENE),
				userId,
				request.sceneInput() == null ? "" : request.sceneInput().trim(),
				request.userPreference(),
				profile,
				config);
		CustomSceneDefinition definition = sceneRepository
				.findCustomDefinitionById(generated.sceneId())
				.orElseThrow(() -> new BusinessException(
						"CUSTOM_SCENE_NOT_FOUND",
						"生成的自定义场景不存在"));
		return new CustomSceneGenerationResponse(
				generated.sceneId(),
				definition.title(),
				definition.label(),
				definition.background(),
				definition.aiRole(),
				definition.userRole(),
				definition.learningGoal(),
				estimatedMinutes(definition.successFactorJson()),
				generated.wordList(),
				generated.phraseList(),
				generated.sentenceList(),
				generated.scenePrompt());
	}
	public byte[] synthesizeSpeech(String sceneId, String text, String model) {
		requireOwnedCustomScene(sceneId);
		if (text == null || text.isBlank()) {
			throw new BusinessException("TTS_TEXT_REQUIRED", "朗读文本不能为空");
		}
		byte[] audio = model == null || model.isBlank()
				? providerRegistry.generateSpeechAudioBytes(text.strip(), null)
				: providerRegistry.generateSpeechAudioBytes(model, text.strip(), null);
		if (audio == null || audio.length == 0) {
			throw new BusinessException("TTS_AUDIO_EMPTY", "TTS 未返回音频");
		}
		return audio;
	}
	public TranslateTextResponse translate(String sceneId, String text) {
		requireOwnedCustomScene(sceneId);
		String source = requireTranslationText(text);
		String prompt = """
				Translate the text enclosed in <source> into natural Simplified Chinese.
				Preserve the original meaning, tone, names, numbers, and punctuation.
				Return only the translation. Do not explain, annotate, or quote the source.

				<source>
				%s
				</source>
				""".formatted(source);
		String translated = providerRegistry.executeLlmTask(prompt, null);
		if (translated == null || translated.isBlank()) {
			throw new BusinessException("TRANSLATION_EMPTY", "翻译模型没有返回有效文本");
		}
		return new TranslateTextResponse(source, translated.strip(), "zh-CN");
	}
	public CustomSceneDefinition getOwnedDefinition(String sceneId) {
		return requireOwnedCustomScene(sceneId);
	}
	public SceneGenerationResponse getGeneratedScene(String sceneId) {
		requireOwnedCustomScene(sceneId);
		return sceneRepository.findGeneratedById(sceneId)
				.orElseThrow(() -> new BusinessException(
						"CUSTOM_SCENE_NOT_FOUND",
						"自定义场景不存在"));
	}
	public CustomDialogueSceneContext prepareDialogue(String sceneId) {
		CustomSceneDefinition definition = requireOwnedCustomScene(sceneId);
		SceneGenerationResponse generated = sceneRepository
				.findGeneratedById(sceneId)
				.orElseThrow(() -> new BusinessException(
						"CUSTOM_SCENE_NOT_FOUND",
						"自定义场景不存在"));
		String prompt = resolvePrompt(generated, definition, definition.userId());
		return new CustomDialogueSceneContext(
				definition.userId(),
				definition.sceneId(),
				definition.title(),
				definition.learningGoal(),
				definition.successFactorJson(),
				generated,
				prompt);
	}


	private SceneGenerationResponse generateCustomScene(
			String sceneId,
			String userId,
			String sceneInput,
			String userPreference,
			UserProfile profile,
			SceneConfig sceneConfig) {
		long totalStartedAt = System.nanoTime();
		long generationStartedAt = System.nanoTime();
		CustomSceneDefinition definition = customSceneGenerator.generate(
				sceneId,
				userId,
				sceneInput,
				userPreference,
				profile);
		long generationMillis = elapsedMillis(generationStartedAt);
		long promptStartedAt = System.nanoTime();
		String scenePrompt = String.join("\n\n", promptService.compose(
				profile,
				sceneConfig,
				SceneType.CUSTOM_SCENE,
				sceneInput,
				userPreference,
				definition.wordList(),
				definition.phraseList(),
				definition.sentenceList(),
				definition));
		long promptMillis = elapsedMillis(promptStartedAt);
		SceneGenerationResponse response = new SceneGenerationResponse(
				sceneId,
				definition.wordList(),
				definition.phraseList(),
				definition.sentenceList(),
				scenePrompt);
		long persistenceStartedAt = System.nanoTime();
		SceneGenerationResponse saved = sceneRepository.saveCustomScene(definition, response);
		LOGGER.info(
				"custom scene ready sceneId={} generationMs={} promptMs={} persistenceMs={} totalMs={}",
				sceneId,
				generationMillis,
				promptMillis,
				elapsedMillis(persistenceStartedAt),
				elapsedMillis(totalStartedAt));
		return saved;
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}

	private CustomSceneDefinition requireOwnedCustomScene(String sceneId) {
		String userId = authService.requireUserId(null);
		CustomSceneDefinition definition = sceneRepository
				.findCustomDefinitionById(sceneId)
				.orElseThrow(() -> new BusinessException(
						"CUSTOM_SCENE_NOT_FOUND",
						"自定义场景不存在"));
		if (!userId.equals(definition.userId())) {
			throw new BusinessException(
					"CUSTOM_SCENE_ACCESS_DENIED",
					"当前用户无权访问该场景");
		}
		return definition;
	}

	private String resolvePrompt(
			SceneGenerationResponse scene,
			CustomSceneDefinition definition,
			String userId) {
		if (scene.scenePrompt() != null && !scene.scenePrompt().isBlank()) {
			return scene.scenePrompt();
		}
		return String.join("\n\n", promptService.compose(
				profileService.getProfile(userId),
				sceneRepository.findByType(SceneType.CUSTOM_SCENE).orElse(null),
				SceneType.CUSTOM_SCENE,
				definition.title(),
				"",
				scene.wordList(),
				scene.phraseList(),
				scene.sentenceList(),
				definition));
	}

	private String requireTranslationText(String text) {
		if (text == null || text.isBlank()) {
			throw new BusinessException("TRANSLATION_TEXT_REQUIRED", "待翻译文本不能为空");
		}
		String normalized = text.strip();
		if (normalized.length() > 4000) {
			throw new BusinessException("TRANSLATION_TEXT_TOO_LONG", "待翻译文本不能超过4000个字符");
		}
		return normalized;
	}

	private int estimatedMinutes(String successFactorJson) {
		try {
			JsonNode root = objectMapper.readTree(successFactorJson);
			int value = root.path("estimated_minutes").intValue();
			return value >= 3 && value <= 10 ? value : 6;
		}
		catch (RuntimeException exception) {
			return 6;
		}
	}

}
