package com.unispeaking.domain.po.scene;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.InterviewQuestionType;
import com.unispeaking.domain.vo.scene.InterviewReportDimension;
import com.unispeaking.domain.vo.scene.InterviewReportType;
import com.unispeaking.domain.vo.scene.TargetRoleSummary;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterviewPersistenceContractTest {

	private static final OffsetDateTime NOW = OffsetDateTime.of(
			2026,
			8,
			4,
			10,
			30,
			0,
			0,
			ZoneOffset.ofHours(8));

	@Test
	void roleSummaryNormalizesNullListsAndDefensivelyCopiesCollections() {
		List<String> responsibilities = new ArrayList<>(List.of("Plan delivery"));
		TargetRoleSummary summary = new TargetRoleSummary(
				"SaaS product role",
				responsibilities,
				null,
				null);

		responsibilities.clear();

		assertAll(
				() -> assertEquals(List.of("Plan delivery"),
						summary.responsibilities()),
				() -> assertEquals(List.of(), summary.requiredSkills()),
				() -> assertEquals(List.of(),
						summary.qualificationRequirements()),
				() -> assertThrows(
						UnsupportedOperationException.class,
						() -> summary.responsibilities().add("Changed")),
				() -> assertThrows(
						NullPointerException.class,
						() -> new TargetRoleSummary(
								"Role",
								Arrays.asList("Valid", null),
								List.of(),
								List.of())));
	}

	@Test
	void interviewRecordCarriesOnlyPersistedTableFacts() {
		InterviewRecord record = new InterviewRecord(
				"interview_1",
				UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
				"interview_session_1",
				"Product Manager",
				InterviewDifficulty.STANDARD,
				summary(),
				null,
				null,
				null,
				NOW,
				NOW);

		assertAll(
				() -> assertEquals("interview_1", record.id()),
				() -> assertEquals("interview_session_1", record.sessionId()),
				() -> assertEquals(InterviewDifficulty.STANDARD,
						record.difficulty()),
				() -> assertNull(record.recordingObjectKey()),
				() -> assertNull(record.recordingDurationSeconds()),
				() -> assertNull(record.completedAt()),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new InterviewRecord(
								"interview_1",
								record.userId(),
								record.sessionId(),
								record.jobTitle(),
								record.difficulty(),
								record.roleSummary(),
								"recordings/interview_1.mp3",
								-1,
								NOW,
								NOW,
								NOW)));
	}

	@Test
	void actualQuestionRecordRequiresOneBasedSequence() {
		InterviewQuestionRecord question = new InterviewQuestionRecord(
				"interview_1",
				1,
				InterviewQuestionType.MAIN,
				"Tell me about your experience.",
				NOW,
				NOW);

		assertAll(
				() -> assertEquals(1, question.questionNo()),
				() -> assertEquals(InterviewQuestionType.MAIN,
						question.questionType()),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new InterviewQuestionRecord(
								"interview_1",
								0,
								InterviewQuestionType.FOLLOW_UP,
								"Why?",
								NOW,
								NOW)));
	}

	@Test
	void reportRequiresCompleteDimensionsAndScoresWithinRange() {
		InterviewReportDimension lowerBound = dimension("0");
		InterviewReportDimension upperBound = dimension("100");
		InterviewReportRecord report = new InterviewReportRecord(
				"interview_1",
				InterviewReportType.FULL,
				new BigDecimal("80.0"),
				"Clear overall performance.",
				lowerBound,
				upperBound,
				dimension("80.0"),
				dimension("75.5"),
				dimension("82.0"),
				NOW,
				NOW);

		assertAll(
				() -> assertEquals(lowerBound, report.fluency()),
				() -> assertEquals(upperBound, report.logicCoherence()),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> dimension("100.1")),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new InterviewReportRecord(
								"interview_1",
								InterviewReportType.PARTIAL,
								new BigDecimal("-0.1"),
								"Summary",
								lowerBound,
								lowerBound,
								lowerBound,
								lowerBound,
								lowerBound,
								NOW,
								NOW)),
				() -> assertThrows(
						NullPointerException.class,
						() -> new InterviewReportRecord(
								"interview_1",
								InterviewReportType.PARTIAL,
								new BigDecimal("80"),
								"Summary",
								null,
								lowerBound,
								lowerBound,
								lowerBound,
								lowerBound,
								NOW,
								NOW)));
	}

	@Test
	void persistenceEnumsExposeOnlyRfcValues() {
		assertAll(
				() -> assertArrayEquals(
						new InterviewDifficulty[] {
							InterviewDifficulty.BASIC,
							InterviewDifficulty.STANDARD,
							InterviewDifficulty.CHALLENGE
						},
						InterviewDifficulty.values()),
				() -> assertArrayEquals(
						new InterviewQuestionType[] {
							InterviewQuestionType.MAIN,
							InterviewQuestionType.FOLLOW_UP
						},
						InterviewQuestionType.values()),
				() -> assertArrayEquals(
						new InterviewReportType[] {
							InterviewReportType.FULL,
							InterviewReportType.PARTIAL
						},
						InterviewReportType.values()));
	}

	private static TargetRoleSummary summary() {
		return new TargetRoleSummary(
				"SaaS product role",
				List.of("Plan delivery"),
				List.of("Communication"),
				List.of());
	}

	private static InterviewReportDimension dimension(String score) {
		return new InterviewReportDimension(
				new BigDecimal(score),
				"Clear evaluation.",
				"Practice with examples.");
	}
}
