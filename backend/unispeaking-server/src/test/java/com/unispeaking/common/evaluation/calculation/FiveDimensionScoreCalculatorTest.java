package com.unispeaking.common.evaluation.calculation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.unispeaking.common.evaluation.model.ConversationLanguageAssessment;
import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FiveDimensionScoreCalculatorTest {

	@Test
	void calculatesDocumentExampleWithDurationWeights() {
		ConversationScoreCalculation result = ConversationScoreCalculator.calculate(
				List.of(new TurnScoreContribution(
						score("82"),
						score("78"),
						score("80"),
						3_000,
						100)),
				new ConversationLanguageAssessment(
						score("76"),
						score("72"),
						score("74"),
						"摘要",
						List.of(),
						List.of()));

		assertAll(
				() -> assertEquals(score("82.0"), result.accuracyScore()),
				() -> assertEquals(score("78.0"), result.fluencyScore()),
				() -> assertEquals(score("76.0"), result.grammarScore()),
				() -> assertEquals(score("72.0"), result.vocabularyScore()),
				() -> assertEquals(score("77.6"), result.naturalnessScore()),
				() -> assertEquals(score("77.6"), result.finalScore()));
	}

	@Test
	void capsEachTurnAtThirtySeconds() {
		ConversationScoreCalculation result = ConversationScoreCalculator.calculate(
				List.of(
						new TurnScoreContribution(
								score("100"),
								score("100"),
								score("100"),
								9_000,
								90),
						new TurnScoreContribution(
								score("0"),
								score("0"),
								score("0"),
								3_000,
								30)),
				new ConversationLanguageAssessment(
						score("50"),
						score("50"),
						score("50"),
						"摘要",
						List.of(),
						List.of()));

		assertEquals(score("50.0"), result.accuracyScore());
	}

	@Test
	void calculatesTurnScoresFromDurationWeightedPhonemes() {
		PronunciationAssessmentResult assessment =
				new PronunciationAssessmentResult(
						score("80"),
						score("70"),
						score("60"),
						score("100"),
						score("80"),
						score("80"),
						EndingTone.FALL,
						List.of(new PronunciationWordResult(
								0,
								"test",
								WordReadStatus.NORMAL,
								score("80"),
								score("80"),
								null,
								List.of(
										new PronunciationPhonemeResult(
												0,
												"t",
												"t",
												score("100"),
												0,
												10),
										new PronunciationPhonemeResult(
												1,
												"e",
												"æ",
												score("40"),
												10,
												40)))));

		TurnSpeechScoreCalculation result =
				TurnSpeechScoreCalculator.calculate(assessment);

		assertAll(
				() -> assertEquals(
						0,
						score("55.0").compareTo(result.phonemeAverage())),
				() -> assertEquals(score("49.0"), result.accuracyScore()),
				() -> assertEquals(score("75.0"), result.fluencyScore()),
				() -> assertEquals(score("68.3"), result.audioNaturalnessScore()),
				() -> assertEquals(40, result.effectiveDurationUnits()),
				() -> assertEquals(2, result.validPhonemeCount()));
	}

	@Test
	void renormalizesAudioNaturalnessWhenToneIsAbsent() {
		PronunciationAssessmentResult assessment =
				new PronunciationAssessmentResult(
						score("80"),
						score("70"),
						null,
						score("100"),
						score("80"),
						score("80"),
						EndingTone.FALL,
						List.of(new PronunciationWordResult(
								0,
								"test",
								WordReadStatus.NORMAL,
								score("80"),
								score("80"),
								null,
								List.of(
										new PronunciationPhonemeResult(
												0,
												"t",
												"t",
												score("55"),
												0,
												10)))));

		TurnSpeechScoreCalculation result =
				TurnSpeechScoreCalculator.calculate(assessment);

		assertEquals(
				score("70.3"),
				result.audioNaturalnessScore());
	}

	private BigDecimal score(String value) {
		return new BigDecimal(value);
	}
}
