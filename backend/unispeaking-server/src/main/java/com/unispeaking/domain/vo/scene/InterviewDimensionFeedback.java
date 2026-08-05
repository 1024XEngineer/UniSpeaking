package com.unispeaking.domain.vo.scene;

/**
 * Provider-independent feedback for one Interview report dimension.
 *
 * <p>Providing Chinese, anonymized and synthesized text rather than source
 * answers is the caller's prerequisite. This type enforces only structural
 * presence and deterministic length limits.</p>
 */
public record InterviewDimensionFeedback(
		String evaluation,
		String actionSuggestion) {

	public InterviewDimensionFeedback {
		evaluation = InterviewReportConstraints.requireText(
				evaluation,
				"evaluation",
				InterviewReportConstraints.MAX_FEEDBACK_CODE_POINTS);
		actionSuggestion = InterviewReportConstraints.requireText(
				actionSuggestion,
				"actionSuggestion",
				InterviewReportConstraints.MAX_FEEDBACK_CODE_POINTS);
	}
}
