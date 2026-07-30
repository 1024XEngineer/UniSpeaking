package com.unispeaking.infrastructure.persistence.evaluation.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.infrastructure.persistence.evaluation.json.EvaluationJsonbCodec;
import com.unispeaking.infrastructure.persistence.evaluation.json.PronunciationDetailsJson;
import com.unispeaking.infrastructure.persistence.evaluation.mapper.PracticeResultUtteranceMapper;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtterance;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtterance.Phoneme;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtterance.Word;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtteranceRow;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 验证气泡评分 SQL 契约、JSONB 完整映射和仓储异常边界。
 */
class PracticeResultUtteranceRepositoryTest {

	private final StubPracticeResultUtteranceMapper mapper =
			new StubPracticeResultUtteranceMapper();
	private final EvaluationJsonbCodec codec =
			new EvaluationJsonbCodec(new ObjectMapper());
	private final PracticeResultUtteranceRepository repository =
			new PracticeResultUtteranceRepository(mapper, codec);

	@Test
	void mapperSqlUsesExpectedConflictKeyAndReplacesAllMutableFields()
			throws Exception {
		Method upsertMethod = PracticeResultUtteranceMapper.class.getMethod(
				"upsert",
				PracticeResultUtteranceRow.class);
		String sql = normalizeSql(String.join(
				" ",
				upsertMethod.getAnnotation(Insert.class).value()));
		String updateClause =
				sql.substring(sql.indexOf("do update set"));

		assertAll(
				() -> assertTrue(sql.contains(
						"insert into public.practice_result_utterances")),
				() -> assertTrue(sql.contains(
						"cast(#{row.pronunciationdetailsjson} as jsonb)")),
				() -> assertTrue(sql.contains(
						"#{row.aitext, jdbctype=varchar}")),
				() -> assertTrue(sql.contains(
						"on conflict (session_id, utterance_no) do update set")),
				() -> assertTrue(updateClause.contains(
						"transcript = excluded.transcript")),
				() -> assertTrue(updateClause.contains(
						"ai_text = excluded.ai_text")),
				() -> assertTrue(updateClause.contains(
						"overall_score = excluded.overall_score")),
				() -> assertTrue(updateClause.contains(
						"rhythm_score = excluded.rhythm_score")),
				() -> assertTrue(updateClause.contains(
						"tone_score = excluded.tone_score")),
				() -> assertTrue(updateClause.contains(
						"integrity_score = excluded.integrity_score")),
				() -> assertTrue(updateClause.contains(
						"pronunciation_score = excluded.pronunciation_score")),
				() -> assertTrue(updateClause.contains(
						"fluency_score = excluded.fluency_score")),
				() -> assertTrue(updateClause.contains(
						"feedback_summary = excluded.feedback_summary")),
				() -> assertTrue(updateClause.contains(
						"suggested_expression = excluded.suggested_expression")),
				() -> assertTrue(updateClause.contains(
						"pronunciation_details = excluded.pronunciation_details")),
				() -> assertTrue(updateClause.contains(
						"updated_at = current_timestamp")),
				() -> assertFalse(updateClause.contains("id =")),
				() -> assertFalse(updateClause.contains("created_at =")),
				() -> assertEquals(
						"row",
						upsertMethod.getParameters()[0]
								.getAnnotation(Param.class)
								.value()));
	}

