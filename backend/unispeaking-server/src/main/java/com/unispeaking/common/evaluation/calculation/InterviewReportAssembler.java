package com.unispeaking.common.evaluation.calculation;

import com.unispeaking.domain.vo.scene.InterviewDimensionFeedback;
import com.unispeaking.domain.vo.scene.InterviewReportCalculation;
import com.unispeaking.domain.vo.scene.InterviewReportDimension;
import com.unispeaking.domain.vo.scene.InterviewReportType;
import com.unispeaking.domain.vo.scene.InterviewSpeechReportScores;
import com.unispeaking.domain.vo.scene.InterviewStructuredReportAssessment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Pure Interview-specific five-dimension report calculation and assembly.
 *
 * <p>Each dimension has an equal 20% weight. The arithmetic mean is rounded
 * to one decimal with {@link RoundingMode#HALF_UP}; report type is metadata and
 * never changes the algorithm.</p>
 */
public final class InterviewReportAssembler {

	private static final BigDecimal DIMENSION_COUNT = new BigDecimal("5");
	private static final int OVERALL_SCORE_SCALE = 1;
	private static final RoundingMode OVERALL_SCORE_ROUNDING = RoundingMode.HALF_UP;

	private InterviewReportAssembler() {
	}

	public static InterviewReportCalculation assemble(
			InterviewReportType reportType,
			InterviewSpeechReportScores speechScores,
			InterviewStructuredReportAssessment structuredAssessment) {
		Objects.requireNonNull(reportType, "reportType must not be null");
		Objects.requireNonNull(speechScores, "speechScores must not be null");
		Objects.requireNonNull(
				structuredAssessment,
				"structuredAssessment must not be null");

		InterviewReportDimension fluency = scored(
				speechScores.fluency(),
				structuredAssessment.fluency());
		InterviewReportDimension pronunciationIntelligibility = scored(
				speechScores.pronunciationIntelligibility(),
				structuredAssessment.pronunciationIntelligibility());

		BigDecimal overallScore = arithmeticMean(
				fluency.score(),
				structuredAssessment.logicCoherence().score(),
				structuredAssessment.grammarControl().score(),
				pronunciationIntelligibility.score(),
				structuredAssessment.vocabularyExpression().score());

		return new InterviewReportCalculation(
				reportType,
				overallScore,
				structuredAssessment.overallSummary(),
				fluency,
				structuredAssessment.logicCoherence(),
				structuredAssessment.grammarControl(),
				pronunciationIntelligibility,
				structuredAssessment.vocabularyExpression());
	}

	private static InterviewReportDimension scored(
			BigDecimal score,
			InterviewDimensionFeedback feedback) {
		return new InterviewReportDimension(
				score,
				feedback.evaluation(),
				feedback.actionSuggestion());
	}

	private static BigDecimal arithmeticMean(BigDecimal... scores) {
		BigDecimal sum = BigDecimal.ZERO;
		for (BigDecimal score : scores) {
			sum = sum.add(score);
		}
		return sum.divide(
				DIMENSION_COUNT,
				OVERALL_SCORE_SCALE,
				OVERALL_SCORE_ROUNDING);
	}
}
