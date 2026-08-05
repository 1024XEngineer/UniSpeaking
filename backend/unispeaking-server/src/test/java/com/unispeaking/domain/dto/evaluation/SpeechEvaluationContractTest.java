package com.unispeaking.domain.dto.evaluation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SpeechEvaluationContractTest {

	@Test
	void commandExposesOnlyReferenceTextAndDefensivelyCopiedAudio() {
		byte[] audio = {1, 2, 3};
		SpeechEvaluationCommand command =
				new SpeechEvaluationCommand("Hello", audio);
		audio[0] = 9;
		byte[] accessedAudio = command.audio();
		accessedAudio[1] = 8;

		assertArrayEquals(
				new String[] {"referenceText", "audio"},
				fields(SpeechEvaluationCommand.class));
		assertArrayEquals(new byte[] {1, 2, 3}, command.audio());
	}

	@Test
	void resultExposesOnlyTheFourAggregateSpeechFields() {
		SpeechEvaluationResult result = new SpeechEvaluationResult(
				new BigDecimal("81.0"),
				new BigDecimal("79.0"),
				120,
				8);

		assertArrayEquals(
				new String[] {
					"accuracyScore",
					"fluencyScore",
					"effectiveDurationUnits",
					"validPhonemeCount"
				},
				fields(SpeechEvaluationResult.class));
		assertEquals(new BigDecimal("81.0"), result.accuracyScore());
		assertEquals(new BigDecimal("79.0"), result.fluencyScore());
		assertEquals(120, result.effectiveDurationUnits());
		assertEquals(8, result.validPhonemeCount());
	}

	private String[] fields(Class<?> type) {
		return Arrays.stream(type.getRecordComponents())
				.map(RecordComponent::getName)
				.toArray(String[]::new);
	}
}
