package com.unispeaking.component.session;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.SessionNotFoundException;
import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.common.util.SessionIdGenerator;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.SessionDetail;
import com.unispeaking.domain.dto.session.StartSessionCommand;
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
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.component.policy.UserEntitlementPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class SessionLifecycleManager {

	private final ActiveSessionRegistry activeSessionRegistry;
	private final SessionMessageRepository sessionMessageRepository;
	private final PracticeSessionRepository practiceSessionRepository;
	private final UserEntitlementPolicy entitlementPolicy;

	public SessionLifecycleManager(
			ActiveSessionRegistry activeSessionRegistry,
			SessionMessageRepository sessionMessageRepository,
			PracticeSessionRepository practiceSessionRepository) {
		this(activeSessionRegistry, sessionMessageRepository, practiceSessionRepository, null);
	}

	@Autowired
	public SessionLifecycleManager(
			ActiveSessionRegistry activeSessionRegistry,
			SessionMessageRepository sessionMessageRepository,
			PracticeSessionRepository practiceSessionRepository,
			UserEntitlementPolicy entitlementPolicy) {
		this.activeSessionRegistry = activeSessionRegistry;
		this.sessionMessageRepository = sessionMessageRepository;
		this.practiceSessionRepository = practiceSessionRepository;
		this.entitlementPolicy = entitlementPolicy;
	}

	public StartSessionResponse startSession(StartSessionCommand command) {
		if (command == null) {
			throw new BusinessException(
					"SESSION_COMMAND_REQUIRED",
					"start session command is required");
		}
		StartSessionResponse response = startSession(
				command.userId(),
				command.sceneType(),
				command.sceneId(),
				command.prompt());
		activeSessionRegistry.findById(response.sessionId()).ifPresent(session -> {
			if (command.stage() != null && command.stage().startsWith("PART")) {
				session.setIeltsPart(switch (command.stage()) {
					case "PART1" -> com.unispeaking.domain.vo.scene.IeltsPart.PART_1;
					case "PART2" -> com.unispeaking.domain.vo.scene.IeltsPart.PART_2;
					case "PART3" -> com.unispeaking.domain.vo.scene.IeltsPart.PART_3;
					default -> null;
				});
				activeSessionRegistry.save(session);
			}
		});
		return response;
	}

	public StartSessionResponse startSession(
			String userId,
			SceneType sceneType,
			String sceneId,
		String prompt) {
		requireUserUuid(userId);
		if (entitlementPolicy != null) entitlementPolicy.assertAllowed(userId);
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

	public void bindProviderSession(String userId, String sessionId, String providerSessionId) {
		if (providerSessionId == null || providerSessionId.isBlank()) {
			throw new BusinessException("PROVIDER_SESSION_ID_REQUIRED", "服务商会话标识不能为空");
		}
		String normalizedProviderSessionId = providerSessionId.trim();
		if (normalizedProviderSessionId.length() > 128) {
			throw new BusinessException("PROVIDER_SESSION_ID_INVALID", "服务商会话标识长度无效");
		}
		AbstractSceneSession session = requireOwnedSession(userId, sessionId);
		String existingProviderSessionId = session.getProviderSessionId();
		if (existingProviderSessionId != null
				&& !existingProviderSessionId.equals(normalizedProviderSessionId)) {
			throw new BusinessException("PROVIDER_SESSION_CONFLICT", "当前会话已绑定其他服务商会话");
		}
		practiceSessionRepository.bindProviderSession(
				sessionId,
				UUID.fromString(session.getUserId()),
				normalizedProviderSessionId);
		session.bindProviderSession(normalizedProviderSessionId);
		activeSessionRegistry.save(session);
	}

	/**
	 * Shared lifecycle hook used by concrete scene implementations without
	 * expanding the scene session service interfaces.
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
	 * Shared terminal lifecycle hook for concrete scenes.
	 * It intentionally remains an implementation capability rather than a
	 * scene session service contract method.
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
				if (entitlementPolicy != null) {
					entitlementPolicy.recordUsage(userId, session.getCreatedAt(), endedAt);
				}
				session.complete(endedAt);
			}
			else {
				practiceSessionRepository.fail(sessionId, ownerId, endedAt);
				session.fail(endedAt);
			}
			activeSessionRegistry.save(session);
		}
	}

	public void endSession(String sessionId) {
		endSession(requireOwnerId(sessionId), sessionId, null);
	}

	public void endSession(String userId, String sessionId, String stopTime) {
		AbstractSceneSession session = requireOwnedSession(userId, sessionId);
		if (session.getStatus() != SessionStatus.COMPLETED) {
			terminateSceneSession(
					userId,
					sessionId,
					SessionStatus.COMPLETED,
					Instant.now());
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

	public void addMessage(String sessionId, Message message) {
		addMessage(requireOwnerId(sessionId), sessionId, message);
	}

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

	public SessionDetail getSession(String sessionId) {
		PracticeSessionRecord record = practiceSessionRepository
				.findBySessionId(sessionId)
				.orElseThrow(() -> new SessionNotFoundException(sessionId));
		return toDetail(record, stageFor(record, 0));
	}

	public List<SessionDetail> getBySceneId(String sceneId) {
		List<PracticeSessionRecord> records = practiceSessionRepository
				.findBySceneId(sceneId);
		return java.util.stream.IntStream.range(0, records.size())
				.mapToObj(index -> toDetail(
						records.get(index),
						stageFor(records.get(index), index)))
				.toList();
	}

	private SessionDetail toDetail(PracticeSessionRecord record, String stage) {
		return new SessionDetail(
				record.sessionId(),
				record.sceneId(),
				record.sceneType(),
				stage,
				sessionMessageRepository.findMessages(record.sessionId()));
	}

	private String stageFor(PracticeSessionRecord record, int sceneIndex) {
		return activeSessionRegistry.findById(record.sessionId())
				.map(session -> session.getIeltsPart() == null
						? "DIALOGUE"
						: session.getIeltsPart().name().replace("PART_", "PART"))
				.orElseGet(() -> record.sceneType() == SceneType.IELTS_SCENE
						? "PART" + Math.min(sceneIndex + 1, 3)
						: "DIALOGUE");
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

	public SceneType requireSceneType(String userId, String sessionId) {
		return requireOwnedSession(userId, sessionId).getSceneType();
	}

	public String requireOwnerId(String sessionId) {
		return requireSession(sessionId).getUserId();
	}

	public String requireSceneId(String sessionId, SceneType expectedType) {
		AbstractSceneSession session = requireSession(sessionId);
		if (session.getSceneType() != expectedType) {
			throw new BusinessException(
					"SESSION_SCENE_TYPE_MISMATCH",
					"session does not belong to " + expectedType);
		}
		return session.getSceneId();
	}

	private AbstractSceneSession requireSession(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			throw new SessionNotFoundException(sessionId);
		}
		return activeSessionRegistry.findById(sessionId)
				.orElseThrow(() -> new SessionNotFoundException(sessionId));
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
