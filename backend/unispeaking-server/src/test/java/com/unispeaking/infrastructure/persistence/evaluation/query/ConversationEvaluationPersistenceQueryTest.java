package com.unispeaking.infrastructure.persistence.evaluation.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.infrastructure.persistence.evaluation.feedback.PracticeResultFeedback;
import com.unispeaking.infrastructure.persistence.evaluation.feedback.PracticeResultFeedbackRow;
import com.unispeaking.infrastructure.persistence.evaluation.json.EvaluationJsonbCodec;
import com.unispeaking.infrastructure.persistence.evaluation.mapper.PracticeResultEvaluationMapper;
import com.unispeaking.infrastructure.persistence.evaluation.mapper.PracticeResultFeedbackMapper;
import com.unispeaking.infrastructure.persistence.evaluation.mapper.PracticeResultUtteranceMapper;
import com.unispeaking.infrastructure.persistence.evaluation.repository.PracticeResultEvaluationRepository;
import com.unispeaking.infrastructure.persistence.evaluation.repository.PracticeResultFeedbackRepository;
import com.unispeaking.infrastructure.persistence.evaluation.repository.PracticeResultUtteranceRepository;
import com.unispeaking.infrastructure.persistence.evaluation.result.PracticeResultScores;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtterance;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtteranceRow;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 验证整场持久化组合查询的事务边界、缺失状态和不可变快照。
 */
class ConversationEvaluationPersistenceQueryTest {

	@Test
	void queryUsesReadOnlyRepeatableReadTransaction() throws Exception {
		Method method = ConversationEvaluationPersistenceQuery.class.getMethod(
				"findBySessionId",
				UUID.class);
		Transactional transactional =
				method.getAnnotation(Transactional.class);

		assertAll(
				() -> assertFalse(Modifier.isFinal(
						ConversationEvaluationPersistenceQuery.class
								.getModifiers())),
				() -> assertFalse(Modifier.isFinal(method.getModifiers())),
				() -> assertTrue(transactional.readOnly()),
				() -> assertEquals(
						Isolation.REPEATABLE_READ,
						transactional.isolation()));
	}

	@Test
	void combinesCompleteReportAndKeepsUtteranceOrder() {
		UUID sessionId = UUID.randomUUID();
		PracticeResultScores scores = scores(sessionId);
		PracticeResultFeedback feedback = feedback(sessionId);
		PracticeResultUtterance first = utterance(sessionId, 1, false);
		PracticeResultUtterance second = utterance(sessionId, 2, true);
		Fixture fixture = fixture(
				scores,
				feedbackRow(feedback),
				List.of(toRow(first), toRow(second)));

		ConversationEvaluationPersistenceSnapshot snapshot =
				fixture.query().findBySessionId(sessionId);

		assertAll(
				() -> assertEquals(scores, snapshot.scores().orElseThrow()),
				() -> assertEquals(
						feedback,
						snapshot.feedback().orElseThrow()),
				() -> assertEquals(
						List.of(first, second),
						snapshot.utterances()),
				() -> assertEquals(
						sessionId,
						fixture.scoreMapper().queriedSessionId),
				() -> assertEquals(
						sessionId,
						fixture.feedbackMapper().queriedSessionId),
				() -> assertEquals(
						sessionId,
						fixture.utteranceMapper().queriedSessionId));
	}

	@Test
	void preservesBothMissingReportParts() {
		ConversationEvaluationPersistenceSnapshot snapshot =
				fixture(null, null, List.of())
						.query()
						.findBySessionId(UUID.randomUUID());

		assertAll(
				() -> assertTrue(snapshot.scores().isEmpty()),
				() -> assertTrue(snapshot.feedback().isEmpty()),
				() -> assertTrue(snapshot.utterances().isEmpty()));
	}

	@Test
	void preservesEachPartiallyMissingState() {
		UUID sessionId = UUID.randomUUID();
		PracticeResultScores scores = scores(sessionId);
		PracticeResultFeedback feedback = feedback(sessionId);

		ConversationEvaluationPersistenceSnapshot missingFeedback =
				fixture(scores, null, List.of())
						.query()
						.findBySessionId(sessionId);
		ConversationEvaluationPersistenceSnapshot missingScores =
				fixture(null, feedbackRow(feedback), List.of())
						.query()
						.findBySessionId(sessionId);

		assertAll(
				() -> assertTrue(missingFeedback.scores().isPresent()),
				() -> assertTrue(missingFeedback.feedback().isEmpty()),
				() -> assertTrue(missingScores.scores().isEmpty()),
				() -> assertTrue(missingScores.feedback().isPresent()));
	}

