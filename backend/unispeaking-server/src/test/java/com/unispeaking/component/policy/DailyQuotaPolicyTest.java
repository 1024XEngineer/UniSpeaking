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
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
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

	@Test
	void rejectsSuspendedGovernanceEntitlementBeforeStartingPractice() {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:quota-governance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("create table user_entitlements (user_id uuid, quota_date date, quota_seconds numeric(12,3), used_seconds numeric(12,3), status varchar(32), updated_at timestamp with time zone)");
		jdbc.update("insert into user_entitlements values (?, current_date, 600, 0, 'suspended', current_timestamp)", userId);
		var governedPolicy = new DailyQuotaPolicy(repository, jdbc);

		BusinessException exception = assertThrows(BusinessException.class,
				() -> governedPolicy.assertWithinQuota(userId.toString(), SceneType.INTERVIEW_SCENE, 5));

		assertEquals("USER_ENTITLEMENT_SUSPENDED", exception.code());
	}

	@Test
	void rejectsSuspendedGovernanceEntitlementAfterTheQuotaDateChanges() {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:quota-governance-rollover;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("create table user_entitlements (user_id uuid, quota_date date, quota_seconds numeric(12,3), used_seconds numeric(12,3), status varchar(32), updated_at timestamp with time zone)");
		jdbc.update("insert into user_entitlements values (?, dateadd('DAY', -1, current_date), 600, 600, 'suspended', current_timestamp)", userId);
		var governedPolicy = new DailyQuotaPolicy(repository, jdbc);

		BusinessException exception = assertThrows(BusinessException.class,
				() -> governedPolicy.assertWithinQuota(userId.toString(), SceneType.INTERVIEW_SCENE, 5));

		assertEquals("USER_ENTITLEMENT_SUSPENDED", exception.code());
	}
}
