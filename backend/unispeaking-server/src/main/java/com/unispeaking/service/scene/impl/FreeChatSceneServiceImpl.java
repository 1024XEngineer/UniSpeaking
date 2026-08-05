package com.unispeaking.service.scene.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.dto.session.StartFreeChatRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.CustomSceneService;
import com.unispeaking.service.scene.FreeChatSceneService;
import com.unispeaking.service.scene.SceneFlowService;
import com.unispeaking.service.session.SessionService;
import org.springframework.stereotype.Service;

@Service
public class FreeChatSceneServiceImpl implements FreeChatSceneService {

	private final CustomSceneService sceneService;
	private final SceneFlowService sceneFlowService;
	private final SessionService sessionService;
	private final RealtimeSessionCoordinator sessionCoordinator;
	private final AuthService authService;
	private final AiProviderRegistry providerRegistry;

	public FreeChatSceneServiceImpl(
			CustomSceneService sceneService,
			SceneFlowService sceneFlowService,
			SessionService sessionService,
			RealtimeSessionCoordinator sessionCoordinator,
			AuthService authService,
			AiProviderRegistry providerRegistry) {
		this.sceneService = sceneService;
		this.sceneFlowService = sceneFlowService;
		this.sessionService = sessionService;
		this.sessionCoordinator = sessionCoordinator;
		this.authService = authService;
		this.providerRegistry = providerRegistry;
	}

	@Override
	public StartSceneSessionResponse startSession(StartFreeChatRequest request) {
		SceneGenerationResponse scene = sceneService.generateScene(
				new SceneGenerationRequest(null, null, SceneType.FREE_CHAT, null));
		SceneFlowResponse flow = sceneFlowService.createFlow(scene.sceneId());
		StartSessionResponse started = sessionService.startSession(
				SceneType.FREE_CHAT,
				scene.sceneId(),
				scene.scenePrompt());
		return sessionCoordinator.connect(
				scene,
				"Free Chat",
				flow.stage(),
				false,
				started,
				SceneType.FREE_CHAT,
				scene.sceneId(),
				scene.scenePrompt(),
				request.offerSdp(),
				request.provider(),
				request.model(),
				request.voice(),
				request.translationEnabled());
	}

	@Override
	public TranslateTextResponse translate(String sessionId, String text) {
		String userId = authService.requireUserId(null);
		sessionCoordinator.requireOwnedSession(userId, sessionId);
		if (text == null || text.isBlank()) {
			throw new BusinessException("TRANSLATION_TEXT_REQUIRED", "待翻译文本不能为空");
		}
		String source = text.strip();
		if (source.length() > 4000) {
			throw new BusinessException("TRANSLATION_TEXT_TOO_LONG", "待翻译文本不能超过4000个字符");
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
			throw new BusinessException("TRANSLATION_EMPTY", "翻译模型没有返回有效文本");
		}
		return new TranslateTextResponse(source, translated.strip(), "zh-CN");
	}
}
