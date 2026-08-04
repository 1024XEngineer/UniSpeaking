package com.unispeaking.common.evaluation.calculation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnSpeechScoreCalculatorTest {

	@Test
	void appliesAccuracyAndFluencyFormulasToDurationWeightedPhonemes() {
		TurnSpeechScoreCalculation result = TurnSpeechScoreCalculator.calculate(
				assessment(
						score("80"),
						score("70"),
						List.of(
								phoneme(score("100"), 0, 10),
								phoneme(score("40"), 10, 40))));

		assertAll(
				() -> assertEquals(score("55.00000000"), result.phonemeAverage()),
				() -> assertEquals(score("49.0"), result.accuracyScore()),
				() -> assertEquals(score("75.0"), result.fluencyScore()),
				() -> assertEquals(40, result.effectiveDurationUnits()),
				() -> assertEquals(2, result.validPhonemeCount()));
	}

	@Test
	void clampsDurationWeightsAndCountsOnlyValidTimedPhonemes() {
		TurnSpeechScoreCalculation result = TurnSpeechScoreCalculator.calculate(
				assessment(
						score("80"),
						score("70"),
						List.of(
								phoneme(score("80"), 0, 1),
								phoneme(score("80"), 1, 51),
								phoneme(score("20"), -1, 5),
								phoneme(score("20"), 8, 8))));

		assertAll(
				() -> assertEquals(32, result.effectiveDurationUnits()),
				() -> assertEquals(2, result.validPhonemeCount()),
				() -> assertEquals(score("84.0"), result.accuracyScore()));
	}

	@Test
	void rejectsInvalidProviderScores() {
		assertAll(
				() -> assertIncomplete(assessment(
						score("101"),
						score("70"),
						List.of(phoneme(score("80"), 0, 10)))),
				() -> assertIncomplete(assessment(
						score("80"),
						score("-1"),
						List.of(phoneme(score("80"), 0, 10)))),
				() -> assertIncomplete(assessment(
						score("80"),
						score("70"),
						List.of(phoneme(score("100.1"), 0, 10)))));
	}

	@Test
	void rejectsNullAssessmentOrAssessmentsWithoutValidPhonemes() {
		assertAll(
				() -> assertIncomplete(null),
				() -> assertIncomplete(assessment(
						score("80"),
						score("70"),
						List.of())),
				() -> assertIncomplete(assessment(
						score("80"),
						score("70"),
						List.of(phoneme(score("80"), 4, 4)))));
	}

	private void assertIncomplete(PronunciationAssessmentResult assessment) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> TurnSpeechScoreCalculator.calculate(assessment));

		assertSame(
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE,
				exception.errorCode());
	}

	private PronunciationAssessmentResult assessment(
			BigDecimal fluency,
			BigDecimal rhythm,
			List<PronunciationPhonemeResult> phonemes) {
		return new PronunciationAssessmentResult(
				score("80"),
				rhythm,
				score("60"),
				score("90"),
				score("80"),
				fluency,
				EndingTone.FALL,
				List.of(new PronunciationWordResult(
						0,
						"test",
						WordReadStatus.NORMAL,
						score("80"),
						score("80"),
						null,
						phonemes)));
	}

	private PronunciationPhonemeResult phoneme(
			BigDecimal score,
			int start,
			int end) {
		return new PronunciationPhonemeResult(
				0,
				"t",
				"t",
				score,
				start,
				end);
	}

	private BigDecimal score(String value) {
		return new BigDecimal(value);
	}
}
