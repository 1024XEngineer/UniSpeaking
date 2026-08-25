package com.unispeaking.component.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.domain.vo.task.AsyncTaskStatus;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsPartEvaluationEntity;
import com.unispeaking.infrastructure.persistence.repository.evaluation.IeltsEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.service.auth.AuthService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IeltsEvaluationCoordinatorTest {

	private static final String USER_ID = "3d8f80be-6390-4db9-a6cf-c10a0145d4c3";
	private static final String IELTS_ID = "ielts_part_1";
	private static final String SESSION_ID = "session_part_1";

	private final EvaluationProcessor processor = mock(EvaluationProcessor.class);
	private final IeltsEvaluationRepository evaluationRepository =
			mock(IeltsEvaluationRepository.class);
	private final IeltsPracticeRepository practiceRepository =
			mock(IeltsPracticeRepository.class);
	private final PracticeSessionRepository sessionRepository =
			mock(PracticeSessionRepository.class);
	private final AuthService authService = mock(AuthService.class);
	private final AtomicReference<IeltsPartEvaluationEntity> stored =
			new AtomicReference<>();
	private IeltsEvaluationCoordinator coordinator;

	@BeforeEach
	void setUp() {
		UUID userId = UUID.fromString(USER_ID);
		when(authService.requireUserId(null)).thenReturn(USER_ID);
		when(practiceRepository.findPractice(IELTS_ID)).thenReturn(Optional.of(
				new IeltsPracticeRecord(
						IELTS_ID,
						userId,
						IeltsMode.PART_PRACTICE,
						IeltsPart.PART_1,
						"topic-1",
						new IeltsContent(List.of(), List.of(), List.of()))));
		when(sessionRepository.findBySceneId(IELTS_ID)).thenReturn(List.of(
				new PracticeSessionRecord(
						SESSION_ID,
						userId,
						IELTS_ID,
						SceneType.IELTS_SCENE,
						SessionStatus.COMPLETED,
						Instant.parse("2026-08-24T01:00:00Z"),
						Instant.parse("2026-08-24T01:10:00Z"))));
		when(evaluationRepository.ensurePartPending(
				IELTS_ID,
				SESSION_ID,
				IeltsPart.PART_1)).thenAnswer(invocation -> {
			if (stored.get() == null || "FAILED".equals(stored.get().getEvaluationStatus())) {
				stored.set(entity("PENDING", null));
			}
			return stored.get();
		});
		when(evaluationRepository.findPart(SESSION_ID)).thenAnswer(
				invocation -> Optional.ofNullable(stored.get()));
		doAnswer(invocation -> {
			stored.set(entity("FAILED", invocation.getArgument(1)));
			return null;
		}).when(evaluationRepository).markPartFailed(anyString(), anyString());
		coordinator = new IeltsEvaluationCoordinator(
				processor,
				evaluationRepository,
				practiceRepository,
				sessionRepository,
				authService,
				Runnable::run);
	}

	@Test
	void completesTaskUsingExplicitUserIdentity() {
		doAnswer(invocation -> {
			stored.set(entity("COMPLETED", null));
			return null;
		}).when(processor).generateIeltsEvaluationForUser(
				IELTS_ID,
				SESSION_ID,
				USER_ID);

		var response = coordinator.submit(IELTS_ID, SESSION_ID);

		assertEquals(AsyncTaskStatus.COMPLETED, response.status());
		assertEquals(IeltsPart.PART_1, response.result().part());
		verify(processor).generateIeltsEvaluationForUser(
				IELTS_ID,
				SESSION_ID,
				USER_ID);
	}

	@Test
	void recordsBackgroundFailureAsFailedTask() {
		doAnswer(invocation -> {
			throw new IllegalStateException("provider unavailable");
		}).when(processor).generateIeltsEvaluationForUser(
				IELTS_ID,
				SESSION_ID,
				USER_ID);

		var response = coordinator.submit(IELTS_ID, SESSION_ID);

		assertEquals(AsyncTaskStatus.FAILED, response.status());
		assertEquals("IELTS 评分失败，请稍后重试", response.failureReason());
	}

	@Test
	void doesNotRunCompletedTaskAgain() {
		stored.set(entity("COMPLETED", null));

		var response = coordinator.submit(IELTS_ID, SESSION_ID);

		assertEquals(AsyncTaskStatus.COMPLETED, response.status());
		verify(processor, never()).generateIeltsEvaluationForUser(
				anyString(),
				anyString(),
				anyString());
	}

	private IeltsPartEvaluationEntity entity(String status, String failureReason) {
		IeltsPartEvaluationEntity entity = new IeltsPartEvaluationEntity();
		entity.setPartEvaluationId("ielts_part_" + SESSION_ID);
		entity.setIeltsId(IELTS_ID);
		entity.setSessionId(SESSION_ID);
		entity.setPart(IeltsPart.PART_1.name());
		entity.setSummary("Part 1 diagnostic");
		entity.setStrengths(new String[0]);
		entity.setImprovements(new String[0]);
		entity.setRecommendedExpressions(new String[0]);
		entity.setEvaluationStatus(status);
		entity.setFailureReason(failureReason);
		entity.setUpdatedAt(OffsetDateTime.now());
		return entity;
	}
}
