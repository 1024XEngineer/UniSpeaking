package com.unispeaking.service.evaluation.internal.result;

import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import java.math.BigDecimal;
import java.util.List;

/**
 * 构造并识别“过短，不予评分”的单轮结果。
 *
 * <p>持久化覆盖和整场报告过滤必须复用本类，不能仅凭任意单项零分判断过短，
 * 否则会错误排除某个正常维度恰好为零的有效评分。</p>
 */
public final class TooShortEvaluationPolicy {

	/**
	 * 过短记录的固定反馈，也是当前数据库记录的精确身份标记，不能随意改写。
	 */
	public static final String FEEDBACK_SUMMARY = "过短，不予评分";

	private TooShortEvaluationPolicy() {
	}

	/**
	 * 创建不调用 Provider 时直接返回的过短结果。
	 *
	 * @return 六项全零、固定反馈、空建议及空单词明细
	 */
	public static DialogueTurnEvaluationResult createResult(
			int turnNo,
			String transcript) {
		return new DialogueTurnEvaluationResult(
				turnNo,
				transcript,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				FEEDBACK_SUMMARY,
				"",
				List.of());
	}

	/**
	 * 判断公开单轮结果是否为过短记录。
	 *
	 * @param result 单轮评分结果，允许为 {@code null}
	 * @return 仅当六项分数全零且反馈文案完全匹配时返回 {@code true}
	 */
	public static boolean isTooShort(DialogueTurnEvaluationResult result) {
		if (result == null) {
			return false;
		}
		return isTooShort(
				result.overallScore(),
				result.rhythmScore(),
				result.toneScore(),
				result.integrityScore(),
				result.pronunciationScore(),
				result.fluencyScore(),
				result.feedbackSummary());
	}

	/**
	 * 根据持久化记录共有的评分字段执行精确识别。
	 *
	 * <p>BigDecimal 使用数值比较，因此不同小数位的零都能命中；反馈文案不做
	 * trim 或模糊匹配，以避免普通零分结果被误判为过短。</p>
	 */
	public static boolean isTooShort(
			BigDecimal overallScore,
			BigDecimal rhythmScore,
			BigDecimal toneScore,
			BigDecimal integrityScore,
			BigDecimal pronunciationScore,
			BigDecimal fluencyScore,
			String feedbackSummary) {
		return isZero(overallScore)
				&& isZero(rhythmScore)
				&& isZero(toneScore)
				&& isZero(integrityScore)
				&& isZero(pronunciationScore)
				&& isZero(fluencyScore)
				&& FEEDBACK_SUMMARY.equals(feedbackSummary);
	}

	private static boolean isZero(BigDecimal score) {
		return score != null && score.compareTo(BigDecimal.ZERO) == 0;
	}
}
