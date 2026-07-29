package com.unispeaking.infrastructure.persistence.evaluation.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.infrastructure.persistence.evaluation.feedback.PracticeResultFeedback;
import com.unispeaking.infrastructure.persistence.evaluation.feedback.PracticeResultFeedbackRow;
import com.unispeaking.infrastructure.persistence.evaluation.mapper.PracticeResultFeedbackMapper;
import com.unispeaking.infrastructure.persistence.evaluation.typehandler.PostgresTextArrayTypeHandler;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

/**
 * 验证整场反馈 SQL、TEXT[] 映射、不可变边界及仓储错误转换。
 */
class PracticeResultFeedbackRepositoryTest {

	private final StubPracticeResultFeedbackMapper mapper =
			new StubPracticeResultFeedbackMapper();
	private final PracticeResultFeedbackRepository repository =
			new PracticeResultFeedbackRepository(mapper);

	@Test
	void mapperUsesSessionUpsertAndExplicitTextArrayHandlers()
			throws Exception {
		Method upsertMethod = PracticeResultFeedbackMapper.class.getMethod(
				"upsert",
				PracticeResultFeedbackRow.class);
		Method queryMethod = PracticeResultFeedbackMapper.class.getMethod(
				"findBySessionId",
				UUID.class);
		String insertSql = normalizeSql(String.join(
				" ",
				upsertMethod.getAnnotation(Insert.class).value()));
		String selectSql = normalizeSql(String.join(
				" ",
				queryMethod.getAnnotation(Select.class).value()));
		Param upsertParameter =
				upsertMethod.getParameters()[0].getAnnotation(Param.class);
		Param queryParameter =
				queryMethod.getParameters()[0].getAnnotation(Param.class);
		ConstructorArgs constructorArgs =
				queryMethod.getAnnotation(ConstructorArgs.class);

		assertAll(
				() -> assertTrue(insertSql.contains(
						"insert into public.practice_result_feedbacks")),
				() -> assertTrue(insertSql.contains(
						"on conflict (session_id) do update")),
				() -> assertTrue(insertSql.contains(
						"updated_at = current_timestamp")),
				() -> assertEquals(
						2,
						countOccurrences(
								insertSql,
								"typehandler=com.unispeaking.infrastructure.persistence.evaluation.typehandler.postgrestextarraytypehandler")),
				() -> assertTrue(selectSql.contains(
						"from public.practice_result_feedbacks")),
				() -> assertTrue(selectSql.contains(
						"where session_id = #{sessionid}")),
				() -> assertEquals("feedback", upsertParameter.value()),
				() -> assertEquals("sessionId", queryParameter.value()),
				() -> assertArrayConstructor(
						constructorArgs,
						"strengths"),
				() -> assertArrayConstructor(
						constructorArgs,
						"improvements"));
	}

	@Test
	void feedbackNormalizesTextAndOwnsImmutableLists() {
		List<String> strengths =
				new ArrayList<>(List.of("  表达清晰  ", "互动自然"));
		List<String> improvements =
				new ArrayList<>(List.of("  增加细节  "));

		PracticeResultFeedback feedback = new PracticeResultFeedback(
				UUID.randomUUID(),
				"  整体表现稳定  ",
				strengths,
				improvements);
		strengths.clear();
		improvements.add("外部修改");

		assertAll(
				() -> assertEquals("整体表现稳定", feedback.summary()),
				() -> assertEquals(
						List.of("表达清晰", "互动自然"),
						feedback.strengths()),
				() -> assertEquals(
						List.of("增加细节"),
						feedback.improvements()),
				() -> assertThrows(
						UnsupportedOperationException.class,
						() -> feedback.strengths().add("禁止修改")),
				() -> assertThrows(
						UnsupportedOperationException.class,
						() -> feedback.improvements().clear()));
	}

