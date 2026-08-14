package com.unispeaking.service.session;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.component.policy.DailyQuotaPolicy;
import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.component.report.InterviewReportCoordinator;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.dto.evaluation.InterviewEndResponse;
import com.unispeaking.domain.dto.evaluation.InterviewReportResponse;
import com.unispeaking.domain.dto.scene.InterviewDialogueSceneContext;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.InterviewTurnResult;
import com.unispeaking.domain.dto.session.InterviewTurnStateResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.evaluation.InterviewReportRecord;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.evaluation.ReportStatus;
import com.unispeaking.domain.vo.scene.InterviewTopicEvent;
import com.unispeaking.domain.vo.scene.InterviewTopicState;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.persistence.repository.evaluation.InterviewReportRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.InterviewSceneService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * Interview 会话实现。镜像 {@code CustomSessionService.startSession}：
 * prepareDialogue（归属校验 + 读 scenePrompt + userId）→ 配额 → 建会话 → 实时连接 → 响应。
 *
 * <p>{@code submitTurn} 在 {@code synchronized(session)} 临界区内完成幂等锚定（终态守卫 +
 * owner=1 消息计数 + content 比对）+ 存录音并 attach（首个音频为准），临界区外做 LLM 主题
 * 识别并经由 {@code InterviewSceneService.advanceTopicState} 推进状态机（DI 结构守卫）。
 * {@code shouldEnd=true} 与用户 {@code endInterview} 共用 {@code orchestrateEnd} 幂等结束编排：
 * 锚点 = terminateSceneSession 早退 + interview_report 行创建者门禁（INSERT + 捕获 PK 冲突），
 * 仅真正创建行的请求提交报告任务。</p>
 */
