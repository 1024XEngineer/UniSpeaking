package com.unispeaking.domain.po.session;

import com.unispeaking.domain.vo.scene.InterviewEndReason;
import com.unispeaking.domain.vo.scene.InterviewQuestionPlan;
import com.unispeaking.domain.vo.scene.InterviewQuestionPrompt;
import com.unispeaking.domain.vo.scene.InterviewSubmissionStatus;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.domain.po.session.ConversationMessage;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.session.SessionPrompt;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InterviewSession extends AbstractSceneSession {

	private final InterviewQuestionPlan questionPlan;
	private final com.unispeaking.domain.vo.scene.InterviewProgress progress;
	private final String interviewId;
	private final Map<Integer, InterviewTurn> turns = new LinkedHashMap<>();
	private final Map<String, InterviewSubmission> submissions = new LinkedHashMap<>();
	private Instant lastSeen;
	private boolean acceptingSubmissions = true;
	private boolean endRequested;
	private boolean confirmationRequired;
	private boolean insufficientDataConfirmed;
	private boolean flowCleanupPending;

	public InterviewSession(
			String id,
			String userId,
			String interviewId,
			InterviewQuestionPlan questionPlan) {
		super(id, userId);
		if (interviewId == null || interviewId.isBlank()) {
			throw new IllegalArgumentException("interviewId must not be blank");
		}
		this.interviewId = interviewId.trim();
		this.questionPlan = Objects.requireNonNull(questionPlan, "questionPlan");
		this.progress = new com.unispeaking.domain.vo.scene.InterviewProgress(questionPlan);
		super.setSceneId(this.interviewId);
		super.setSceneType(SceneType.INTERVIEW_SCENE);
		lastSeen = getCreatedAt();
	}

	public synchronized InterviewQuestionPrompt recordMainQuestion(String text) {
		InterviewQuestionPrompt prompt = progress.recordMainQuestion(text);
		turns.put(prompt.questionNo(), new InterviewTurn(prompt));
		return prompt;
	}

	public synchronized InterviewQuestionPrompt recordFollowUp(String text) {
		InterviewQuestionPrompt prompt = progress.recordFollowUp(text);
		turns.put(prompt.questionNo(), new InterviewTurn(prompt));
		return prompt;
	}

	public synchronized void moveToNextMainQuestion() {
		progress.moveToNextMainQuestion();
		if (progress.isComplete()) {
			acceptingSubmissions = false;
			endRequested = false;
		}
	}

	public synchronized void end(InterviewEndReason reason) {
		progress.end(reason);
		acceptingSubmissions = false;
		endRequested = false;
	}

	public synchronized void touch(Instant at) {
		Instant required = Objects.requireNonNull(at, "at");
		if (required.isAfter(lastSeen)) {
			lastSeen = required;
		}
	}

	public synchronized void registerSubmission(InterviewSubmission submission) {
		Objects.requireNonNull(submission, "submission");
		if (!acceptingSubmissions) {
			throw new IllegalStateException("interview is not accepting submissions");
		}
		if (progress.currentQuestionNo() != submission.questionNo()) {
			throw new IllegalArgumentException("submission question is not current");
		}
		if (submissions.values().stream()
				.anyMatch(existing -> existing.questionNo() == submission.questionNo()
						&& existing.status() != InterviewSubmissionStatus.FAILED_TERMINAL)) {
			throw new IllegalStateException("current question already has a reusable submission");
		}
		if (submissions.putIfAbsent(submission.submissionId(), submission) != null) {
			throw new IllegalStateException("submissionId is already registered");
		}
	}

	public synchronized Optional<InterviewSubmission> submission(String submissionId) {
		return Optional.ofNullable(submissions.get(submissionId));
	}

	public synchronized List<InterviewSubmission> submissions() {
		return List.copyOf(submissions.values());
	}

	public synchronized Optional<InterviewTurn> turn(int questionNo) {
		return Optional.ofNullable(turns.get(questionNo));
	}

	public synchronized List<InterviewTurn> turns() {
		return List.copyOf(turns.values());
	}

	public synchronized List<InterviewQuestionPrompt> actualQuestions() {
		return progress.actualQuestions();
	}

	public synchronized void requestEnd() {
		endRequested = true;
		acceptingSubmissions = false;
	}

	public synchronized void clearEndRequest() {
		ensureCanAcceptSubmissionsAgain();
		endRequested = false;
		acceptingSubmissions = true;
	}

	public synchronized void requireConfirmation() {
		ensureCanAcceptSubmissionsAgain();
		confirmationRequired = true;
		endRequested = false;
		acceptingSubmissions = true;
	}

	public synchronized void confirmInsufficientData() {
		if (!confirmationRequired) {
			throw new IllegalStateException("insufficient data confirmation is not required");
		}
		insufficientDataConfirmed = true;
		acceptingSubmissions = false;
	}

	public synchronized boolean hasInFlightSubmissions() {
		return submissions.values().stream()
				.anyMatch(submission -> submission.status().isInFlight());
	}

	public synchronized boolean acceptingSubmissions() { return acceptingSubmissions; }
	public synchronized boolean endRequested() { return endRequested; }
	public synchronized boolean confirmationRequired() { return confirmationRequired; }
	public synchronized boolean insufficientDataConfirmed() { return insufficientDataConfirmed; }
	public synchronized boolean flowCleanupPending() { return flowCleanupPending; }
	public synchronized void markFlowCleanupPending() { flowCleanupPending = true; }
	public synchronized void clearFlowCleanupPending() { flowCleanupPending = false; }
	public synchronized Instant lastSeen() { return lastSeen; }
	public InterviewQuestionPlan questionPlan() { return questionPlan; }
	public String interviewId() { return interviewId; }

	@Override
	public synchronized void activate() { super.activate(); }

	@Override
	public synchronized void markConnecting() { super.markConnecting(); }

	@Override
	public synchronized void waitForClient() { super.waitForClient(); }

	@Override
	public synchronized void pause() { super.pause(); }

	@Override
	public synchronized void resume() { super.resume(); }

	@Override
	public synchronized void bindProviderSession(String providerSessionId) {
		super.bindProviderSession(providerSessionId);
	}

	@Override
	public synchronized void addMessage(ConversationMessage message) {
		throw new UnsupportedOperationException("Interview does not persist conversation messages");
	}

	@Override
	public synchronized void setSceneId(String sceneId) {
		if (!interviewId.equals(sceneId)) {
			throw new IllegalArgumentException("Interview sceneId is immutable");
		}
		super.setSceneId(sceneId);
	}

	@Override
	public synchronized void setSceneType(SceneType sceneType) {
		if (sceneType != SceneType.INTERVIEW_SCENE) {
			throw new IllegalArgumentException("Interview scene type is immutable");
		}
		super.setSceneType(sceneType);
	}

	@Override
	public synchronized void setPrompt(SessionPrompt prompt) { super.setPrompt(prompt); }

	@Override
	public synchronized void setProviderType(ProviderType providerType) {
		super.setProviderType(providerType);
	}

	@Override
	public synchronized void setModel(String model) { super.setModel(model); }

	@Override
	public synchronized void setVoiceId(String voiceId) { super.setVoiceId(voiceId); }

	@Override
	public synchronized void setCredentialExpiresAt(Instant expiresAt) {
		super.setCredentialExpiresAt(expiresAt);
	}

	@Override
	public synchronized void recordInterrupt() { super.recordInterrupt(); }

	@Override
	public synchronized void complete(Instant stopTime) { super.complete(stopTime); }

	@Override
	public synchronized void fail(Instant stopTime) { super.fail(stopTime); }

	@Override
	public synchronized void fail(String errorCode, String errorMessage) {
		super.fail(errorCode, errorMessage);
	}

	@Override
	public synchronized SessionStatus getStatus() { return super.getStatus(); }

	@Override
	public synchronized String getId() { return super.getId(); }

	@Override
	public synchronized String getUserId() { return super.getUserId(); }

	@Override
	public synchronized Instant getCreatedAt() { return super.getCreatedAt(); }

	@Override
	public synchronized String getSceneId() { return super.getSceneId(); }

	@Override
	public synchronized SceneType getSceneType() { return super.getSceneType(); }

	@Override
	public synchronized String getProviderSessionId() { return super.getProviderSessionId(); }

	@Override
	public synchronized Instant getEndedAt() { return super.getEndedAt(); }

	@Override
	public synchronized SessionPrompt getPrompt() { return super.getPrompt(); }

	@Override
	public synchronized ProviderType getProviderType() { return super.getProviderType(); }

	@Override
	public synchronized String getModel() { return super.getModel(); }

	@Override
	public synchronized String getVoiceId() { return super.getVoiceId(); }

	@Override
	public synchronized Instant getCredentialExpiresAt() {
		return super.getCredentialExpiresAt();
	}

	@Override
	public synchronized String getErrorCode() { return super.getErrorCode(); }

	@Override
	public synchronized String getErrorMessage() { return super.getErrorMessage(); }

	private void ensureCanAcceptSubmissionsAgain() {
		if (insufficientDataConfirmed
				|| progress.endReason() != null
				|| getStatus() == SessionStatus.COMPLETED
				|| getStatus() == SessionStatus.FAILED) {
			throw new IllegalStateException("terminal interview cannot accept submissions");
		}
	}

	@Override
	public synchronized boolean equals(Object other) {
		return this == other;
	}

	@Override
	public synchronized int hashCode() { return System.identityHashCode(this); }
}