	@Test
	void feedbackRejectsMissingBlankAndOutOfRangeValues() {
		UUID sessionId = UUID.randomUUID();

		assertAll(
				() -> assertThrows(
						NullPointerException.class,
						() -> new PracticeResultFeedback(
								null,
								"总结",
								List.of("优势"),
								List.of("建议"))),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new PracticeResultFeedback(
								sessionId,
								" ",
								List.of("优势"),
								List.of("建议"))),
				() -> assertThrows(
						NullPointerException.class,
						() -> new PracticeResultFeedback(
								sessionId,
								"总结",
								null,
								List.of("建议"))),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new PracticeResultFeedback(
								sessionId,
								"总结",
								List.of(),
								List.of("建议"))),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new PracticeResultFeedback(
								sessionId,
								"总结",
								List.of("一", "二", "三", "四"),
								List.of("建议"))),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new PracticeResultFeedback(
								sessionId,
								"总结",
								List.of(" "),
								List.of("建议"))));
	}

	@Test
	void rowDefensivelyCopiesDatabaseArrays() {
		String[] strengths = {"表达清晰"};
		String[] improvements = {"补充细节"};
		PracticeResultFeedbackRow row = new PracticeResultFeedbackRow(
				UUID.randomUUID(),
				"总结",
				strengths,
				improvements);

		strengths[0] = "外部修改";
		improvements[0] = "外部修改";
		String[] returnedStrengths = row.strengths();
		String[] returnedImprovements = row.improvements();
		returnedStrengths[0] = "再次修改";
		returnedImprovements[0] = "再次修改";

		assertAll(
				() -> assertArrayEquals(
						new String[] {"表达清晰"},
						row.strengths()),
				() -> assertArrayEquals(
						new String[] {"补充细节"},
						row.improvements()));
	}

	@Test
	void upsertConvertsListsToIndependentArrays() {
		PracticeResultFeedback feedback = feedback();
		mapper.upsertCount = 1;

		repository.upsert(feedback);
		String[] capturedStrengths = mapper.upsertedRow.strengths();
		capturedStrengths[0] = "外部修改";

		assertAll(
				() -> assertEquals(
						feedback.sessionId(),
						mapper.upsertedRow.sessionId()),
				() -> assertEquals(
						feedback.summary(),
						mapper.upsertedRow.summary()),
				() -> assertArrayEquals(
						feedback.strengths().toArray(String[]::new),
						mapper.upsertedRow.strengths()),
				() -> assertArrayEquals(
						feedback.improvements().toArray(String[]::new),
						mapper.upsertedRow.improvements()));
	}

	@Test
	void queryReturnsImmutableFeedbackAndEmptyForMissingRow() {
		PracticeResultFeedback expected = feedback();
		mapper.selectedRow = new PracticeResultFeedbackRow(
				expected.sessionId(),
				expected.summary(),
				expected.strengths().toArray(String[]::new),
				expected.improvements().toArray(String[]::new));

		Optional<PracticeResultFeedback> found =
				repository.findBySessionId(expected.sessionId());
		mapper.selectedRow = null;
		Optional<PracticeResultFeedback> missing =
				repository.findBySessionId(expected.sessionId());

		assertAll(
				() -> assertEquals(Optional.of(expected), found),
				() -> assertTrue(missing.isEmpty()),
				() -> assertThrows(
						UnsupportedOperationException.class,
						() -> found.orElseThrow().strengths().clear()));
	}

	@Test
	void validatesRepositoryInputAndAffectedRows() {
		mapper.upsertCount = 0;

		EvaluationException nullFeedback = assertThrows(
				EvaluationException.class,
				() -> repository.upsert(null));
		EvaluationException nullSession = assertThrows(
				EvaluationException.class,
				() -> repository.findBySessionId(null));
		EvaluationException zeroRows = assertThrows(
				EvaluationException.class,
				() -> repository.upsert(feedback()));
		mapper.upsertCount = 2;
		EvaluationException multipleRows = assertThrows(
				EvaluationException.class,
				() -> repository.upsert(feedback()));

		assertAll(
				() -> assertError(
						EvaluationErrorCode.INVALID_REQUEST,
						nullFeedback),
				() -> assertError(
						EvaluationErrorCode.INVALID_REQUEST,
						nullSession),
				() -> assertError(
						EvaluationErrorCode.PERSISTENCE_FAILED,
						zeroRows),
				() -> assertError(
						EvaluationErrorCode.PERSISTENCE_FAILED,
						multipleRows));
	}

	@Test
	void hidesMapperAndCorruptRowDetails() {
		mapper.upsertFailure = new IllegalStateException(
				"SQL contains private feedback");
		EvaluationException upsertFailure = assertThrows(
				EvaluationException.class,
				() -> repository.upsert(feedback()));
		mapper.upsertFailure = null;
		mapper.queryFailure = new IllegalStateException(
				"jdbc:postgresql://secret-host/evaluation");
		EvaluationException queryFailure = assertThrows(
				EvaluationException.class,
				() -> repository.findBySessionId(UUID.randomUUID()));
		mapper.queryFailure = null;
		mapper.selectedRow = new PracticeResultFeedbackRow(
				UUID.randomUUID(),
				"总结",
				new String[0],
				new String[] {"建议"});
		EvaluationException corruptRow = assertThrows(
				EvaluationException.class,
				() -> repository.findBySessionId(UUID.randomUUID()));

		assertAll(
				() -> assertPersistenceFailure(upsertFailure),
				() -> assertPersistenceFailure(queryFailure),
				() -> assertPersistenceFailure(corruptRow));
	}

	private static void assertArrayConstructor(
			ConstructorArgs constructorArgs,
			String column) {
		Arg argument = List.of(constructorArgs.value())
				.stream()
				.filter(candidate -> candidate.column().equals(column))
				.findFirst()
				.orElseThrow();
		assertAll(
				() -> assertEquals(String[].class, argument.javaType()),
				() -> assertEquals(JdbcType.ARRAY, argument.jdbcType()),
				() -> assertEquals(
						PostgresTextArrayTypeHandler.class,
						argument.typeHandler()));
	}

	private static void assertError(
			EvaluationErrorCode expected,
			EvaluationException exception) {
		assertAll(
				() -> assertEquals(expected, exception.errorCode()),
				() -> assertNull(exception.getCause()));
	}

	private static void assertPersistenceFailure(
			EvaluationException exception) {
		assertAll(
				() -> assertError(
						EvaluationErrorCode.PERSISTENCE_FAILED,
						exception),
				() -> assertFalse(
						exception.getMessage().contains("private")),
				() -> assertFalse(
						exception.getMessage().contains("secret-host")),
				() -> assertFalse(
						exception.getMessage().contains("jdbc")));
	}

	private static PracticeResultFeedback feedback() {
		return new PracticeResultFeedback(
				UUID.randomUUID(),
				"整体表现稳定",
				List.of("表达清晰", "互动自然"),
				List.of("增加细节"));
	}

	private static int countOccurrences(
			String text,
			String target) {
		int count = 0;
		int start = 0;
		while ((start = text.indexOf(target, start)) >= 0) {
			count++;
			start += target.length();
		}
		return count;
	}

	private static String normalizeSql(String sql) {
		return sql.replaceAll("\\s+", " ")
				.trim()
				.toLowerCase();
	}

	/**
	 * 可控 Mapper stub，记录仓储参数并模拟数据库行数及失败。
	 */
	private static final class StubPracticeResultFeedbackMapper
			implements PracticeResultFeedbackMapper {

		private PracticeResultFeedbackRow upsertedRow;
		private PracticeResultFeedbackRow selectedRow;
		private RuntimeException upsertFailure;
		private RuntimeException queryFailure;
		private int upsertCount;

		@Override
		public int upsert(PracticeResultFeedbackRow feedback) {
			if (upsertFailure != null) {
				throw upsertFailure;
			}
			upsertedRow = feedback;
			return upsertCount;
		}

		@Override
		public PracticeResultFeedbackRow findBySessionId(UUID sessionId) {
			if (queryFailure != null) {
				throw queryFailure;
			}
			return selectedRow;
		}
	}
}
