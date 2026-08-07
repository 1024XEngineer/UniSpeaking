package com.unispeaking.service.evaluation;

import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;

/**
 * Stable evaluation contract shared only by scenes that support scoring.
 */
public interface EvaluationService<R, D> {

	DialogueTurnEvaluationResult evaluateTurn(
			DialogueTurnEvaluationCommand command);

	R generateReport(String sceneId);

	D getEvaluation(String sceneId);
}