	@Test
	void mapperQueriesUsePublicSchemaParametersAndAscendingOrder()
			throws Exception {
		Method beforeMethod = PracticeResultUtteranceMapper.class.getMethod(
				"selectBefore",
				UUID.class,
				int.class);
		Method allMethod = PracticeResultUtteranceMapper.class.getMethod(
				"selectAll",
				UUID.class);
		String beforeSql = selectSql(beforeMethod);
		String allSql = selectSql(allMethod);

		assertAll(
				() -> assertTrue(beforeSql.contains(
						"from public.practice_result_utterances")),
				() -> assertTrue(beforeSql.contains(
						"session_id = #{sessionid}")),
				() -> assertTrue(beforeSql.contains(
						"utterance_no < #{beforeutteranceno}")),
				() -> assertTrue(beforeSql.endsWith(
						"order by utterance_no asc")),
				() -> assertTrue(allSql.contains(
						"from public.practice_result_utterances")),
				() -> assertFalse(allSql.contains("utterance_no <")),
				() -> assertTrue(allSql.endsWith(
						"order by utterance_no asc")),
				() -> assertTrue(beforeSql.contains(
						"cast(pronunciation_details as text)")),
				() -> assertEquals(
						"sessionId",
						beforeMethod.getParameters()[0]
								.getAnnotation(Param.class)
								.value()),
				() -> assertEquals(
						"beforeUtteranceNo",
						beforeMethod.getParameters()[1]
								.getAnnotation(Param.class)
								.value()),
				() -> assertEquals(
						"sessionId",
						allMethod.getParameters()[0]
								.getAnnotation(Param.class)
								.value()));
	}

	@Test
	void upsertsNormalResultWithGeneratedIdAndCompletePhonemes() {
		mapper.upsertCount = 1;
		PracticeResultUtterance utterance = normalUtterance(
				UUID.randomUUID(),
				2);

		repository.upsert(utterance);

		PracticeResultUtteranceRow row = mapper.upsertedRow;
		PronunciationDetailsJson details =
				codec.decodePronunciationDetails(
						row.pronunciationDetailsJson());
		PronunciationDetailsJson.Word word = details.words().get(0);
		PronunciationDetailsJson.Phoneme phoneme =
				word.phonemes().get(0);

		assertAll(
				() -> assertNotNull(row.id()),
				() -> assertEquals(utterance.sessionId(), row.sessionId()),
				() -> assertEquals(2, row.utteranceNo()),
				() -> assertEquals("Previous AI text.", row.aiText()),
				() -> assertEquals(score("91"), row.overallScore()),
				() -> assertEquals(4, word.index()),
				() -> assertEquals("good", word.text()),
				() -> assertEquals(score("84"), word.pronunciationScore()),
				() -> assertEquals(2, phoneme.index()),
				() -> assertEquals("g", phoneme.expectedPhoneme()),
				() -> assertEquals("k", phoneme.actualPhoneme()),
				() -> assertEquals(
						score("82"),
						phoneme.pronunciationScore()));
	}

	@Test
	void upsertsTooShortResultWithNullAiTextAndEmptyWords() {
		mapper.upsertCount = 1;
		PracticeResultUtterance utterance = tooShortUtterance(
				UUID.randomUUID(),
				1);

		repository.upsert(utterance);

		PracticeResultUtteranceRow row = mapper.upsertedRow;
		PronunciationDetailsJson details =
				codec.decodePronunciationDetails(
						row.pronunciationDetailsJson());

		assertAll(
				() -> assertNull(row.aiText()),
				() -> assertEquals(BigDecimal.ZERO, row.overallScore()),
				() -> assertEquals("过短，不予评分", row.feedbackSummary()),
				() -> assertEquals("", row.suggestedExpression()),
				() -> assertEquals("{\"words\":[]}",
						row.pronunciationDetailsJson()),
				() -> assertTrue(details.words().isEmpty()));
	}

	@Test
	void restoresOrderedRowsIncludingNormalAndTooShortDetails() {
		UUID sessionId = UUID.randomUUID();
		PracticeResultUtterance first = tooShortUtterance(sessionId, 1);
		PracticeResultUtterance second = normalUtterance(sessionId, 2);
		mapper.beforeRows = List.of(toRow(first), toRow(second));
		mapper.allRows = List.of(toRow(first), toRow(second));

		List<PracticeResultUtterance> history =
				repository.findBefore(sessionId, 3);
		List<PracticeResultUtterance> all =
				repository.findAll(sessionId);
		Phoneme phoneme = all.get(1).words().get(0).phonemes().get(0);

		assertAll(
				() -> assertEquals(3, mapper.beforeUtteranceNo),
				() -> assertEquals(sessionId, mapper.beforeSessionId),
				() -> assertEquals(sessionId, mapper.allSessionId),
				() -> assertEquals(List.of(1, 2), history.stream()
						.map(PracticeResultUtterance::utteranceNo)
						.toList()),
				() -> assertEquals(List.of(1, 2), all.stream()
						.map(PracticeResultUtterance::utteranceNo)
						.toList()),
				() -> assertTrue(all.get(0).words().isEmpty()),
				() -> assertEquals("g", phoneme.expectedPhoneme()),
				() -> assertEquals("k", phoneme.actualPhoneme()),
				() -> assertThrows(
						UnsupportedOperationException.class,
						() -> all.add(first)));
	}

