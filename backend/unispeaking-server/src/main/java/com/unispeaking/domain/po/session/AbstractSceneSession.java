package com.unispeaking.domain.po.session;

import com.unispeaking.domain.po.session.ConversationMessage;
import com.unispeaking.domain.vo.session.SessionPrompt;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractSceneSession {

	private final String id;
	private final String userId;
	private final Instant createdAt = Instant.now();
	private final List<ConversationMessage> messages = new CopyOnWriteArrayList<>();
	private String sceneId;
	private SceneType sceneType;
	private String providerSessionId;
	private SessionStatus status = SessionStatus.CREATED;
	private Instant endedAt;
	private SessionPrompt prompt;
	private ProviderType providerType;
	private String model;
	private String voiceId;
	private Instant credentialExpiresAt;
	private String errorCode;
	private String errorMessage;

	protected AbstractSceneSession(String id, String userId) {
		this.id = id;
		this.userId = userId;
	}

	public void markConnecting() {
		status = SessionStatus.CONNECTING;
	}

	public void bindProviderSession(String providerSessionId) {
		this.providerSessionId = providerSessionId;
	}

	public void waitForClient() {
		status = SessionStatus.WAITING_CLIENT;
	}

	public void activate() {
		status = SessionStatus.ACTIVE;
	}

	public void pause() {
		status = SessionStatus.PAUSED;
	}

	public void resume() {
		status = SessionStatus.ACTIVE;
	}

	public void recordInterrupt() {
		status = SessionStatus.INTERRUPTED;
	}

	public void complete() {
		complete(Instant.now());
	}

	public void complete(Instant stopTime) {
		status = SessionStatus.COMPLETED;
		endedAt = stopTime == null ? Instant.now() : stopTime;
	}

	public void fail() {
		fail(Instant.now());
	}

	public void fail(Instant stopTime) {
		status = SessionStatus.FAILED;
		endedAt = stopTime == null ? Instant.now() : stopTime;
	}

	public void fail(String errorCode, String errorMessage) {
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
		fail();
	}

	public void addMessage(ConversationMessage message) {
		if (message != null) {
			messages.add(message);
		}
	}

	public String getId() { return id; }
	public String getUserId() { return userId; }
	public Instant getCreatedAt() { return createdAt; }
	public List<ConversationMessage> getMessages() { return List.copyOf(messages); }
	public String getSceneId() { return sceneId; }
	public void setSceneId(String sceneId) { this.sceneId = sceneId; }
	public SceneType getSceneType() { return sceneType; }
	public void setSceneType(SceneType sceneType) { this.sceneType = sceneType; }
	public String getProviderSessionId() { return providerSessionId; }
	public SessionStatus getStatus() { return status; }
	public Instant getEndedAt() { return endedAt; }
	public SessionPrompt getPrompt() { return prompt; }
	public void setPrompt(SessionPrompt prompt) { this.prompt = prompt; }
	public ProviderType getProviderType() { return providerType; }
	public void setProviderType(ProviderType providerType) { this.providerType = providerType; }
	public String getModel() { return model; }
	public void setModel(String model) { this.model = model; }
	public String getVoiceId() { return voiceId; }
	public void setVoiceId(String voiceId) { this.voiceId = voiceId; }
	public Instant getCredentialExpiresAt() { return credentialExpiresAt; }
	public void setCredentialExpiresAt(Instant credentialExpiresAt) {
		this.credentialExpiresAt = credentialExpiresAt;
	}
	public String getErrorCode() { return errorCode; }
	public String getErrorMessage() { return errorMessage; }
}
