package com.unispeaking.service.scene.impl;

import com.unispeaking.common.util.SceneIdGenerator;
import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.statemachine.ScenarioDialogueStateMachine;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.scene.CustomSceneRequest;
import com.unispeaking.domain.dto.scene.CustomSceneGenerationResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueResponse;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.SceneNotFoundException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.asset.impl.ObsoleteDialogueCleanup;
import com.unispeaking.service.evaluation.EvaluationService;
import com.unispeaking.service.profile.ProfileService;
import com.unispeaking.common.prompt.FiveLayerPromptBuilder;
import com.unispeaking.service.scene.CustomSceneService;
import com.unispeaking.service.scene.SceneFlowService;
import com.unispeaking.service.session.SessionService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SceneServiceImpl implements CustomSceneService {

	private static final Logger LOGGER = LoggerFactory.getLogger(SceneServiceImpl.class);

	private final AuthService authService;
	private final ProfileService profileService;
	private final SceneRepository sceneRepository;
	private final FiveLayerPromptBuilder promptService;
	private final CustomSceneGenerator customSceneGenerator;
	private final AiProviderRegistry providerRegistry;
	private final ObjectMapper objectMapper;
	private final SessionService sessionService;
	private final SceneFlowService sceneFlowService;
	private final RealtimeSessionCoordinator sessionCoordinator;
	private final EvaluationService evaluationService;
	private final ScenarioDialogueStateMachine stateMachine;
	private final SessionMessageRepository sessionMessageRepository;
	private final ObsoleteDialogueCleanup dialogueCleanup;

	public SceneServiceImpl(
			AuthService authService,
			ProfileService profileService,
			SceneRepository sceneRepository,
			FiveLayerPromptBuilder promptService,
			CustomSceneGenerator customSceneGenerator,
			AiProviderRegistry providerRegistry,
			ObjectMapper objectMapper,
			SessionService sessionService,
			SceneFlowService sceneFlowService,
			RealtimeSessionCoordinator sessionCoordinator,
			EvaluationService evaluationService,
			ScenarioDialogueStateMachine stateMachine,
			SessionMessageRepository sessionMessageRepository,
			ObsoleteDialogueCleanup dialogueCleanup) {
		this.authService = authService;
		this.profileService = profileService;
		this.sceneRepository = sceneRepository;
		this.promptService = promptService;
		this.customSceneGenerator = customSceneGenerator;
		this.providerRegistry = providerRegistry;
		this.objectMapper = objectMapper;
		this.sessionService = sessionService;
		this.sceneFlowService = sceneFlowService;
		this.sessionCoordinator = sessionCoordinator;
		this.evaluationService = evaluationService;
		this.stateMachine = stateMachine;
		this.sessionMessageRepository = sessionMessageRepository;
		this.dialogueCleanup = dialogueCleanup;
	}

	@Override
	public SceneGenerationResponse generateScene(SceneGenerationRequest request) {
		String userId = authService.requireUserId(request.userId());
		SceneType sceneType = request.sceneType() == null ? SceneType.FREE_CHAT : request.sceneType();
		SceneConfig sceneConfig = sceneRepository.findByType(sceneType)
				.orElseThrow(() -> new SceneNotFoundException(sceneType.name()));
		UserProfile profile = profileService.getProfile(userId);
		String sceneInput = request.sceneInput() == null ? "" : request.sceneInput().trim();
		String sceneId = SceneIdGenerator.generate(sceneType);
		if (sceneType == SceneType.CUSTOM_SCENE) {
			return generateCustomScene(
					sceneId,
					userId,
					sceneInput,
					request.userPreference(),
					profile,
					sceneConfig);
		}
		if (sceneType != SceneType.FREE_CHAT) {
			throw new BusinessException(
					"SCENE_TYPE_NOT_IMPLEMENTED",
					"当前场景类型尚未实现: " + sceneType);
		}
		String scenePrompt = String.join("\n\n", promptService.compose(
				profile,
				sceneConfig,
				sceneType,
				sceneInput,
				request.userPreference(),
				List.of(),
				List.of(),
				List.of()));
		SceneGenerationResponse response = new SceneGenerationResponse(
				sceneId,
				List.of(),
				List.of(),
				List.of(),
				scenePrompt);
		return response;
	}

	@Override
	public CustomSceneGenerationResponse generate(CustomSceneRequest request) {
		SceneGenerationResponse generated = generateScene(new SceneGenerationRequest(
				request.userId(),
				request.userPreference(),
				SceneType.CUSTOM_SCENE,
				request.sceneInput()));
		CustomSceneDefinition definition = sceneRepository
				.findCustomDefinitionById(generated.sceneId())
				.orElseThrow(() -> new BusinessException(
						"CUSTOM_SCENE_NOT_FOUND",
						"生成的自定义场景不存在"));
		return new CustomSceneGenerationResponse(
				generated.sceneId(),
				definition.title(),
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

	@Override
	public byte[] synthesizeSpeech(String sceneId, String text, String model) {
		requireOwnedCustomScene(sceneId);
		if (text == null || text.isBlank()) {
			throw new BusinessException("TTS_TEXT_REQUIRED", "朗读文本不能为空");
		}
		Byte[] boxed = model == null || model.isBlank()
				? providerRegistry.generateSpeechAudio(text.strip(), null)
				: providerRegistry.generateSpeechAudio(model, text.strip(), null);
		if (boxed == null || boxed.length == 0) {
			throw new BusinessException("TTS_AUDIO_EMPTY", "TTS 未返回音频");
		}
		byte[] audio = new byte[boxed.length];
		for (int index = 0; index < boxed.length; index++) {
			if (boxed[index] == null) {
				throw new BusinessException("TTS_AUDIO_INVALID", "TTS 返回的音频无效");
			}
			audio[index] = boxed[index];
		}
		return audio;
	}

	@Override
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
		String translated = providerRegistry.executeLlmTask(
				AiProviderRegistry.QWEN_LLM_PLUS,
				prompt,
				null);
		if (translated == null || translated.isBlank()) {
			throw new BusinessException("TRANSLATION_EMPTY", "翻译模型没有返回有效文本");
		}
		return new TranslateTextResponse(source, translated.strip(), "zh-CN");
	}

	@Override
	public StartSceneSessionResponse startSession(
			String sceneId,
			StartCustomSceneDialogueRequest request) {
		String userId = authService.requireUserId(null);
		CustomSceneDefinition definition = requireOwnedCustomScene(sceneId);
		SceneGenerationResponse scene = sceneRepository.findGeneratedById(sceneId)
				.orElseThrow(() -> new BusinessException(
						"CUSTOM_SCENE_NOT_FOUND",
						"自定义场景不存在"));
		String prompt = resolvePrompt(scene, definition, userId);
		StartSessionResponse started = sessionService.startSession(
				SceneType.CUSTOM_SCENE,
				sceneId,
				prompt);
		stateMachine.start(
				started.sessionId(),
				definition.sceneId(),
				definition.successFactorJson(),
				definition.learningGoal());
		try {
			return sessionCoordinator.connect(
					scene,
					definition.title(),
					SceneFlowStage.DIALOGUE,
					true,
					started,
					SceneType.CUSTOM_SCENE,
					sceneId,
					prompt,
					request.offerSdp(),
					request.provider(),
					request.model(),
					request.voice(),
					request.translationEnabled());
		}
		catch (RuntimeException exception) {
			stateMachine.remove(started.sessionId());
			throw exception;
		}
	}

	@Override
	public CompleteCustomSceneDialogueResponse completeSession(
			String sceneId,
			String sessionId,
			String stopTime) {
		String userId = authService.requireUserId(null);
		requireOwnedCustomScene(sceneId);
		AbstractSceneSession session = sessionCoordinator.requireOwnedSession(
				userId,
				sessionId);
		requireCustomSceneBinding(session, sceneId);
		ScenarioDialogueStateResponse state = stateMachine.findState(sessionId)
				.map(ignored -> stateMachine.beginClosing(sessionId))
				.orElse(null);
		sessionService.endSession(userId, sessionId, stopTime);
		String endedAt = session.getEndedAt().toString();
		RealtimeFlowLog.info(
				"evaluation.report.start sceneId={} sessionId={}",
				sceneId,
				sessionId);
		DialogueReportResult report;
		try {
			report = evaluationService.generateDialogueReport(
					sessionId,
					sessionMessageRepository.findMessages(sessionId));
		}
		finally {
			sceneFlowService.completeFlow(sceneId, true);
			stateMachine.remove(sessionId);
			sessionCoordinator.remove(sessionId);
		}
		dialogueCleanup.retainLatestDialogue(sceneId, sessionId);
		return new CompleteCustomSceneDialogueResponse(
				sceneId,
				sessionId,
				endedAt,
				report,
				state);
	}

	@Override
	public ScenarioDialogueStateResponse advanceSessionState(
			String sceneId,
			String sessionId,
			int turnNo,
			String transcript) {
		String userId = authService.requireUserId(null);
		requireOwnedCustomScene(sceneId);
		requireCustomSceneBinding(
				sessionCoordinator.requireOwnedSession(userId, sessionId),
				sceneId);
		return stateMachine.advance(sessionId, turnNo, transcript);
	}

	@Override
	public ScenarioDialogueStateResponse getSessionState(
			String sceneId,
			String sessionId) {
		String userId = authService.requireUserId(null);
		requireOwnedCustomScene(sceneId);
		requireCustomSceneBinding(
				sessionCoordinator.requireOwnedSession(userId, sessionId),
				sceneId);
		return stateMachine.getState(sessionId);
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

	private void requireCustomSceneBinding(
			AbstractSceneSession session,
			String sceneId) {
		if (session.getSceneType() != SceneType.CUSTOM_SCENE
				|| !sceneId.equals(session.getSceneId())) {
			throw new BusinessException(
					"SESSION_ACCESS_DENIED",
					"当前会话不属于该场景");
		}
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
