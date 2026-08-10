package com.unispeaking.service.evaluation;

import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;

/**
 * Stable evaluation contract shared only by scenes that support scoring.
 */
public interface EvaluationService<R, D> {

	/** 在对话上下文中评价学习者的一轮回答。 */
	DialogueTurnEvaluationResult evaluateTurn(
			DialogueTurnEvaluationCommand command);

	/** 生成场景最终评价报告。 */
	R generateReport(String sceneId);

	/** 获取场景已经保存的评价详情。 */
	D getEvaluation(String sceneId);
}