@Service
public class InterviewSessionService {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			InterviewSessionService.class);
	private static final int DAILY_PRACTICE_LIMIT = 5;
	private static final String SCENE_NAME = "模拟面试";

	private final InterviewSceneService interviewSceneService;
	private final DailyQuotaPolicy dailyQuotaPolicy;
	private final SessionLifecycleManager sessionLifecycle;
	private final RealtimeSessionCoordinator sessionCoordinator;
	private final AuthService authService;
	private final SessionMessageRepository sessionMessageRepository;
	private final InterviewReportRepository interviewReportRepository;
	private final InterviewReportCoordinator reportCoordinator;
	private final RecordingStore interviewRecordingStore;
	private final AiProviderRegistry providerRegistry;
	private final ObjectMapper objectMapper;
	private final ObjectReader strictReader;

	public InterviewSessionService(
			InterviewSceneService interviewSceneService,
			DailyQuotaPolicy dailyQuotaPolicy,
			SessionLifecycleManager sessionLifecycle,
			RealtimeSessionCoordinator sessionCoordinator,
			AuthService authService,
			SessionMessageRepository sessionMessageRepository,
			InterviewReportRepository interviewReportRepository,
			InterviewReportCoordinator reportCoordinator,
			@Qualifier("interviewRecordingStore") RecordingStore interviewRecordingStore,
			AiProviderRegistry providerRegistry,
			ObjectMapper objectMapper) {
		this.interviewSceneService = interviewSceneService;
		this.dailyQuotaPolicy = dailyQuotaPolicy;
		this.sessionLifecycle = sessionLifecycle;
		this.sessionCoordinator = sessionCoordinator;
		this.authService = authService;
		this.sessionMessageRepository = sessionMessageRepository;
		this.interviewReportRepository = interviewReportRepository;
		this.reportCoordinator = reportCoordinator;
		this.interviewRecordingStore = interviewRecordingStore;
		this.providerRegistry = providerRegistry;
		this.objectMapper = objectMapper;
		this.strictReader = objectMapper.reader()
				.with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
	}
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
	public void addMessage(String sessionId, Message message) {
		sessionLifecycle.addMessage(sessionId, message);
	}
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
			persistTurnAudio(sessionId, turnNo, audio);
		}
		InterviewTopicEvent event = identifyTopic(
				transcript,
				interviewSceneService.interviewTopics(sceneId));
		InterviewTopicState state = interviewSceneService.advanceTopicState(
				sceneId,
				sessionId,
				turnNo,
				event);
		if (state.shouldEnd()) {
			InterviewEndResponse end = orchestrateEnd(sceneId, sessionId);
			return new InterviewTurnResult(
					new InterviewTurnStateResponse(
							true,
							state.completedTopicCount(),
							state.coveredTopicCount(),
							state.currentTopic(),
							state.controlInstruction()),
					end.reportStatus());
		}
		return toTurnResult(state);
	}
	public InterviewEndResponse endInterview(
			String sceneId,
			String sessionId) {
		return orchestrateEnd(sceneId, sessionId);
	}
	public InterviewReportResponse getReport(
			String sceneId,
			String sessionId) {
		String userId = authService.requireUserId(null);
		InterviewReportRecord record = requireOwnedReport(
				sessionId,
				sceneId,
				userId);
		if (record == null) {
			throw new BusinessException(
					InterviewErrorCode.INTERVIEW_REPORT_NOT_FOUND,
					"面试报告不存在");
		}
		if (record.status() == ReportStatus.PROCESSING) {
			reportCoordinator.redispatchIfStale(sessionId, sceneId, userId);
			record = requireOwnedReport(sessionId, sceneId, userId);
		}
		return reportCoordinator.toResponse(record);
	}
	public InterviewReportResponse retryReport(
			String sceneId,
			String sessionId) {
		String userId = authService.requireUserId(null);
		InterviewReportRecord record = requireOwnedReport(
				sessionId,
				sceneId,
				userId);
		if (record == null) {
			throw new BusinessException(
					InterviewErrorCode.INTERVIEW_REPORT_NOT_FOUND,
					"面试报告不存在");
		}
		if (record.status() == ReportStatus.FAILED
				&& interviewReportRepository.casFailedToProcessing(sessionId)) {
			reportCoordinator.submit(sessionId, sceneId, userId);
		}
		record = requireOwnedReport(sessionId, sceneId, userId);
		return reportCoordinator.toResponse(record);
	}
	public String uploadAiAudio(
			String sceneId,
			String sessionId,
			byte[] audio) {
		String userId = authService.requireUserId(null);
		AbstractSceneSession session = requireInterviewSession(sceneId, userId, sessionId);
		if (audio == null || audio.length == 0) {
			throw new BusinessException(
					InterviewErrorCode.INTERVIEW_AUDIO_INVALID,
					"AI 音频不能为空");
		}
		return interviewRecordingStore.storeAiAudio(sessionId, audio);
	}

	/**
	 * 幂等结束编排：会话锁内完成终态化 + 报告行创建门禁 + 提交任务 + 清理注册表。
	 * 会话已从活跃注册表移除（重复/并发 end）时读报告行幂等返回。
	 */
	private InterviewEndResponse orchestrateEnd(
			String sceneId,
			String sessionId) {
		String userId = authService.requireUserId(null);
		AbstractSceneSession session;
		try {
			session = requireInterviewSession(sceneId, userId, sessionId);
		}
		catch (BusinessException exception) {
			InterviewReportRecord existing = requireOwnedReport(
					sessionId,
					sceneId,
					userId);
			if (existing != null) {
				return new InterviewEndResponse(
						sessionId,
						existing.status());
			}
			throw exception;
		}
		synchronized (session) {
			sessionLifecycle.terminateSceneSession(
					userId,
					sessionId,
					SessionStatus.COMPLETED,
					Instant.now());
			boolean created = interviewReportRepository.createIfAbsent(
					sessionId,
					sceneId,
					userId);
			ReportStatus status = readReportStatus(sessionId);
			if (created) {
				reportCoordinator.submit(sessionId, sceneId, userId);
			}
			sessionCoordinator.remove(sessionId);
			LOGGER.info(
					"interview session ended sessionId={} reportStatus={} created={}",
					sessionId,
					status,
					created);
			return new InterviewEndResponse(sessionId, status);
		}
	}

	/** 首个音频为准：临界区内先存录音得 key 再 attach（attach 前判 NULL，重试不覆盖证据）。 */
	private void persistTurnAudio(
			String sessionId,
			int turnNo,
			byte[] audio) {
		if (audio == null || audio.length == 0) {
			return;
		}
		try {
			String key = interviewRecordingStore.storeTurn(
					sessionId,
					turnNo,
					audio);
			sessionMessageRepository.attachLearnerAudioObjectKey(
					sessionId,
					turnNo,
					key);
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
					"interview turn audio persistence unavailable sessionId={} turnNo={}",
					sessionId,
					turnNo);
		}
	}

	private InterviewReportRecord requireOwnedReport(
			String sessionId,
			String sceneId,
			String userId) {
		return interviewReportRepository.findById(sessionId)
				.filter(record -> record.userId() != null
						&& record.userId().equals(userId))
				.filter(record -> record.sceneId() != null
						&& record.sceneId().equals(sceneId))
				.orElse(null);
	}

	private ReportStatus readReportStatus(String sessionId) {
		return interviewReportRepository.findById(sessionId)
				.map(InterviewReportRecord::status)
				.orElse(ReportStatus.PROCESSING);
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
						state.coveredTopicCount(),
						state.currentTopic(),
						state.controlInstruction()),
				state.shouldEnd() ? ReportStatus.PROCESSING : null);
	}

	private InterviewTopicEvent identifyTopic(
			String transcript,
			List<String> candidateTopics) {
		if (transcript == null || transcript.isBlank()) {
			return InterviewTopicEvent.ignored();
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
				- topicCompleted may be true when the candidate gives a comprehensive answer that substantially covers
				  the whole topic, or explicitly signals they are done with it.
				- topicCompleted MUST still be false for a first brief answer, a short or interrupted answer,
				  or a partial answer on that topic.
				- Choose UNKNOWN when the answer is too short, ambiguous, cut off, or does not clearly belong to any topic.
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
