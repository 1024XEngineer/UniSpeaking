package com.unispeaking.service.evaluation;

import com.unispeaking.domain.dto.evaluation.CustomEvaluationDetail;
import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationResponse;

/** 自定义场景评价服务，继承通用单轮评价、报告和详情能力。 */
public interface CustomEvaluationService extends EvaluationService<
		DialogueReportResult,
		CustomEvaluationDetail> {

	/** 覆写通用单轮评价方法，返回自定义场景的单轮评价结果。 */
	@Override
	DialogueTurnEvaluationResult evaluateTurn(
			DialogueTurnEvaluationCommand command);

	/** 覆写通用报告生成方法，返回自定义场景评价报告。 */
	@Override
	DialogueReportResult generateReport(String sceneId);

	/** 覆写通用详情查询方法，返回自定义场景评价详情。 */
	@Override
	CustomEvaluationDetail getEvaluation(String sceneId);

	/** 对一条学习句子的跟读音频进行发音评价。 */
	SentenceEvaluationResponse evaluateSentence(String sentenceId, byte[] audio);

	/** 获取指定会话已经保存的对话评价明细。 */
	DialogueEvaluationResult getDialogueEvaluation(String sessionId);
}
