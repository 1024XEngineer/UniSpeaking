package com.unispeaking.domain.vo.scene;

/**
 * Provider-independent role-summary generation boundary.
 */
@FunctionalInterface
public interface TargetRoleSummaryGenerator {

	TargetRoleSummary generate(TargetRoleSummaryGenerationInput input);
}
