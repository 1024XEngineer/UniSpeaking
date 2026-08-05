package com.unispeaking.domain.vo.scene;

import java.util.Objects;

/**
 * Source boundary for role-summary generation. Resume content is intentionally absent.
 */
public record TargetRoleSummaryGenerationInput(
		String jobTitle,
		String jobDescription) {

	public TargetRoleSummaryGenerationInput {
		jobTitle = Objects.requireNonNull(jobTitle, "jobTitle must not be null");
	}

	@Override
	public String toString() {
		return "TargetRoleSummaryGenerationInput[jobTitle=<redacted>, "
				+ "jobDescription=<redacted>]";
	}
}
