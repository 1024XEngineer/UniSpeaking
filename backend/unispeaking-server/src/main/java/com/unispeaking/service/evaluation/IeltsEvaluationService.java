package com.unispeaking.service.evaluation;

import com.unispeaking.domain.dto.evaluation.IeltsEvaluationDetail;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationHistoryItem;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationReport;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import java.math.BigDecimal;
import java.util.List;

/** IELTS 评价服务，继承通用单轮评价、报告和详情能力。 */
public interface IeltsEvaluationService extends EvaluationService<
		IeltsEvaluationReport,
		IeltsEvaluationDetail> {

	/** 覆写通用单轮评价方法，返回 IELTS 场景的单轮评价结果。 */
	@Override
	DialogueTurnEvaluationResult evaluateTurn(
			DialogueTurnEvaluationCommand command);

	/** 覆写通用报告生成方法，返回 IELTS 场景评价报告。 */
	@Override
	IeltsEvaluationReport generateReport(String sceneId);

	/** 覆写通用详情查询方法，返回 IELTS 场景评价详情。 */
	@Override
	IeltsEvaluationDetail getEvaluation(String sceneId);

	/** 为已完成的 IELTS 会话生成并保存评价结果。 */
	IeltsEvaluationResult generateEvaluation(String ieltsId, String sessionId);

	/** 获取当前用户最新估算的 IELTS 分数。 */
	BigDecimal getLatestEstimatedScore();

	/** 查询当前用户的 IELTS 历史评价记录。 */
	List<IeltsEvaluationHistoryItem> getHistory();
}
