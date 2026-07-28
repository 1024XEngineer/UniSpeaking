package com.unispeaking.service.session;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.component.SessionIdGenerator;
import com.unispeaking.domain.dto.command.StartCommand;
import com.unispeaking.domain.dto.session.AddSessionMessageRequest;
import com.unispeaking.domain.dto.session.EndSessionRequest;
import com.unispeaking.domain.dto.session.EndSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionRequest;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.prompt.SessionPrompt;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.realtime.RealtimeConnectionResult;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.exception.SessionNotFoundException;
import com.unispeaking.repository.SessionStateStore;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.quota.UsageQuotaService;
import com.unispeaking.service.realtime.RealtimeConnectionService;

public abstract class SessionService {

	protected final SessionStateStore sessionStateStore;
	private final AuthService authService;
	private final UsageQuotaService usageQuotaService;
	private final SessionIdGenerator sessionIdGenerator;
	private final RealtimeConnectionService realtimeConnectionService;

	protected SessionService(
			SessionStateStore sessionStateStore,
			AuthService authService,
			UsageQuotaService usageQuotaService,
			SessionIdGenerator sessionIdGenerator,
			RealtimeConnectionService realtimeConnectionService) {
		this.sessionStateStore = sessionStateStore;
		this.authService = authService;
		this.usageQuotaService = usageQuotaService;
		this.sessionIdGenerator = sessionIdGenerator;
		this.realtimeConnectionService = realtimeConnectionService;
	}

	public final StartSessionResponse startSession(StartSessionRequest request) {
		String userId = authService.requireUserId(request.userId());
		ProviderType providerType = request.provider() == null ? ProviderType.QWEN : request.provider();
		String voiceId = request.voice() == null || request.voice().isBlank()
				? "Katerina"
				: request.voice().trim();
		AbstractSceneSession session = createSession(generateSessionId(), userId);
		session.setSceneId(request.sceneId());
		session.setFlowId(request.flowId());
		session.setSceneType(request.sceneType());
		session.setProviderType(providerType);
		session.setModel(request.model());
		session.setVoiceId(voiceId);
		SessionPrompt prompt = prepareScene(session, request);
		session.setPrompt(prompt);
		session.markConnecting();
		sessionStateStore.save(session);
		usageQuotaService.reserve(userId, session.getId());
		usageQuotaService.startMetering(session.getId());
		try {
			RealtimeConnectionResult connection = exchangeSdp(
					session,
					prompt,
					request,
					providerType,
					voiceId);
			if (connection.providerSessionId() != null
					&& !connection.providerSessionId().isBlank()) {
				session.bindProviderSession(connection.providerSessionId());
			}
			session.setCredentialExpiresAt(connection.credentialExpiresAt());
			session.waitForClient();
			sessionStateStore.save(session);
			RealtimeFlowLog.info(
					"session.start sessionId={} userId={} sceneType={} provider={} model={} voice={} status={} prompt={}",
					session.getId(),
					userId,
					request.sceneType(),
					providerType,
					request.model(),
					voiceId,
					session.getStatus(),
					RealtimeFlowLog.textSummary(request.prompt()));
			return new StartSessionResponse(
					session.getId(),
					session.getSceneId(),
					session.getFlowId(),
					session.getCreatedAt().toString(),
					session.getProviderSessionId(),
					connection.answerSdp(),
					connection.credentialExpiresAt(),
					session.getVoiceId(),
					session.getStatus());
		}
		catch (RuntimeException exception) {
			session.fail("REALTIME_CONNECTION_FAILED", exception.getMessage());
			sessionStateStore.save(session);
			usageQuotaService.settle(session.getId());
			throw exception;
		}
	}

	private RealtimeConnectionResult exchangeSdp(
			AbstractSceneSession session,
			SessionPrompt prompt,
			StartSessionRequest request,
			ProviderType providerType,
			String voiceId) {
		StartCommand command = new StartCommand(
				request.sceneType(),
				session.getUserId(),
				request.sceneId(),
				request.offerSdp(),
				request.prompt(),
				providerType,
				request.model(),
				voiceId,
				request.translationEnabled());
		return realtimeConnectionService.connect(providerType, session, prompt, command);
	}

	public final void addMessage(AddSessionMessageRequest request) {
		AbstractSceneSession session = get(request.sessionId());
		appendMessage(session, request);
		RealtimeFlowLog.info("session.addMessage sessionId={} status={} owner={} content={}",
				session.getId(), session.getStatus(),
				request.message() == null ? null : request.message().owner(),
				request.message() == null ? null : RealtimeFlowLog.textSummary(request.message().content()));
	}

	public final EndSessionResponse endSession(EndSessionRequest request) {
		AbstractSceneSession session = get(request.sessionId());
		if (session.getStatus() != SessionStatus.COMPLETED) {
			session.complete();
			sessionStateStore.save(session);
			usageQuotaService.settle(session.getId());
			handleSessionCompleted(session);
		}
		RealtimeFlowLog.info("session.end sessionId={} status={} stopTime={}",
				session.getId(), session.getStatus(), session.getEndedAt());
		return new EndSessionResponse(session.getId(), session.getEndedAt().toString());
	}

	public AbstractSceneSession get(String sessionId) {
		return sessionStateStore.findById(sessionId)
				.orElseThrow(() -> new SessionNotFoundException(sessionId));
	}

	protected String generateSessionId() {
		return sessionIdGenerator.generate();
	}

	protected abstract AbstractSceneSession createSession(String sessionId, String userId);
	protected abstract SessionPrompt prepareScene(AbstractSceneSession session, StartSessionRequest request);
	protected abstract void appendMessage(AbstractSceneSession session, AddSessionMessageRequest request);
	protected abstract void handleSessionCompleted(AbstractSceneSession session);
}
