package com.unispeaking.domain.vo.scene;

import java.util.List;

/**
 * A persistence-safe summary of the target role without source materials.
 */
public record TargetRoleSummary(
		String overview,
		List<String> responsibilities,
		List<String> requiredSkills,
		List<String> qualificationRequirements) {

	public TargetRoleSummary {
		if (overview == null || overview.isBlank()) {
			throw new IllegalArgumentException("overview must not be blank");
		}
		responsibilities = immutableList(responsibilities);
		requiredSkills = immutableList(requiredSkills);
		qualificationRequirements = immutableList(
				qualificationRequirements);
	}

	private static List<String> immutableList(List<String> values) {
		return values == null ? List.of() : List.copyOf(values);
	}
}
