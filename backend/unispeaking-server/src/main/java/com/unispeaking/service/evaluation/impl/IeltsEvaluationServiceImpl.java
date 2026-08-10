package com.unispeaking.service.evaluation.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.component.evaluation.EvaluationProcessor;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationDetail;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationHistoryItem;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationReport;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.domain.dto.session.SessionDetail;
import com.unispeaking.service.evaluation.IeltsEvaluationService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IeltsEvaluationServiceImpl implements IeltsEvaluationService {

	private final EvaluationProcessor delegate;
	private final SessionLifecycleManager sessionLifecycle;

	public IeltsEvaluationServiceImpl(
			EvaluationProcessor delegate,
			SessionLifecycleManager sessionLifecycle) {
		this.delegate = delegate;
		this.sessionLifecycle = sessionLifecycle;
	}

	@Override
	public DialogueTurnEvaluationResult evaluateTurn(
			DialogueTurnEvaluationCommand command) {
		SessionDetail session = sessionLifecycle.getSession(command.sessionId());
		return delegate.evaluateIeltsTurn(session.sceneId(), command);
	}

	@Override
	public IeltsEvaluationReport generateReport(String sceneId) {
		return toReport(generateResult(sceneId));
	}

	@Override
	public IeltsEvaluationDetail getEvaluation(String sceneId) {
		IeltsEvaluationResult result = generateResult(sceneId);
		return new IeltsEvaluationDetail(toReport(result), result);
	}

	@Override
	public IeltsEvaluationResult generateEvaluation(
			String ieltsId,
			String sessionId) {
		return delegate.generateIeltsEvaluation(ieltsId, sessionId);
	}

	@Override
	public BigDecimal getLatestEstimatedScore() {
		return delegate.getLatestIeltsEstimatedScore();
	}

	@Override
	public List<IeltsEvaluationHistoryItem> getHistory() {
		return delegate.getIeltsEvaluationHistory();
	}

	private IeltsEvaluationResult generateResult(String sceneId) {
		List<SessionDetail> sessions = sessionLifecycle.getBySceneId(sceneId);
		if (sessions.isEmpty()) {
			throw new BusinessException(
					"SESSION_NOT_FOUND",
					"IELTS scene has no session");
		}
		return delegate.generateIeltsEvaluation(
				sceneId,
				sessions.getLast().sessionId());
	}

	private IeltsEvaluationReport toReport(IeltsEvaluationResult result) {
		return new IeltsEvaluationReport(
				result.fluencyCoherenceScore(),
				result.lexicalResourceScore(),
				result.grammaticalRangeAccuracyScore(),
				result.pronunciationScore(),
				result.overallBandScore(),
				result.summary());
	}
}
