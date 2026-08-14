package com.unispeaking.service.evaluation;

import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import java.util.function.Function;

/**
 * 支持评分场景的通用评价实现，由子类注入具体评价策略。
 */
public class EvaluationService<R, D> {

	private final Function<DialogueTurnEvaluationCommand, DialogueTurnEvaluationResult>
			turnEvaluator;
	private final Function<String, R> reportGenerator;
	private final Function<String, D> evaluationReader;

	public EvaluationService(
			Function<DialogueTurnEvaluationCommand, DialogueTurnEvaluationResult>
					turnEvaluator,
			Function<String, R> reportGenerator,
			Function<String, D> evaluationReader) {
		this.turnEvaluator = turnEvaluator;
		this.reportGenerator = reportGenerator;
		this.evaluationReader = evaluationReader;
	}

	/** 在对话上下文中评价学习者的一轮回答。 */
	public DialogueTurnEvaluationResult evaluateTurn(
			DialogueTurnEvaluationCommand command) {
		return turnEvaluator.apply(command);
	}

	/** 生成场景最终评价报告。 */
	public R generateReport(String sceneId) {
		return reportGenerator.apply(sceneId);
	}

	/** 获取场景已经保存的评价详情。 */
	public D getEvaluation(String sceneId) {
		return evaluationReader.apply(sceneId);
	}
}
