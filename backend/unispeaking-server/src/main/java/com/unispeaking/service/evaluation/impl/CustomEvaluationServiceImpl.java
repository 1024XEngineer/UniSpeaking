package com.unispeaking.service.evaluation.impl;

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
import com.unispeaking.service.evaluation.CustomEvaluationService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomEvaluationServiceImpl implements CustomEvaluationService {

	private final EvaluationProcessor delegate;
	private final SessionLifecycleManager sessionLifecycle;

	public CustomEvaluationServiceImpl(
			EvaluationProcessor delegate,
			SessionLifecycleManager sessionLifecycle) {
		this.delegate = delegate;
		this.sessionLifecycle = sessionLifecycle;
	}

	@Override
	public DialogueTurnEvaluationResult evaluateTurn(
			DialogueTurnEvaluationCommand command) {
		return delegate.evaluateDialogueTurn(command);
	}

	@Override
	public DialogueReportResult generateReport(String sceneId) {
		SessionDetail session = latestSession(sceneId);
		return delegate.generateDialogueReport(
				session.sessionId(),
				session.dialogue());
	}

	@Override
	public CustomEvaluationDetail getEvaluation(String sceneId) {
		SessionDetail session = latestSession(sceneId);
		DialogueEvaluationResult detail = delegate.getDialogueEvaluation(
				session.sessionId());
		return new CustomEvaluationDetail(generateReport(sceneId), detail);
	}

	@Override
	public SentenceEvaluationResponse evaluateSentence(
			String sentenceId,
			byte[] audio) {
		return delegate.evaluateSentenceReading(sentenceId, audio);
	}

	@Override
	public DialogueEvaluationResult getDialogueEvaluation(String sessionId) {
		return delegate.getDialogueEvaluation(sessionId);
	}

	private SessionDetail latestSession(String sceneId) {
		List<SessionDetail> sessions = sessionLifecycle.getBySceneId(sceneId);
		if (sessions.isEmpty()) {
			throw new BusinessException(
					"SESSION_NOT_FOUND",
					"scene has no session");
		}
		return sessions.getLast();
	}

}
