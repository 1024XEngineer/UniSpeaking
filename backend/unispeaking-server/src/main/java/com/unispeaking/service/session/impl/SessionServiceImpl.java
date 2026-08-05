package com.unispeaking.service.session.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.SessionNotFoundException;
import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.common.util.SessionIdGenerator;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.po.session.ConversationMessage;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.po.session.FreeChatSceneSession;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionPrompt;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.domain.vo.session.SpeakerType;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.SceneFlowService;
import com.unispeaking.service.session.SessionService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SessionServiceImpl implements SessionService {

	private final AuthService authService;
	private final ActiveSessionRegistry activeSessionRegistry;
	private final SessionMessageRepository sessionMessageRepository;
	private final PracticeSessionRepository practiceSessionRepository;
	private final IeltsPracticeRepository ieltsPracticeRepository;
	private final SceneFlowService sceneFlowService;

	public SessionServiceImpl(
			AuthService authService,
			ActiveSessionRegistry activeSessionRegistry,
			SessionMessageRepository sessionMessageRepository,
			PracticeSessionRepository practiceSessionRepository,
			IeltsPracticeRepository ieltsPracticeRepository,
			SceneFlowService sceneFlowService) {
		this.authService = authService;
		this.activeSessionRegistry = activeSessionRegistry;
		this.sessionMessageRepository = sessionMessageRepository;
		this.practiceSessionRepository = practiceSessionRepository;
		this.ieltsPracticeRepository = ieltsPracticeRepository;
		this.sceneFlowService = sceneFlowService;
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
		registerSceneSession(session);
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

	/**
	 * Shared lifecycle hook used by concrete scene implementations such as
	 * Interview without expanding the stable SessionService interface.
	 */
	public void registerSceneSession(AbstractSceneSession session) {
		UUID userId = validateSceneSessionBinding(session);
		if (!activeSessionRegistry.registerIfAbsent(session)) {
			throw new BusinessException(
					"SESSION_ALREADY_REGISTERED",
					"同一会话标识已注册");
		}
		try {
			practiceSessionRepository.create(new PracticeSessionRecord(
					session.getId(),
					userId,
					session.getSceneId(),
					session.getSceneType(),
					session.getStatus(),
					session.getCreatedAt(),
					session.getEndedAt()));
		}
		catch (RuntimeException exception) {
			activeSessionRegistry.remove(session.getId(), session);
			throw exception;
		}
	}

	/**
	 * Shared terminal lifecycle hook for Interview and other concrete scenes.
	 * It intentionally remains an implementation capability rather than a
	 * SessionService contract method.
	 */
	public void terminateSceneSession(
			String userId,
			String sessionId,
			SessionStatus terminalStatus,
			Instant endedAt) {
		UUID ownerId = requireUserUuid(userId);
		if (endedAt == null) {
			throw new BusinessException(
					"SESSION_END_TIME_REQUIRED",
					"会话结束时间不能为空");
		}
		if (terminalStatus != SessionStatus.COMPLETED
				&& terminalStatus != SessionStatus.FAILED) {
			throw new BusinessException(
					"INVALID_SESSION_TERMINAL_STATUS",
					"会话终态只允许 COMPLETED 或 FAILED");
		}
		AbstractSceneSession session = requireOwnedSession(userId, sessionId);
		synchronized (session) {
			if (session.getStatus() == terminalStatus) return;
			if (session.getStatus() == SessionStatus.COMPLETED
					|| session.getStatus() == SessionStatus.FAILED) {
				throw new BusinessException(
						"SESSION_ALREADY_TERMINATED",
						"会话已进入其他终态");
			}
			if (terminalStatus == SessionStatus.COMPLETED) {
				practiceSessionRepository.complete(sessionId, ownerId, endedAt);
				session.complete(endedAt);
			}
			else {
				practiceSessionRepository.fail(sessionId, ownerId, endedAt);
				session.fail(endedAt);
			}
			activeSessionRegistry.save(session);
		}
	}

	@Override
	public void endSession(String userId, String sessionId, String stopTime) {
		AbstractSceneSession session = requireOwnedSession(userId, sessionId);
		if (session.getStatus() != SessionStatus.COMPLETED) {
			terminateSceneSession(
					userId,
					sessionId,
					SessionStatus.COMPLETED,
					Instant.now());
			if (session.getSceneType() == SceneType.IELTS_SCENE) {
				SceneFlowResponse next = sceneFlowService.advanceStage(
						session.getSceneId(),
						null);
				if (Boolean.TRUE.equals(next.completed())) {
					ieltsPracticeRepository.incrementCompletedCount(
							UUID.fromString(userId));
					sceneFlowService.completeFlow(session.getSceneId(), true);
				}
			}
		}
		RealtimeFlowLog.info(
				"session.end sessionId={} status={} stopTime={}",
				session.getId(),
				session.getStatus(),
				session.getEndedAt());
		if (session.getSceneType() == SceneType.FREE_CHAT
				|| session.getSceneType() == SceneType.IELTS_SCENE) {
			activeSessionRegistry.remove(sessionId);
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
					"scene session is not bound to a scene");
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

	private AbstractSceneSession requireOwnedSession(
			String userId,
			String sessionId) {
		if (userId == null || userId.isBlank()) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "请先登录");
		}
		if (sessionId == null || sessionId.isBlank()) {
			throw new SessionNotFoundException(sessionId);
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

	private UUID validateSceneSessionBinding(AbstractSceneSession session) {
		if (session == null
				|| session.getId() == null
				|| session.getId().isBlank()
				|| session.getUserId() == null
				|| session.getUserId().isBlank()
				|| session.getSceneId() == null
				|| session.getSceneId().isBlank()
				|| session.getSceneType() == null
				|| session.getStatus() == null
				|| session.getCreatedAt() == null) {
			throw new BusinessException(
					"INVALID_SCENE_SESSION_BINDING",
					"Scene 会话缺少必填绑定");
		}
		try {
			return UUID.fromString(session.getUserId());
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException(
					"INVALID_SCENE_SESSION_BINDING",
					"Scene 会话用户标识必须是 UUID");
		}
	}

	private UUID requireUserUuid(String userId) {
		if (userId == null || userId.isBlank()) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "请先登录");
		}
		try {
			return UUID.fromString(userId);
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException("INVALID_USER_ID", "用户标识必须是 UUID");
		}
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