	@Test
	void rejectsInvalidRepositoryInputsWithStableRequestError() {
		EvaluationException nullUpsert = assertThrows(
				EvaluationException.class,
				() -> repository.upsert(null));
		EvaluationException nullBeforeSession = assertThrows(
				EvaluationException.class,
				() -> repository.findBefore(null, 1));
		EvaluationException invalidBeforeNumber = assertThrows(
				EvaluationException.class,
				() -> repository.findBefore(UUID.randomUUID(), 0));
		EvaluationException nullAllSession = assertThrows(
				EvaluationException.class,
				() -> repository.findAll(null));

		assertAll(
				() -> assertRequestFailure(nullUpsert),
				() -> assertRequestFailure(nullBeforeSession),
				() -> assertRequestFailure(invalidBeforeNumber),
				() -> assertRequestFailure(nullAllSession),
				() -> assertNull(mapper.upsertedRow));
	}

	@Test
	void rejectsInvalidDomainFieldsBeforeDatabaseAccess() {
		UUID sessionId = UUID.randomUUID();

		assertAll(
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> utterance(
								sessionId,
								0,
								"answer",
								null,
								score("80"),
								List.of())),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> utterance(
								sessionId,
								1,
								" ",
								null,
								score("80"),
								List.of())),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> utterance(
								sessionId,
								1,
								"answer",
								null,
								score("101"),
								List.of())),
				() -> assertThrows(
						NullPointerException.class,
						() -> utterance(
								sessionId,
								1,
								"answer",
								null,
								score("80"),
								null)),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new Phoneme(
								0,
								" ",
								"k",
								score("80"))));
	}

	@Test
	void convertsMapperRowsAndCodecFailuresWithoutLeakingCause() {
		UUID sessionId = UUID.randomUUID();
		mapper.upsertCount = 0;
		EvaluationException unexpectedCount = assertThrows(
				EvaluationException.class,
				() -> repository.upsert(normalUtterance(sessionId, 1)));

		mapper.upsertFailure = new IllegalStateException(
				"jdbc:postgresql://secret-host/private");
		EvaluationException upsertFailure = assertThrows(
				EvaluationException.class,
				() -> repository.upsert(normalUtterance(sessionId, 1)));

		mapper.beforeFailure = new IllegalStateException(
				"SQL includes private transcript");
		EvaluationException beforeFailure = assertThrows(
				EvaluationException.class,
				() -> repository.findBefore(sessionId, 2));

		mapper.allRows = new ArrayList<>();
		mapper.allRows.add(invalidJsonRow(sessionId));
		EvaluationException decodeFailure = assertThrows(
				EvaluationException.class,
				() -> repository.findAll(sessionId));

		assertAll(
				() -> assertPersistenceFailure(unexpectedCount),
				() -> assertPersistenceFailure(upsertFailure),
				() -> assertPersistenceFailure(beforeFailure),
				() -> assertPersistenceFailure(decodeFailure));
	}

	private PracticeResultUtterance normalUtterance(
			UUID sessionId,
			int utteranceNo) {
		Phoneme phoneme = new Phoneme(
				2,
				"g",
				"k",
				score("82"));
		Word word = new Word(
				4,
				"good",
				score("84"),
				List.of(phoneme));
		return new PracticeResultUtterance(
				sessionId,
				utteranceNo,
				"I would like some coffee.",
				" Previous AI text. ",
				score("91"),
				score("87"),
				score("73"),
				score("89"),
				score("88"),
				score("90"),
				"表达清晰",
				"I would like some coffee, please.",
				List.of(word));
	}

	private PracticeResultUtterance tooShortUtterance(
			UUID sessionId,
			int utteranceNo) {
		return new PracticeResultUtterance(
				sessionId,
				utteranceNo,
				"Yes",
				" ",
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				"过短，不予评分",
				"",
				List.of());
	}

	private PracticeResultUtterance utterance(
			UUID sessionId,
			int utteranceNo,
			String transcript,
			String aiText,
			BigDecimal overallScore,
			List<Word> words) {
		return new PracticeResultUtterance(
				sessionId,
				utteranceNo,
				transcript,
				aiText,
				overallScore,
				score("80"),
				score("80"),
				score("80"),
				score("80"),
				score("80"),
				"反馈",
				"",
				words);
	}

	private PracticeResultUtteranceRow toRow(
			PracticeResultUtterance utterance) {
		PronunciationDetailsJson details = new PronunciationDetailsJson(
				utterance.words().stream()
						.map(word -> new PronunciationDetailsJson.Word(
								word.index(),
								word.text(),
								word.pronunciationScore(),
								word.phonemes().stream()
										.map(phoneme ->
												new PronunciationDetailsJson.Phoneme(
														phoneme.index(),
														phoneme.expectedPhoneme(),
														phoneme.actualPhoneme(),
														phoneme.pronunciationScore()))
										.toList()))
						.toList());
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
				codec.encodePronunciationDetails(details));
	}

	private PracticeResultUtteranceRow invalidJsonRow(UUID sessionId) {
		return new PracticeResultUtteranceRow(
				UUID.randomUUID(),
				sessionId,
				1,
				"private transcript",
				null,
				score("80"),
				score("80"),
				score("80"),
				score("80"),
				score("80"),
				score("80"),
				"private feedback",
				"private suggestion",
				"{\"words\":[{\"unexpected\":\"private\"}]}");
	}

	private String selectSql(Method method) {
		return normalizeSql(String.join(
				" ",
				method.getAnnotation(Select.class).value()));
	}

	private String normalizeSql(String sql) {
		return sql.replaceAll("\\s+", " ")
				.trim()
				.toLowerCase();
	}

	private void assertRequestFailure(EvaluationException exception) {
		assertAll(
				() -> assertEquals(
						EvaluationErrorCode.INVALID_REQUEST,
						exception.errorCode()),
				() -> assertNull(exception.getCause()));
	}

	private void assertPersistenceFailure(EvaluationException exception) {
		assertAll(
				() -> assertEquals(
						EvaluationErrorCode.PERSISTENCE_FAILED,
						exception.errorCode()),
				() -> assertFalse(exception.getMessage().contains("secret-host")),
				() -> assertFalse(exception.getMessage().contains("private")),
				() -> assertNull(exception.getCause()));
	}

	private static BigDecimal score(String value) {
		return new BigDecimal(value);
	}

	/**
	 * 不依赖动态代理的可控 Mapper stub，用于记录参数和模拟数据库失败。
	 */
	private static final class StubPracticeResultUtteranceMapper
			implements PracticeResultUtteranceMapper {

		private int upsertCount;
		private PracticeResultUtteranceRow upsertedRow;
		private RuntimeException upsertFailure;
		private UUID beforeSessionId;
		private int beforeUtteranceNo;
		private List<PracticeResultUtteranceRow> beforeRows = List.of();
		private RuntimeException beforeFailure;
		private UUID allSessionId;
		private List<PracticeResultUtteranceRow> allRows = List.of();
		private RuntimeException allFailure;

		@Override
		public int upsert(PracticeResultUtteranceRow row) {
			if (upsertFailure != null) {
				throw upsertFailure;
			}
			upsertedRow = row;
			return upsertCount;
		}

		@Override
		public List<PracticeResultUtteranceRow> selectBefore(
				UUID sessionId,
				int beforeNumber) {
			if (beforeFailure != null) {
				throw beforeFailure;
			}
			beforeSessionId = sessionId;
			beforeUtteranceNo = beforeNumber;
			return beforeRows;
		}

		@Override
		public List<PracticeResultUtteranceRow> selectAll(UUID sessionId) {
			if (allFailure != null) {
				throw allFailure;
			}
			allSessionId = sessionId;
			return allRows;
		}
	}
}
