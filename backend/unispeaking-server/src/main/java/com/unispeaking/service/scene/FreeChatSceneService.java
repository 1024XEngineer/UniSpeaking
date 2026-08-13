package com.unispeaking.service.scene;

import com.unispeaking.common.exception.SceneNotFoundException;
import com.unispeaking.common.prompt.FiveLayerPromptBuilder;
import com.unispeaking.common.util.SceneIdGenerator;
import com.unispeaking.domain.dto.scene.FreeChatSceneRequest;
import com.unispeaking.domain.dto.scene.FreeChatSceneResult;
import com.unispeaking.domain.dto.scene.FreeChatSceneContext;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.profile.ProfileService;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FreeChatSceneService {

	private final AuthService authService;
	private final ProfileService profileService;
	private final SceneRepository sceneRepository;
	private final FiveLayerPromptBuilder promptBuilder;
	private final AiProviderRegistry providerRegistry;

	public FreeChatSceneService(
			AuthService authService,
			ProfileService profileService,
			SceneRepository sceneRepository,
			FiveLayerPromptBuilder promptBuilder,
			AiProviderRegistry providerRegistry) {
		this.authService = authService;
		this.profileService = profileService;
		this.sceneRepository = sceneRepository;
		this.promptBuilder = promptBuilder;
		this.providerRegistry = providerRegistry;
	}
	public FreeChatSceneResult generate(FreeChatSceneRequest request) {
		return prepare(request).scene();
	}
	public FreeChatSceneContext prepare(FreeChatSceneRequest request) {
		String userId = authService.requireUserId(null);
		UserProfile profile = profileService.getProfile(userId);
		SceneConfig config = sceneRepository.findByType(SceneType.FREE_CHAT)
				.orElseThrow(() -> new SceneNotFoundException(
						SceneType.FREE_CHAT.name()));
		String input = request == null || request.prompt() == null
				? ""
				: request.prompt().trim();
		String prompt = String.join("\n\n", promptBuilder.compose(
				profile,
				config,
				SceneType.FREE_CHAT,
				input,
				null,
				List.of(),
				List.of(),
				List.of()));
		return new FreeChatSceneContext(
				userId,
				new FreeChatSceneResult(
						SceneIdGenerator.generate(SceneType.FREE_CHAT),
						prompt));
	}
	public TranslateTextResponse translate(String text) {
		authService.requireUserId(null);
		if (text == null || text.isBlank()) {
			throw new BusinessException(
					"TRANSLATION_TEXT_REQUIRED",
					"待翻译文本不能为空");
		}
		String source = text.strip();
		if (source.length() > 4000) {
			throw new BusinessException(
					"TRANSLATION_TEXT_TOO_LONG",
					"待翻译文本不能超过4000个字符");
		}
		String prompt = """
				Translate the text enclosed in <source> into natural Simplified Chinese.
				Preserve the original meaning, tone, names, numbers, and punctuation.
				Return only the translation. Do not explain, annotate, or quote the source.

				<source>
				%s
				</source>
				""".formatted(source);
		String translated = providerRegistry.executeLlmTask(
				AiProviderRegistry.QWEN_LLM_PLUS,
				prompt,
				null);
		if (translated == null || translated.isBlank()) {
			throw new BusinessException(
					"TRANSLATION_EMPTY",
					"翻译模型没有返回有效文本");
		}
		return new TranslateTextResponse(source, translated.strip(), "zh-CN");
	}
}
