package com.unispeaking.service.session.impl;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.common.util.SessionIdGenerator;
import com.unispeaking.domain.dto.session.StartCommand;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.session.StartFreeChatRequest;
import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueResponse;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.po.session.ConversationMessage;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.po.session.FreeChatSceneSession;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.session.SpeakerType;
import com.unispeaking.domain.vo.session.SessionPrompt;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.SessionNotFoundException;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.asset.impl.ObsoleteDialogueCleanup;
import com.unispeaking.service.evaluation.EvaluationService;
import com.unispeaking.service.profile.ProfileService;
import com.unispeaking.common.prompt.FiveLayerPromptBuilder;
import com.unispeaking.infrastructure.realtime.RealtimeSdpExchange;
import com.unispeaking.service.scene.SceneFlowService;
import com.unispeaking.service.scene.SceneService;
import com.unispeaking.service.scene.impl.ScenarioDialogueStateMachine;
import com.unispeaking.service.session.SessionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SessionServiceImpl implements SessionService {

	private final AuthService authService;
	private final SceneService sceneService;
	private final SceneFlowService sceneFlowService;
	private final SceneRepository sceneRepository;
	private final ActiveSessionRegistry activeSessionRegistry;
	private final SessionMessageRepository sessionMessageRepository;
	private final PracticeSessionRepository practiceSessionRepository;
	private final RealtimeSdpExchange realtimeSdpExchange;
	private final EvaluationService evaluationService;
	private final ScenarioDialogueStateMachine stateMachine;
	private final ProfileService profileService;
	private final FiveLayerPromptBuilder promptService;
	private final AiProviderRegistry providerRegistry;
	private final ObsoleteDialogueCleanup dialogueCleanup;

	public SessionServiceImpl(
			AuthService authService,
			SceneService sceneService,
			SceneFlowService sceneFlowService,
			SceneRepository sceneRepository,
			ActiveSessionRegistry activeSessionRegistry,
			SessionMessageRepository sessionMessageRepository,
			PracticeSessionRepository practiceSessionRepository,
			RealtimeSdpExchange realtimeSdpExchange,
			EvaluationService evaluationService,
			ScenarioDialogueStateMachine stateMachine,
			ProfileService profileService,
			FiveLayerPromptBuilder promptService,
			AiProviderRegistry providerRegistry,
			ObsoleteDialogueCleanup dialogueCleanup) {
		this.authService = authService;
		this.sceneService = sceneService;
		this.sceneFlowService = sceneFlowService;
		this.sceneRepository = sceneRepository;
		this.activeSessionRegistry = activeSessionRegistry;
		this.sessionMessageRepository = sessionMessageRepository;
		this.practiceSessionRepository = practiceSessionRepository;
		this.realtimeSdpExchange = realtimeSdpExchange;
		this.evaluationService = evaluationService;
		this.stateMachine = stateMachine;
		this.profileService = profileService;
		this.promptService = promptService;
		this.providerRegistry = providerRegistry;
		this.dialogueCleanup = dialogueCleanup;
	}

	@Override
	public StartSessionResponse startSession(
			SceneType sceneType,
			String sceneId,
			String prompt) {
		String userId = authService.requireUserId(null);
		SceneType type = sceneType == null ? SceneType.FREE_CHAT : sceneType;
		String sessionId = SessionIdGenerator.generate(type);
		AbstractSceneSession session = type == SceneType.FREE_CHAT
				? new FreeChatSceneSession(sessionId, userId)
				: new CustomSceneSession(sessionId, userId);
		session.setSceneType(type);
		session.setSceneId(sceneId);
		session.setPrompt(new SessionPrompt(requirePrompt(prompt)));
		practiceSessionRepository.create(new PracticeSessionRecord(
				session.getId(),
				UUID.fromString(userId),
				sceneId,
				type,
				session.getStatus(),
				session.getCreatedAt(),
				null));
		activeSessionRegistry.save(session);
		RealtimeFlowLog.info(
				"session.start sessionId={} userId={} sceneType={} startTime={} prompt={}",
				session.getId(),
				userId,
				type,
				session.getCreatedAt(),
				RealtimeFlowLog.textSummary(prompt));
		return new StartSessionResponse(
				session.getId(),
				session.getCreatedAt().toString());
	}

	@Override
	public StartSceneSessionResponse startFreeChat(StartFreeChatRequest request) {
		SceneGenerationResponse scene = sceneService.generateScene(
				new SceneGenerationRequest(
						null,
						null,
						SceneType.FREE_CHAT,
						null));
		SceneFlowResponse flow = sceneFlowService.createFlow(scene.sceneId());
		StartSessionResponse started = startSession(
				SceneType.FREE_CHAT,
				scene.sceneId(),
				scene.scenePrompt());
		RealtimeConnectionResult connection = connect(
				started.sessionId(),
				scene.sceneId(),
				scene.scenePrompt(),
				SceneType.FREE_CHAT,
				request.offerSdp(),
				request.provider(),
				request.model(),
				request.voice(),
				request.translationEnabled());
		AbstractSceneSession session = requireOwnedSession(
				authService.requireUserId(null),
				started.sessionId());
		return response(
				scene,
				"Free Chat",
				flow.stage(),
				false,
				started,
				session,
				connection);
	}

	@Override
	public StartSceneSessionResponse startCustomScene(
			String sceneId,
			StartCustomSceneDialogueRequest request) {
		String userId = authService.requireUserId(null);
		CustomSceneDefinition definition = requireOwnedScene(sceneId, userId);
		SceneGenerationResponse scene = sceneRepository.findGeneratedById(sceneId)
				.orElseThrow(() -> new BusinessException(
						"CUSTOM_SCENE_NOT_FOUND",
						"自定义场景不存在"));
		String basePrompt = resolvePrompt(scene, definition, userId);
		StartSessionResponse started = startSession(
				SceneType.CUSTOM_SCENE,
				sceneId,
				basePrompt);
		stateMachine.start(started.sessionId(), definition);
		try {
			RealtimeConnectionResult connection = connect(
					started.sessionId(),
					sceneId,
					basePrompt,
					SceneType.CUSTOM_SCENE,
					request.offerSdp(),
					request.provider(),
					request.model(),
					request.voice(),
					request.translationEnabled());
			AbstractSceneSession session = requireOwnedSession(
					userId,
					started.sessionId());
			return response(
					scene,
					definition.title(),
					SceneFlowStage.DIALOGUE,
					true,
					started,
					session,
					connection);
		}
		catch (RuntimeException exception) {
			stateMachine.remove(started.sessionId());
			throw exception;
		}
	}

	@Override
	public void addMessage(String userId, String sessionId, Message message) {
		validateMessage(message);
		AbstractSceneSession session = requireOwnedSession(userId, sessionId);
		if (session.getSceneType() == SceneType.FREE_CHAT) {
			RealtimeFlowLog.info(
					"session.addMessage ignoredForStorage sessionId={} owner={}",
					session.getId(),
					message.owner());
			return;
		}
		int messageNo = session.getMessages().size() + 1;
		ConversationMessage stored = new ConversationMessage(
				"msg_" + UUID.randomUUID(),
				session.getId(),
				message.owner() == 0 ? SpeakerType.ASSISTANT : SpeakerType.USER,
				message.content().trim(),
				message.audio(),
				Instant.now());
		if (session.getSceneId() == null || session.getSceneId().isBlank()) {
			throw new BusinessException(
					"SESSION_SCENE_NOT_BOUND",
					"custom scene session is not bound to a scene");
		}
		sessionMessageRepository.append(
				session.getSceneId(),
				session.getId(),
				messageNo,
				message);
		session.addMessage(stored);
		activeSessionRegistry.save(session);
		RealtimeFlowLog.info(
				"session.addMessage sessionId={} messageNo={} owner={} content={} audioBytes={}",
				session.getId(),
				messageNo,
				message.owner(),
				RealtimeFlowLog.textSummary(message.content()),
				message.audio() == null ? 0 : message.audio().length);
	}

	@Override
	public void endSession(String userId, String sessionId, String stopTime) {
		AbstractSceneSession session = requireOwnedSession(userId, sessionId);
		if (session.getStatus() != SessionStatus.COMPLETED) {
			Instant endedAt = Instant.now();
			practiceSessionRepository.complete(
					sessionId,
					UUID.fromString(userId),
					endedAt);
			session.complete(endedAt);
			activeSessionRegistry.save(session);
		}
		RealtimeFlowLog.info(
				"session.end sessionId={} status={} stopTime={}",
				session.getId(),
				session.getStatus(),
				session.getEndedAt());
		if (session.getSceneType() == SceneType.FREE_CHAT) {
			activeSessionRegistry.remove(sessionId);
		}
	}

	@Override
	public CompleteCustomSceneDialogueResponse completeCustomScene(
			String sceneId,
			String sessionId,
			String stopTime) {
		String userId = authService.requireUserId(null);
		requireOwnedScene(sceneId, userId);
		AbstractSceneSession session = requireOwnedSession(userId, sessionId);
		requireCustomSceneBinding(session, sceneId);
		ScenarioDialogueStateResponse state =
				stateMachine.findState(sessionId)
						.map(ignored -> stateMachine.beginClosing(sessionId))
						.orElse(null);
		endSession(userId, sessionId, stopTime);
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
			activeSessionRegistry.remove(sessionId);
		}
		RealtimeFlowLog.info(
				"session.complete sceneId={} sessionId={} finalScore={}",
				sceneId,
				sessionId,
				report.finalScore());
		dialogueCleanup.retainLatestDialogue(sceneId, sessionId);
		return new CompleteCustomSceneDialogueResponse(
				sceneId,
				sessionId,
				endedAt,
				report,
				state);
	}

	@Override
	public ScenarioDialogueStateResponse advanceCustomSceneState(
			String sceneId,
			String sessionId,
			int turnNo,
			String transcript) {
		String userId = authService.requireUserId(null);
		requireOwnedScene(sceneId, userId);
		requireCustomSceneBinding(
				requireOwnedSession(userId, sessionId),
				sceneId);
		return stateMachine.advance(sessionId, turnNo, transcript);
	}

	@Override
	public ScenarioDialogueStateResponse getCustomSceneState(
			String sceneId,
			String sessionId) {
		String userId = authService.requireUserId(null);
		requireOwnedScene(sceneId, userId);
		requireCustomSceneBinding(
				requireOwnedSession(userId, sessionId),
				sceneId);
		return stateMachine.getState(sessionId);
	}

	@Override
	public TranslateTextResponse translate(String sessionId, String text) {
		String userId = authService.requireUserId(null);
		requireOwnedSession(userId, sessionId);
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
		return new TranslateTextResponse(
				source,
				translated.strip(),
				"zh-CN");
	}

	private RealtimeConnectionResult connect(
			String sessionId,
			String sceneId,
			String prompt,
			SceneType sceneType,
			String offerSdp,
			ProviderType provider,
			String model,
			String voice,
			Boolean translationEnabled) {
		AbstractSceneSession session = activeSessionRegistry.findById(sessionId)
				.orElseThrow(() -> new SessionNotFoundException(sessionId));
		ProviderType providerType = provider == null ? ProviderType.QWEN : provider;
		String voiceId = voice == null || voice.isBlank()
				? "Katerina"
				: voice.trim();
		session.setSceneId(sceneId);
		session.setSceneType(sceneType);
		session.setProviderType(providerType);
		session.setModel(model);
		session.setVoiceId(voiceId);
		session.setPrompt(new SessionPrompt(prompt));
		session.markConnecting();
		activeSessionRegistry.save(session);
		StartCommand command = new StartCommand(
				sceneType,
				session.getUserId(),
				sceneId,
				offerSdp,
				prompt,
				providerType,
				model,
				voiceId,
				translationEnabled);
		try {
			RealtimeConnectionResult connection =
					realtimeSdpExchange.exchangeSdp(
							providerType,
							session,
							session.getPrompt(),
							command);
			if (connection.providerSessionId() != null
					&& !connection.providerSessionId().isBlank()) {
				session.bindProviderSession(connection.providerSessionId());
			}
			session.setCredentialExpiresAt(connection.credentialExpiresAt());
			session.waitForClient();
			activeSessionRegistry.save(session);
			return connection;
		}
		catch (RuntimeException exception) {
			session.fail("REALTIME_CONNECTION_FAILED", exception.getMessage());
			practiceSessionRepository.fail(
					sessionId,
					UUID.fromString(session.getUserId()),
					session.getEndedAt());
			activeSessionRegistry.remove(sessionId);
			throw exception;
		}
	}

	private StartSceneSessionResponse response(
			SceneGenerationResponse scene,
			String sceneName,
			SceneFlowStage stage,
			boolean scoringEnabled,
			StartSessionResponse started,
			AbstractSceneSession session,
			RealtimeConnectionResult connection) {
		return new StartSceneSessionResponse(
				scene.sceneId(),
				sceneName,
				session.getSceneType(),
				scene.wordList(),
				scene.phraseList(),
				scene.sentenceList(),
				stage,
				scoringEnabled,
				started.sessionId(),
				session.getProviderSessionId(),
				connection.answerSdp(),
				connection.credentialExpiresAt(),
				session.getVoiceId(),
				session.getStatus(),
				started.startTime(),
				session.getPrompt().systemPrompt());
	}

	private CustomSceneDefinition requireOwnedScene(
			String sceneId,
			String userId) {
		CustomSceneDefinition scene = sceneRepository
				.findCustomDefinitionById(sceneId)
				.orElseThrow(() -> new BusinessException(
						"CUSTOM_SCENE_NOT_FOUND",
						"自定义场景不存在"));
		if (!userId.equals(scene.userId())) {
			throw new BusinessException(
					"CUSTOM_SCENE_ACCESS_DENIED",
					"当前用户无权访问该场景");
		}
		return scene;
	}

	private AbstractSceneSession requireOwnedSession(
			String userId,
			String sessionId) {
		if (userId == null || userId.isBlank()) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "请先登录");
		}
		AbstractSceneSession session = activeSessionRegistry.findById(sessionId)
				.orElseThrow(() -> new SessionNotFoundException(sessionId));
		if (!userId.equals(session.getUserId())) {
			throw new BusinessException(
					"SESSION_ACCESS_DENIED",
					"当前用户无权访问该会话");
		}
		return session;
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

	private void validateMessage(Message message) {
		if (message == null
				|| message.owner() == null
				|| (message.owner() != 0 && message.owner() != 1)
				|| message.content() == null
				|| message.content().isBlank()) {
			throw new BusinessException(
					"INVALID_SESSION_MESSAGE",
					"message owner must be 0 or 1 and content must not be blank");
		}
	}

	private String requirePrompt(String prompt) {
		if (prompt == null || prompt.isBlank()) {
			throw new BusinessException(
					"SESSION_PROMPT_REQUIRED",
					"session prompt must not be blank");
		}
		return prompt;
	}

}
