package com.unispeaking.domain.vo.scene;

import java.math.BigDecimal;

/**
 * Normalized, side-effect-free speech scores used by an Interview report.
 */
public record InterviewSpeechReportScores(
		BigDecimal fluency,
		BigDecimal pronunciationIntelligibility) {

	public InterviewSpeechReportScores {
		fluency = InterviewReportConstraints.requireScore(fluency, "fluency");
		pronunciationIntelligibility = InterviewReportConstraints.requireScore(
				pronunciationIntelligibility,
				"pronunciationIntelligibility");
	}
}
