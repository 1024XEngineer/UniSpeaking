package com.unispeaking.service.session.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.component.policy.DailyQuotaPolicy;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.dto.scene.InterviewDialogueSceneContext;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.InterviewTurnResult;
import com.unispeaking.domain.dto.session.InterviewTurnStateResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.evaluation.ReportStatus;
import com.unispeaking.domain.vo.scene.InterviewTopicEvent;
import com.unispeaking.domain.vo.scene.InterviewTopicState;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.InterviewSceneService;
import com.unispeaking.service.session.InterviewSessionService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * Interview 会话实现。镜像 {@code CustomSessionServiceImpl.startSession}：
 * prepareDialogue（归属校验 + 读 scenePrompt + userId）→ 配额 → 建会话 → 实时连接 → 响应。
 *
 * <p>{@code submitTurn} 在 {@code synchronized(session)} 临界区内完成幂等锚定（终态守卫 +
 * owner=1 消息计数 + content 比对），临界区外做 LLM 主题识别并经由
 * {@code InterviewSceneService.advanceTopicState} 推进状态机（DI 结构守卫：本类不直接触碰
 * 状态机）。音频落盘/attach 由第五刀补齐，本刀先接收参数。</p>
 */
@Service
public class InterviewSessionServiceImpl implements InterviewSessionService {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			InterviewSessionServiceImpl.class);
	private static final int DAILY_PRACTICE_LIMIT = 5;
	private static final String SCENE_NAME = "模拟面试";

	private final InterviewSceneService interviewSceneService;
	private final DailyQuotaPolicy dailyQuotaPolicy;
	private final SessionLifecycleManager sessionLifecycle;
	private final RealtimeSessionCoordinator sessionCoordinator;
	private final AuthService authService;
	private final SessionMessageRepository sessionMessageRepository;
	private final AiProviderRegistry providerRegistry;
	private final ObjectMapper objectMapper;
	private final ObjectReader strictReader;

	public InterviewSessionServiceImpl(
			InterviewSceneService interviewSceneService,
			DailyQuotaPolicy dailyQuotaPolicy,
			SessionLifecycleManager sessionLifecycle,
			RealtimeSessionCoordinator sessionCoordinator,
			AuthService authService,
			SessionMessageRepository sessionMessageRepository,
			AiProviderRegistry providerRegistry,
			ObjectMapper objectMapper) {
		this.interviewSceneService = interviewSceneService;
		this.dailyQuotaPolicy = dailyQuotaPolicy;
		this.sessionLifecycle = sessionLifecycle;
		this.sessionCoordinator = sessionCoordinator;
		this.authService = authService;
		this.sessionMessageRepository = sessionMessageRepository;
		this.providerRegistry = providerRegistry;
		this.objectMapper = objectMapper;
		this.strictReader = objectMapper.reader()
				.with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
	}

	@Override
	public StartSceneSessionResponse startSession(
			String sceneId,
			StartCustomSceneDialogueRequest request) {
		InterviewDialogueSceneContext prepared =
				interviewSceneService.prepareDialogue(sceneId);
		dailyQuotaPolicy.assertWithinQuota(
				prepared.userId(),
				SceneType.INTERVIEW_SCENE,
				DAILY_PRACTICE_LIMIT);
		StartSessionResponse started = sessionLifecycle.startSession(
				new StartSessionCommand(
						prepared.userId(),
						prepared.sceneId(),
						SceneType.INTERVIEW_SCENE,
						SceneFlowStage.DIALOGUE.name(),
						prepared.scenePrompt()));
		return sessionCoordinator.connect(
				new SceneGenerationResponse(
						prepared.sceneId(),
						List.of(),
						List.of(),
						List.of(),
						prepared.scenePrompt()),
				SCENE_NAME,
				SceneFlowStage.DIALOGUE,
				true,
				started,
				SceneType.INTERVIEW_SCENE,
				prepared.sceneId(),
				prepared.scenePrompt(),
				request.offerSdp(),
				request.provider(),
				request.model(),
				request.voice(),
				request.translationEnabled());
	}

	@Override
	public void addMessage(String sessionId, Message message) {
		sessionLifecycle.addMessage(sessionId, message);
	}

	@Override
	public InterviewTurnResult submitTurn(
			String sceneId,
			String sessionId,
			int turnNo,
			String transcript,
			byte[] audio) {
		String userId = authService.requireUserId(null);
		AbstractSceneSession session = requireInterviewSession(sceneId, userId, sessionId);
		if (turnNo < 1) {
			throw new BusinessException(
					InterviewErrorCode.INTERVIEW_TURN_OUT_OF_ORDER,
					"面试轮次必须大于 0");
		}
		synchronized (session) {
			if (session.getStatus() == SessionStatus.COMPLETED
					|| session.getStatus() == SessionStatus.FAILED) {
				throw new BusinessException(
						InterviewErrorCode.INTERVIEW_SESSION_ENDED,
						"面试会话已结束");
			}
			List<Message> learnerMessages = sessionMessageRepository
					.findLearnerMessages(sessionId);
			int persistedCount = learnerMessages.size();
			if (turnNo == persistedCount + 1) {
				throw new BusinessException(
						InterviewErrorCode.INTERVIEW_TURN_MESSAGE_PENDING,
						"用户消息在途，请稍后重试");
			}
			if (turnNo > persistedCount + 1) {
				throw new BusinessException(
						InterviewErrorCode.INTERVIEW_TURN_OUT_OF_ORDER,
						"面试轮次空洞");
			}
			String storedContent = learnerMessages.get(turnNo - 1).content();
			String submittedContent = transcript == null ? "" : transcript.strip();
			if (!storedContent.equals(submittedContent)) {
				throw new BusinessException(
						InterviewErrorCode.INTERVIEW_TURN_CONTENT_MISMATCH,
						"转写内容与已保存消息不一致");
			}
			// 消息已由 WS 持久化；本刀不落音频（第五刀 RecordingStore 泛化后 attach）。
		}
		InterviewTopicEvent event = identifyTopic(
				transcript,
				interviewSceneService.interviewTopics(sceneId));
		InterviewTopicState state = interviewSceneService.advanceTopicState(
				sceneId,
				sessionId,
				turnNo,
				event);
		return toTurnResult(state);
	}

	private AbstractSceneSession requireInterviewSession(
			String sceneId,
			String userId,
			String sessionId) {
		AbstractSceneSession session = sessionCoordinator.requireOwnedSession(
				userId,
				sessionId);
		if (session.getSceneType() != SceneType.INTERVIEW_SCENE) {
			throw new BusinessException(
					"INTERVIEW_SESSION_MISMATCH",
					"session does not belong to interview");
		}
		if (session.getSceneId() == null || !session.getSceneId().equals(sceneId)) {
			throw new BusinessException(
					"INTERVIEW_SCENE_MISMATCH",
					"session is not bound to this interview scene");
		}
		return session;
	}

	private InterviewTurnResult toTurnResult(InterviewTopicState state) {
		return new InterviewTurnResult(
				new InterviewTurnStateResponse(
						state.shouldEnd(),
						state.completedTopicCount(),
						state.currentTopic()),
				state.shouldEnd() ? ReportStatus.PROCESSING : null);
	}

	private InterviewTopicEvent identifyTopic(
			String transcript,
			List<String> candidateTopics) {
		if (transcript == null || transcript.isBlank()) {
			return InterviewTopicEvent.unknown();
		}
		String prompt = buildTopicIdentificationPrompt(
				transcript,
				candidateTopics);
		try {
			String content = providerRegistry
					.executeLlmTaskRouted(prompt, null)
					.response();
			return parseTopicEvent(content);
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
					"interview topic identification failed error={}",
					exception.getMessage());
			return InterviewTopicEvent.unknown();
		}
	}

	private String buildTopicIdentificationPrompt(
			String transcript,
			List<String> candidateTopics) {
		return """
				You are an interview topic tracker for a live job interview. Given the candidate's
				spoken answer, identify which interview topic (from the provided list) the answer
				belongs to.

				Candidate topics:
				%s

				Candidate answer:
				%s

				Return exactly one JSON object and no Markdown or explanatory prose.
				The JSON shape must be:
				{
				  "topic": "one of the candidate topics, or UNKNOWN if the answer does not clearly match any",
				  "topicCompleted": true or false
				}

				Rules:
				- topic MUST be one of the candidate topics verbatim, or "UNKNOWN". Do not invent new topics.
				- topicCompleted is true only when the candidate has fully addressed and closed that topic.
				""".formatted(
						jsonValue(candidateTopics == null ? List.of() : candidateTopics),
						jsonValue(transcript));
	}

	private InterviewTopicEvent parseTopicEvent(String content) {
		try {
			JsonNode root = strictReader.readTree(unwrapJsonFence(content));
			if (root == null || !root.isObject()) {
				return InterviewTopicEvent.unknown();
			}
			JsonNode topicNode = root.path("topic");
			String topic = topicNode.isString() ? topicNode.asString("").strip() : null;
			if (topic == null || topic.isBlank()) {
				return InterviewTopicEvent.unknown();
			}
			JsonNode completedNode = root.path("topicCompleted");
			boolean completed = completedNode.isBoolean()
					&& completedNode.asBoolean(false);
			return new InterviewTopicEvent(topic, completed);
		}
		catch (RuntimeException exception) {
			return InterviewTopicEvent.unknown();
		}
	}

	private String unwrapJsonFence(String content) {
		String value = content == null ? "" : content.strip();
		if (value.startsWith("```json\n") && value.endsWith("\n```")) {
			value = value.substring(8, value.length() - 4).strip();
		}
		if (value.isBlank() || value.contains("```")) {
			throw new BusinessException(
					InterviewErrorCode.INTERVIEW_REQUEST_INVALID,
					"主题识别响应格式非法");
		}
		return value;
	}

	private String jsonValue(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(
					InterviewErrorCode.INTERVIEW_REQUEST_INVALID,
					"无法序列化转写文本");
		}
	}
}
