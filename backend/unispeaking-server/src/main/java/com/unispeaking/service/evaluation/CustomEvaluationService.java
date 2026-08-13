package com.unispeaking.service.evaluation;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.component.evaluation.EvaluationProcessor;
import com.unispeaking.domain.dto.evaluation.CustomEvaluationDetail;
import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationResponse;
import com.unispeaking.domain.dto.session.SessionDetail;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomEvaluationService extends EvaluationService<
		DialogueReportResult,
		CustomEvaluationDetail> {

	private final EvaluationProcessor delegate;

	public CustomEvaluationService(
			EvaluationProcessor delegate,
			SessionLifecycleManager sessionLifecycle) {
		super(
				delegate::evaluateDialogueTurn,
				sceneId -> generateReport(delegate, sessionLifecycle, sceneId),
				sceneId -> getEvaluation(delegate, sessionLifecycle, sceneId));
		this.delegate = delegate;
	}

	@Override
	public DialogueTurnEvaluationResult evaluateTurn(
			DialogueTurnEvaluationCommand command) {
		return super.evaluateTurn(command);
	}

	@Override
	public DialogueReportResult generateReport(String sceneId) {
		return super.generateReport(sceneId);
	}

	@Override
	public CustomEvaluationDetail getEvaluation(String sceneId) {
		return super.getEvaluation(sceneId);
	}
	public SentenceEvaluationResponse evaluateSentence(
			String sentenceId,
			byte[] audio) {
		return delegate.evaluateSentenceReading(sentenceId, audio);
	}
	public DialogueEvaluationResult getDialogueEvaluation(String sessionId) {
		return delegate.getDialogueEvaluation(sessionId);
	}

	private static DialogueReportResult generateReport(
			EvaluationProcessor delegate,
			SessionLifecycleManager sessionLifecycle,
			String sceneId) {
		SessionDetail session = latestSession(sessionLifecycle, sceneId);
		return delegate.generateDialogueReport(
				session.sessionId(),
				session.dialogue());
	}

	private static CustomEvaluationDetail getEvaluation(
			EvaluationProcessor delegate,
			SessionLifecycleManager sessionLifecycle,
			String sceneId) {
		SessionDetail session = latestSession(sessionLifecycle, sceneId);
		DialogueEvaluationResult detail = delegate.getDialogueEvaluation(
				session.sessionId());
		return new CustomEvaluationDetail(
				generateReport(delegate, sessionLifecycle, sceneId),
				detail);
	}

	private static SessionDetail latestSession(
			SessionLifecycleManager sessionLifecycle,
			String sceneId) {
		List<SessionDetail> sessions = sessionLifecycle.getBySceneId(sceneId);
		if (sessions.isEmpty()) {
			throw new BusinessException(
					"SESSION_NOT_FOUND",
					"scene has no session");
		}
		return sessions.getLast();
	}

}
