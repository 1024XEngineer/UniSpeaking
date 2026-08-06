package com.unispeaking.domain.vo.scene;

import java.util.Objects;

/**
 * Provider-independent structured assessment used to assemble an Interview
 * report.
 *
 * <p>Only normalized report fields cross into this domain object. The text is
 * expected to be Chinese, synthesized and anonymized before construction;
 * provider payloads and source answers have no representation here.</p>
 */
public record InterviewStructuredReportAssessment(
		String overallSummary,
		InterviewDimensionFeedback fluency,
		InterviewReportDimension logicCoherence,
		InterviewReportDimension grammarControl,
		InterviewDimensionFeedback pronunciationIntelligibility,
		InterviewReportDimension vocabularyExpression) {

	public InterviewStructuredReportAssessment {
		overallSummary = InterviewReportConstraints.requireText(
				overallSummary,
				"overallSummary",
				InterviewReportConstraints.MAX_SUMMARY_CODE_POINTS);
		Objects.requireNonNull(fluency, "fluency must not be null");
		validateScoredDimension(logicCoherence, "logicCoherence");
		validateScoredDimension(grammarControl, "grammarControl");
		Objects.requireNonNull(
				pronunciationIntelligibility,
				"pronunciationIntelligibility must not be null");
		validateScoredDimension(vocabularyExpression, "vocabularyExpression");
	}

	private static void validateScoredDimension(
			InterviewReportDimension dimension,
			String fieldName) {
		Objects.requireNonNull(dimension, fieldName + " must not be null");
		InterviewReportConstraints.requireScore(dimension.score(), fieldName + ".score");
		InterviewReportConstraints.requireText(
				dimension.evaluation(),
				fieldName + ".evaluation",
				InterviewReportConstraints.MAX_FEEDBACK_CODE_POINTS);
		InterviewReportConstraints.requireText(
				dimension.actionSuggestion(),
				fieldName + ".actionSuggestion",
				InterviewReportConstraints.MAX_FEEDBACK_CODE_POINTS);
	}
}
