package com.unispeaking.infrastructure.persistence.evaluation.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.infrastructure.persistence.evaluation.mapper.PracticeSessionEvaluationMapper;
import com.unispeaking.infrastructure.persistence.evaluation.session.PracticeSessionEvaluationContext;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/**
 * 验证会话上下文 SQL 契约、开放模式和仓储异常边界。
 */
class PracticeSessionEvaluationRepositoryTest {

	@Test
	void mapperUsesExactEightFieldLeftJoinSqlWithoutDeletedFilter()
			throws Exception {
		Method method = PracticeSessionEvaluationMapper.class.getMethod(
				"findBySessionId",
				UUID.class);
		Select select = method.getAnnotation(Select.class);
		String sql = normalizeSql(String.join(" ", select.value()));
		String expected = normalizeSql("""
				SELECT
				    ps.id AS "sessionId",
				    ps.user_id AS "userId",
				    ps.practice_mode AS "practiceMode",
				    ps.status AS status,
				    cs.background AS background,
				    cs.ai_role AS "aiRole",
				    cs.user_role AS "userRole",
				    cs.learning_goal AS "learningGoal"
				FROM public.practice_sessions ps
				LEFT JOIN public.custom_scenes cs ON cs.id = ps.scene_id
				WHERE ps.id = #{sessionId}
				""");
		Param parameter = method.getParameters()[0].getAnnotation(Param.class);

		assertAll(
				() -> assertEquals(expected, sql),
				() -> assertEquals("sessionId", parameter.value()),
				() -> assertFalse(sql.toLowerCase().contains("deleted_at")),
				() -> assertFalse(sql.toLowerCase().contains("update ")),
				() -> assertFalse(sql.toLowerCase().contains("insert ")),
				() -> assertFalse(sql.toLowerCase().contains("delete ")));
	}

	@Test
	void forwardsUuidAndReturnsOpenProfessionalMode() {
		UUID sessionId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		AtomicReference<UUID> capturedId = new AtomicReference<>();
		PracticeSessionEvaluationContext context =
				new PracticeSessionEvaluationContext(
						sessionId,
						userId,
						"IELTS_SPEAKING",
						"COMPLETED",
						"Academic speaking test",
						"Examiner",
						"Candidate",
						"Answer all sections");
		PracticeSessionEvaluationMapper mapper = requestedId -> {
			capturedId.set(requestedId);
			return context;
		};
		PracticeSessionEvaluationRepository repository =
				new PracticeSessionEvaluationRepository(mapper);

		Optional<PracticeSessionEvaluationContext> result =
				repository.findBySessionId(sessionId);

		assertAll(
				() -> assertTrue(result.isPresent()),
				() -> assertEquals(context, result.orElseThrow()),
				() -> assertEquals(sessionId, capturedId.get()),
				() -> assertEquals(
						"IELTS_SPEAKING",
						result.orElseThrow().practiceMode()));
	}

	@Test
	void supportsFreeChatWithoutSceneContext() {
		PracticeSessionEvaluationContext context =
				new PracticeSessionEvaluationContext(
						UUID.randomUUID(),
						UUID.randomUUID(),
						"FREE_CHAT",
						"ACTIVE",
						null,
						null,
						null,
						null);
		PracticeSessionEvaluationRepository repository =
				new PracticeSessionEvaluationRepository(id -> context);

		PracticeSessionEvaluationContext result =
				repository.findBySessionId(context.sessionId()).orElseThrow();

		assertAll(
				() -> assertEquals("FREE_CHAT", result.practiceMode()),
				() -> assertNull(result.background()),
				() -> assertNull(result.aiRole()),
				() -> assertNull(result.userRole()),
				() -> assertNull(result.learningGoal()));
	}

	@Test
	void returnsEmptyWhenSessionDoesNotExist() {
		PracticeSessionEvaluationRepository repository =
				new PracticeSessionEvaluationRepository(id -> null);

		assertTrue(
				repository.findBySessionId(UUID.randomUUID()).isEmpty());
	}

	@Test
	void convertsMapperFailureWithoutLeakingDetails() {
		PracticeSessionEvaluationRepository repository =
				new PracticeSessionEvaluationRepository(id -> {
					throw new IllegalStateException(
							"jdbc:postgresql://secret-host/evaluation");
				});

		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> repository.findBySessionId(UUID.randomUUID()));

		assertAll(
				() -> assertEquals(
						EvaluationErrorCode.PERSISTENCE_FAILED,
						exception.errorCode()),
				() -> assertFalse(exception.getMessage().contains("secret-host")),
				() -> assertFalse(exception.getMessage().contains("jdbc")),
				() -> assertNull(exception.getCause()));
	}

	@Test
	void validatesRequiredContextAndRepositoryInput() {
		UUID sessionId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		PracticeSessionEvaluationRepository repository =
				new PracticeSessionEvaluationRepository(id -> null);

		assertAll(
				() -> assertThrows(
						NullPointerException.class,
						() -> new PracticeSessionEvaluationContext(
								null,
								userId,
								"FREE_CHAT",
								"ACTIVE",
								null,
								null,
								null,
								null)),
				() -> assertThrows(
						NullPointerException.class,
						() -> new PracticeSessionEvaluationContext(
								sessionId,
								null,
								"FREE_CHAT",
								"ACTIVE",
								null,
								null,
								null,
								null)),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new PracticeSessionEvaluationContext(
								sessionId,
								userId,
								" ",
								"ACTIVE",
								null,
								null,
								null,
								null)),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new PracticeSessionEvaluationContext(
								sessionId,
								userId,
								"FREE_CHAT",
								" ",
								null,
								null,
								null,
								null)),
				() -> assertEvaluationError(
						EvaluationErrorCode.INVALID_REQUEST,
						() -> repository.findBySessionId(null)));
	}

	private static void assertEvaluationError(
			EvaluationErrorCode expectedCode,
			Runnable operation) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				operation::run);
		assertEquals(expectedCode, exception.errorCode());
	}

	private static String normalizeSql(String sql) {
		return sql.replaceAll("\\s+", " ").trim();
	}
}