	@Test
	void snapshotOwnsImmutableUtteranceList() {
		UUID sessionId = UUID.randomUUID();
		List<PracticeResultUtterance> source =
				new ArrayList<>(List.of(utterance(sessionId, 1, false)));
		ConversationEvaluationPersistenceSnapshot snapshot =
				new ConversationEvaluationPersistenceSnapshot(
						Optional.empty(),
						Optional.empty(),
						source);

		source.clear();

		assertAll(
				() -> assertEquals(1, snapshot.utterances().size()),
				() -> assertThrows(
						UnsupportedOperationException.class,
						() -> snapshot.utterances().clear()));
	}

	@Test
	void rejectsNullSessionIdBeforeReadingRepositories() {
		Fixture fixture = fixture(null, null, List.of());

		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> fixture.query().findBySessionId(null));

		assertAll(
				() -> assertEquals(
						EvaluationErrorCode.INVALID_REQUEST,
						exception.errorCode()),
				() -> assertEquals(
						0,
						fixture.scoreMapper().queryCount),
				() -> assertEquals(
						0,
						fixture.feedbackMapper().queryCount),
				() -> assertEquals(
						0,
						fixture.utteranceMapper().queryCount));
	}

	@Test
	void propagatesSafeRepositoryFailureAndStopsFurtherReads() {
		Fixture fixture = fixture(null, null, List.of());
		fixture.scoreMapper().failure = new IllegalStateException(
				"database secret");

		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> fixture.query().findBySessionId(UUID.randomUUID()));

		assertAll(
				() -> assertEquals(
						EvaluationErrorCode.PERSISTENCE_FAILED,
						exception.errorCode()),
				() -> assertEquals(
						EvaluationErrorCode.PERSISTENCE_FAILED.defaultMessage(),
						exception.getMessage()),
				() -> assertEquals(
						0,
						fixture.feedbackMapper().queryCount),
				() -> assertEquals(
						0,
						fixture.utteranceMapper().queryCount));
	}

	@Test
	void snapshotRejectsNullComponents() {
		assertAll(
				() -> assertThrows(
						NullPointerException.class,
						() -> new ConversationEvaluationPersistenceSnapshot(
								null,
								Optional.empty(),
								List.of())),
				() -> assertThrows(
						NullPointerException.class,
						() -> new ConversationEvaluationPersistenceSnapshot(
								Optional.empty(),
								null,
								List.of())),
				() -> assertThrows(
						NullPointerException.class,
						() -> new ConversationEvaluationPersistenceSnapshot(
								Optional.empty(),
								Optional.empty(),
								null)));
	}

	private Fixture fixture(
			PracticeResultScores selectedScores,
			PracticeResultFeedbackRow selectedFeedback,
			List<PracticeResultUtteranceRow> selectedUtterances) {
		StubScoreMapper scoreMapper = new StubScoreMapper();
		scoreMapper.selected = selectedScores;
		StubFeedbackMapper feedbackMapper = new StubFeedbackMapper();
		feedbackMapper.selected = selectedFeedback;
		StubUtteranceMapper utteranceMapper = new StubUtteranceMapper();
		utteranceMapper.selected = List.copyOf(selectedUtterances);
		ConversationEvaluationPersistenceQuery query =
				new ConversationEvaluationPersistenceQuery(
						new PracticeResultEvaluationRepository(scoreMapper),
						new PracticeResultFeedbackRepository(feedbackMapper),
						new PracticeResultUtteranceRepository(
								utteranceMapper,
								new EvaluationJsonbCodec(
										new ObjectMapper())));
		return new Fixture(
				query,
				scoreMapper,
				feedbackMapper,
				utteranceMapper);
	}

	private PracticeResultScores scores(UUID sessionId) {
		return new PracticeResultScores(
				sessionId,
				score("90"),
				score("80"),
				score("85"),
				score("84"),
				score("83"),
				score("84"));
	}

	private PracticeResultFeedback feedback(UUID sessionId) {
		return new PracticeResultFeedback(
				sessionId,
				"整体表现稳定",
				List.of("表达清晰"),
				List.of("补充细节"));
	}

	private PracticeResultFeedbackRow feedbackRow(
			PracticeResultFeedback feedback) {
		return new PracticeResultFeedbackRow(
				feedback.sessionId(),
				feedback.summary(),
				feedback.strengths().toArray(String[]::new),
				feedback.improvements().toArray(String[]::new));
	}

	private PracticeResultUtterance utterance(
			UUID sessionId,
			int utteranceNo,
			boolean tooShort) {
		BigDecimal value = tooShort ? score("0") : score("80");
		return new PracticeResultUtterance(
				sessionId,
				utteranceNo,
				tooShort ? "Too short" : "This answer is long enough",
				"Please answer",
				value,
				value,
				value,
				value,
				value,
				value,
				tooShort ? "过短，不予评分" : "表现稳定",
				tooShort ? "" : "Try a fuller answer.",
				tooShort
						? List.of()
						: List.of(new PracticeResultUtterance.Word(
								0,
								"This",
								value,
								List.of(
										new PracticeResultUtterance.Phoneme(
												0,
												"DH",
												"DH",
												value)))));
	}

	private PracticeResultUtteranceRow toRow(
			PracticeResultUtterance utterance) {
		String json = utterance.words().isEmpty()
				? "{\"words\":[]}"
				: """
						{"words":[{"index":0,"text":"This",\
						"pronunciation_score":80,"phonemes":[{"index":0,\
						"expected_phoneme":"DH","actual_phoneme":"DH",\
						"pronunciation_score":80,"start_position":0,\
						"end_position":1}]}]}
						""";
		return new PracticeResultUtteranceRow(
				UUID.randomUUID(),
				utterance.sessionId(),
				utterance.utteranceNo(),
				utterance.transcript(),
				utterance.aiText(),
				utterance.overallScore(),
				utterance.rhythmScore(),
				utterance.toneScore(),
				utterance.integrityScore(),
				utterance.pronunciationScore(),
				utterance.fluencyScore(),
				utterance.feedbackSummary(),
				utterance.suggestedExpression(),
				json);
	}

	private BigDecimal score(String value) {
		return new BigDecimal(value);
	}

	private record Fixture(
			ConversationEvaluationPersistenceQuery query,
			StubScoreMapper scoreMapper,
			StubFeedbackMapper feedbackMapper,
			StubUtteranceMapper utteranceMapper) {
	}

	private static final class StubScoreMapper
			implements PracticeResultEvaluationMapper {

		private PracticeResultScores selected;
		private RuntimeException failure;
		private UUID queriedSessionId;
		private int queryCount;

		@Override
		public int upsert(PracticeResultScores result) {
			throw new UnsupportedOperationException();
		}

		@Override
		public PracticeResultScores findBySessionId(UUID sessionId) {
			queryCount++;
			queriedSessionId = sessionId;
			if (failure != null) {
				throw failure;
			}
			return selected;
		}
	}

	private static final class StubFeedbackMapper
			implements PracticeResultFeedbackMapper {

		private PracticeResultFeedbackRow selected;
		private UUID queriedSessionId;
		private int queryCount;

		@Override
		public int upsert(PracticeResultFeedbackRow feedback) {
			throw new UnsupportedOperationException();
		}

		@Override
		public PracticeResultFeedbackRow findBySessionId(UUID sessionId) {
			queryCount++;
			queriedSessionId = sessionId;
			return selected;
		}
	}

	private static final class StubUtteranceMapper
			implements PracticeResultUtteranceMapper {

		private List<PracticeResultUtteranceRow> selected = List.of();
		private UUID queriedSessionId;
		private int queryCount;

		@Override
		public int upsert(PracticeResultUtteranceRow row) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<PracticeResultUtteranceRow> selectBefore(
				UUID sessionId,
				int beforeUtteranceNo) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<PracticeResultUtteranceRow> selectAll(UUID sessionId) {
			queryCount++;
			queriedSessionId = sessionId;
			return selected;
		}
	}
}
