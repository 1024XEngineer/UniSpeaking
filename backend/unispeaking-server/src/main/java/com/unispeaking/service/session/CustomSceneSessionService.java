package com.unispeaking.service.session;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.component.SessionIdGenerator;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.conversation.ConversationMessage;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.vo.conversation.SpeakerType;
import com.unispeaking.domain.vo.prompt.SessionPrompt;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.repository.SessionStateStore;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.quota.UsageQuotaService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CustomSceneSessionService implements SessionService {

	private final SessionStateStore sessionStateStore;
	private final AuthService authService;
	private final UsageQuotaService usageQuotaService;
	private final SessionIdGenerator sessionIdGenerator;
	private CustomSceneSession session;

	public CustomSceneSessionService(
			SessionStateStore sessionStateStore,
			AuthService authService,
			UsageQuotaService usageQuotaService,
			SessionIdGenerator sessionIdGenerator) {
		this.sessionStateStore = sessionStateStore;
		this.authService = authService;
		this.usageQuotaService = usageQuotaService;
		this.sessionIdGenerator = sessionIdGenerator;
	}

	@Override
	public synchronized StartSessionResponse startSession(String prompt) {
		if (session != null) {
			throw new BusinessException("SESSION_ALREADY_STARTED", "session service already owns a session");
		}
		String userId = authService.requireUserId(null);
		session = new CustomSceneSession(sessionIdGenerator.generate(), userId);
		session.setSceneType(SceneType.CUSTOM_SCENE);
		session.setPrompt(new SessionPrompt(prompt));
		sessionStateStore.save(session);
		usageQuotaService.reserve(userId, session.getId());
		usageQuotaService.startMetering(session.getId());
		RealtimeFlowLog.info(
				"session.start sessionId={} userId={} sceneType={} startTime={} prompt={}",
				session.getId(),
				userId,
				SceneType.CUSTOM_SCENE,
				session.getCreatedAt(),
				RealtimeFlowLog.textSummary(prompt));
		return new StartSessionResponse(session.getId(), session.getCreatedAt().toString());
	}

	@Override
	public synchronized void addMessage(Message message) {
		CustomSceneSession current = requireStartedSession();
		if (message == null || !hasContent(message)) {
			return;
		}
		current.addMessage(new ConversationMessage(
				"msg_" + UUID.randomUUID(),
				current.getId(),
				toSpeaker(message.owner()),
				message.content(),
				message.audio(),
				Instant.now()));
		sessionStateStore.save(current);
		RealtimeFlowLog.info(
				"session.addMessage sessionId={} status={} owner={} content={} audioBytes={}",
				current.getId(),
				current.getStatus(),
				message.owner(),
				RealtimeFlowLog.textSummary(message.content()),
				message.audio() == null ? 0 : message.audio().length);
	}

	@Override
	public synchronized void endSession(String sessionId, String stopTime) {
		CustomSceneSession current = requireSession(sessionId);
		if (current.getStatus() != SessionStatus.COMPLETED) {
			current.complete(parseStopTime(stopTime));
			sessionStateStore.save(current);
			usageQuotaService.settle(current.getId());
		}
		RealtimeFlowLog.info(
				"session.end sessionId={} status={} stopTime={}",
				current.getId(),
				current.getStatus(),
				current.getEndedAt());
	}

	private CustomSceneSession requireStartedSession() {
		if (session == null) {
			throw new BusinessException("SESSION_NOT_STARTED", "session has not been started");
		}
		return session;
	}

	private CustomSceneSession requireSession(String sessionId) {
		CustomSceneSession current = requireStartedSession();
		if (sessionId == null || !current.getId().equals(sessionId.trim())) {
			throw new BusinessException("SESSION_ID_MISMATCH", "sessionId does not belong to this service");
		}
		return current;
	}

	private boolean hasContent(Message message) {
		return message.content() != null && !message.content().isBlank();
	}

	private SpeakerType toSpeaker(Integer owner) {
		return owner != null && owner == 0 ? SpeakerType.ASSISTANT : SpeakerType.USER;
	}

	private Instant parseStopTime(String stopTime) {
		if (stopTime == null || stopTime.isBlank()) {
			return Instant.now();
		}
		try {
			return Instant.parse(stopTime.trim());
		}
		catch (DateTimeParseException exception) {
			throw new BusinessException("INVALID_STOP_TIME", "stopTime must use ISO-8601 format");
		}
	}
}
