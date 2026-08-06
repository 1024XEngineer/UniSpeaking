package com.unispeaking.common.evaluation.calculation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.domain.vo.scene.InterviewDimensionFeedback;
import com.unispeaking.domain.vo.scene.InterviewReportCalculation;
import com.unispeaking.domain.vo.scene.InterviewReportDimension;
import com.unispeaking.domain.vo.scene.InterviewReportType;
import com.unispeaking.domain.vo.scene.InterviewSpeechReportScores;
import com.unispeaking.domain.vo.scene.InterviewStructuredReportAssessment;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class InterviewReportAssemblerTest {

	@Test
	void assemblesCompleteReportAtInclusiveScoreBoundaries() {
		InterviewReportCalculation report = assemble(
				InterviewReportType.FULL,
				score("0"),
				score("25"),
				score("50"),
				score("75"),
				score("100"));

		assertAll(
				() -> assertEquals(InterviewReportType.FULL, report.reportType()),
				() -> assertEquals(score("50.0"), report.overallScore()),
				() -> assertEquals("表达整体清楚，五个维度均有可执行的提升空间。",
						report.overallSummary()),
				() -> assertDimension(report.fluency(), "0"),
				() -> assertDimension(report.logicCoherence(), "25"),
				() -> assertDimension(report.grammarControl(), "50"),
				() -> assertDimension(
						report.pronunciationIntelligibility(),
						"75"),
				() -> assertDimension(report.vocabularyExpression(), "100"));
	}

	@Test
	void appliesEqualWeightToEveryDimension() {
		for (int raisedDimension = 0; raisedDimension < 5; raisedDimension++) {
			BigDecimal[] scores = {
					BigDecimal.ZERO,
					BigDecimal.ZERO,
					BigDecimal.ZERO,
					BigDecimal.ZERO,
					BigDecimal.ZERO
			};
			scores[raisedDimension] = score("100");

			InterviewReportCalculation report = assemble(
					InterviewReportType.FULL,
					scores[0],
					scores[1],
					scores[2],
					scores[3],
					scores[4]);

			assertEquals(score("20.0"), report.overallScore());
		}
	}

	@Test
	void roundsArithmeticMeanHalfUpToOneDecimal() {
		InterviewReportCalculation report = assemble(
				InterviewReportType.FULL,
				score("0"),
				score("0"),
				score("0"),
				score("0"),
				score("0.25"));

		assertAll(
				() -> assertEquals(score("0.1"), report.overallScore()),
				() -> assertEquals(1, report.overallScore().scale()));
	}

	@Test
	void fullAndPartialUseTheSameCompleteFiveDimensionAlgorithm() {
		InterviewReportCalculation full = assemble(
				InterviewReportType.FULL,
				score("61"),
				score("72"),
				score("83"),
				score("94"),
				score("55"));
		InterviewReportCalculation partial = assemble(
				InterviewReportType.PARTIAL,
				score("61"),
				score("72"),
				score("83"),
				score("94"),
				score("55"));

		assertAll(
				() -> assertEquals(score("73.0"), full.overallScore()),
				() -> assertEquals(full.overallScore(), partial.overallScore()),
				() -> assertEquals(full.overallSummary(), partial.overallSummary()),
				() -> assertEquals(full.fluency(), partial.fluency()),
				() -> assertEquals(full.logicCoherence(), partial.logicCoherence()),
				() -> assertEquals(full.grammarControl(), partial.grammarControl()),
				() -> assertEquals(
						full.pronunciationIntelligibility(),
						partial.pronunciationIntelligibility()),
				() -> assertEquals(
						full.vocabularyExpression(),
						partial.vocabularyExpression()));
	}

	@Test
	void rejectsOutOfRangeSpeechAndStructuredScores() {
		assertAll(
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new InterviewSpeechReportScores(
								score("-0.1"),
								score("80"))),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new InterviewSpeechReportScores(
								score("80"),
								score("100.1"))),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> dimension("-0.1")),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> dimension("100.1")));
	}

	@Test
	void rejectsPathologicalBigDecimalShapesBeforeCalculation() {
		BigDecimal extremePositiveScale = new BigDecimal(
				BigInteger.ONE,
				Integer.MAX_VALUE);
		BigDecimal extremeNegativeScale = new BigDecimal(
				BigInteger.ZERO,
				Integer.MIN_VALUE);
		BigDecimal excessivePrecision = new BigDecimal("9".repeat(1_000));
		InterviewReportDimension extremeLogic = new InterviewReportDimension(
				extremePositiveScale,
				"逻辑结构完整。",
				"继续练习分点表达。");

		assertAll(
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new InterviewSpeechReportScores(
								extremePositiveScale,
								score("80"))),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new InterviewSpeechReportScores(
								extremeNegativeScale,
								score("80"))),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new InterviewSpeechReportScores(
								excessivePrecision,
								score("80"))),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> assessment(
								extremeLogic,
								dimension("80"),
								dimension("80"))));
	}

	@Test
	void rejectsMissingDimensionsAndIncompleteNarratives() {
		assertAll(
				() -> assertThrows(
						NullPointerException.class,
						() -> InterviewReportAssembler.assemble(
								null,
								new InterviewSpeechReportScores(
										score("80"),
										score("80")),
								assessment(
										dimension("80"),
										dimension("80"),
										dimension("80")))),
				() -> assertThrows(
						NullPointerException.class,
						() -> assessment(null, dimension("80"), dimension("80"))),
				() -> assertThrows(
						NullPointerException.class,
						() -> InterviewReportAssembler.assemble(
								InterviewReportType.PARTIAL,
								null,
								assessment(
										dimension("80"),
										dimension("80"),
										dimension("80")))),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new InterviewDimensionFeedback(" ", "加强限时表达练习。")),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new InterviewDimensionFeedback(
								"表达较为清楚。",
								"建".repeat(1_001))));
	}

	@Test
	void domainTypesExposeOnlyNormalizedReportFields() {
		assertAll(
				() -> assertEquals(
						Set.of("evaluation", "actionSuggestion"),
						recordComponents(InterviewDimensionFeedback.class)),
				() -> assertEquals(
						Set.of("score", "evaluation", "actionSuggestion"),
						recordComponents(InterviewReportDimension.class)),
				() -> assertEquals(
						Set.of("fluency", "pronunciationIntelligibility"),
						recordComponents(InterviewSpeechReportScores.class)),
				() -> assertEquals(
						Set.of(
								"overallSummary",
								"fluency",
								"logicCoherence",
								"grammarControl",
								"pronunciationIntelligibility",
								"vocabularyExpression"),
						recordComponents(InterviewStructuredReportAssessment.class)),
				() -> assertEquals(
						Set.of(
								"reportType",
								"overallScore",
								"overallSummary",
								"fluency",
								"logicCoherence",
								"grammarControl",
								"pronunciationIntelligibility",
								"vocabularyExpression"),
						recordComponents(InterviewReportCalculation.class)));
	}

	private InterviewReportCalculation assemble(
			InterviewReportType reportType,
			BigDecimal fluency,
			BigDecimal logicCoherence,
			BigDecimal grammarControl,
			BigDecimal pronunciationIntelligibility,
			BigDecimal vocabularyExpression) {
		return InterviewReportAssembler.assemble(
				reportType,
				new InterviewSpeechReportScores(
						fluency,
						pronunciationIntelligibility),
				assessment(
						dimension(logicCoherence.toPlainString()),
						dimension(grammarControl.toPlainString()),
						dimension(vocabularyExpression.toPlainString())));
	}

	private InterviewStructuredReportAssessment assessment(
			InterviewReportDimension logicCoherence,
			InterviewReportDimension grammarControl,
			InterviewReportDimension vocabularyExpression) {
		return new InterviewStructuredReportAssessment(
				"表达整体清楚，五个维度均有可执行的提升空间。",
				feedback(),
				logicCoherence,
				grammarControl,
				feedback(),
				vocabularyExpression);
	}

	private InterviewDimensionFeedback feedback() {
		return new InterviewDimensionFeedback(
				"表达较为清楚，关键信息能够被理解。",
				"加强限时表达练习，并在练习后复盘。");
	}

	private InterviewReportDimension dimension(String value) {
		return new InterviewReportDimension(
				score(value),
				"表达较为清楚，关键信息能够被理解。",
				"加强限时表达练习，并在练习后复盘。");
	}

	private void assertDimension(
			InterviewReportDimension dimension,
			String expectedScore) {
		assertAll(
				() -> assertEquals(score(expectedScore), dimension.score()),
				() -> assertNotNull(dimension.evaluation()),
				() -> assertNotNull(dimension.actionSuggestion()));
	}

	private Set<String> recordComponents(Class<?> type) {
		return Arrays.stream(type.getRecordComponents())
				.map(component -> component.getName())
				.collect(Collectors.toSet());
	}

	private BigDecimal score(String value) {
		return new BigDecimal(value);
	}
}
