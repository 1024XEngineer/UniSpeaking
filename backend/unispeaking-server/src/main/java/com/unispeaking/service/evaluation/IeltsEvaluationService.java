package com.unispeaking.service.evaluation;

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
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IeltsEvaluationService extends EvaluationService<
		IeltsEvaluationReport,
		IeltsEvaluationDetail> {

	private final EvaluationProcessor delegate;

	public IeltsEvaluationService(
			EvaluationProcessor delegate,
			SessionLifecycleManager sessionLifecycle) {
		super(
				command -> evaluateTurn(delegate, sessionLifecycle, command),
				sceneId -> toReport(generateResult(
						delegate,
						sessionLifecycle,
						sceneId)),
				sceneId -> {
					IeltsEvaluationResult result = generateResult(
							delegate,
							sessionLifecycle,
							sceneId);
					return new IeltsEvaluationDetail(toReport(result), result);
				});
		this.delegate = delegate;
	}

	@Override
	public DialogueTurnEvaluationResult evaluateTurn(
			DialogueTurnEvaluationCommand command) {
		return super.evaluateTurn(command);
	}

	@Override
	public IeltsEvaluationReport generateReport(String sceneId) {
		return super.generateReport(sceneId);
	}

	@Override
	public IeltsEvaluationDetail getEvaluation(String sceneId) {
		return super.getEvaluation(sceneId);
	}

	private static DialogueTurnEvaluationResult evaluateTurn(
			EvaluationProcessor delegate,
			SessionLifecycleManager sessionLifecycle,
			DialogueTurnEvaluationCommand command) {
		SessionDetail session = sessionLifecycle.getSession(command.sessionId());
		return delegate.evaluateIeltsTurn(session.sceneId(), command);
	}
	public IeltsEvaluationResult generateEvaluation(
			String ieltsId,
			String sessionId) {
		return delegate.generateIeltsEvaluation(ieltsId, sessionId);
	}
	public BigDecimal getLatestEstimatedScore() {
		return delegate.getLatestIeltsEstimatedScore();
	}
	public List<IeltsEvaluationHistoryItem> getHistory() {
		return delegate.getIeltsEvaluationHistory();
	}

	private static IeltsEvaluationResult generateResult(
			EvaluationProcessor delegate,
			SessionLifecycleManager sessionLifecycle,
			String sceneId) {
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

	private static IeltsEvaluationReport toReport(IeltsEvaluationResult result) {
		return new IeltsEvaluationReport(
				result.fluencyCoherenceScore(),
				result.lexicalResourceScore(),
				result.grammaticalRangeAccuracyScore(),
				result.pronunciationScore(),
				result.overallBandScore(),
				result.summary());
	}
}
