package com.unispeaking.component.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyQuotaPolicyTest {

	private final PracticeSessionRepository repository =
			mock(PracticeSessionRepository.class);
	private final DailyQuotaPolicy policy = new DailyQuotaPolicy(repository);
	private final UUID userId = UUID.randomUUID();

	@Test
	void allowsWhenCompletedCountIsBelowLimit() {
		when(repository.countCompletedOnDate(
				eq(userId),
				eq(SceneType.INTERVIEW_SCENE),
				any(LocalDate.class)))
				.thenReturn(4L);

		assertDoesNotThrow(() -> policy.assertWithinQuota(
				userId.toString(),
				SceneType.INTERVIEW_SCENE,
				5));
	}

	@Test
	void rejectsWhenCompletedCountReachesLimit() {
		when(repository.countCompletedOnDate(
				eq(userId),
				eq(SceneType.INTERVIEW_SCENE),
				any(LocalDate.class)))
				.thenReturn(5L);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> policy.assertWithinQuota(
						userId.toString(),
						SceneType.INTERVIEW_SCENE,
						5));

		assertEquals(
				InterviewErrorCode.INTERVIEW_DAILY_LIMIT_REACHED,
				exception.code());
	}

	@Test
	void rejectsInvalidUserId() {
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> policy.assertWithinQuota(
						"not-a-uuid",
						SceneType.INTERVIEW_SCENE,
						5));

		assertEquals("INVALID_USER_ID", exception.code());
	}

	@Test
	void rejectsNullSceneType() {
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> policy.assertWithinQuota(userId.toString(), null, 5));

		assertEquals("SCENE_TYPE_REQUIRED", exception.code());
	}
}
