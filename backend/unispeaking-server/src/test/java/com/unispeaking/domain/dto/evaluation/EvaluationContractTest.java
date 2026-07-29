package com.unispeaking.domain.dto.evaluation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.domain.dto.session.Message;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationContractTest {

	@Test
	void commandAndMessageDefensivelyCopyAudio() {
		byte[] audio = {1, 2, 3};
		DialogueTurnEvaluationCommand command =
				new DialogueTurnEvaluationCommand(
						"session",
						1,
						audio,
						"Hello there");
		audio[0] = 9;
		byte[] read = command.audio();
		read[1] = 8;

		assertArrayEquals(new byte[] {1, 2, 3}, command.audio());
	}

	@Test
	void publicCollectionsAreImmutableSnapshots() {
		List<String> strengths = new ArrayList<>(List.of("清晰"));
		DialogueReportResult report = new DialogueReportResult(
				score("80.0"),
				score("81.0"),
				score("82.0"),
				score("83.0"),
				score("84.0"),
				score("82.0"),
				"摘要",
				strengths,
				List.of("建议"));
		strengths.clear();

		assertEquals(List.of("清晰"), report.strengths());
		assertThrows(
				UnsupportedOperationException.class,
				() -> report.strengths().add("x"));

		DialogueEvaluationResult evaluation = new DialogueEvaluationResult(
				List.of(new Message(1, "Hello", null)),
				List.of(turnResult()));
		assertThrows(
				UnsupportedOperationException.class,
				() -> evaluation.turnEvaluation().clear());
	}

	private DialogueTurnEvaluationResult turnResult() {
		return new DialogueTurnEvaluationResult(
				1,
				"Hello there",
				score("80"),
				score("80"),
				score("80"),
				score("80"),
				score("80"),
				score("80"),
				"清晰",
				"Hello there.",
				List.of());
	}

	private BigDecimal score(String value) {
		return new BigDecimal(value);
	}
}
