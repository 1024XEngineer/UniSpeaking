package com.unispeaking.domain.vo.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class TargetRoleSummaryTest {
	@Test
	void validatesOverviewAndCopiesOptionalLists() {
		TargetRoleSummary summary = new TargetRoleSummary("Engineer", null,
				List.of("Java"), List.of("Degree"));
		assertEquals(List.of(), summary.responsibilities());
		assertEquals(List.of("Java"), summary.requiredSkills());
		assertThrows(UnsupportedOperationException.class,
				() -> summary.requiredSkills().add("SQL"));
		assertThrows(IllegalArgumentException.class,
				() -> new TargetRoleSummary(" ", List.of(), List.of(), List.of()));
	}

	@Test
	void redactsSensitiveSourceContentAndRequiresJobTitle() {
		var input = new TargetRoleSummaryGenerationInput("Engineer", "secret resume");
		assertEquals("TargetRoleSummaryGenerationInput[jobTitle=<redacted>, jobDescription=<redacted>]",
				input.toString());
		assertThrows(NullPointerException.class,
				() -> new TargetRoleSummaryGenerationInput(null, "description"));
	}
}
