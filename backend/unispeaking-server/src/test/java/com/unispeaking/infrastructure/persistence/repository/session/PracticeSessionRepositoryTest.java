package com.unispeaking.infrastructure.persistence.repository.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.infrastructure.persistence.entity.session.PracticeSessionEntity;
import com.unispeaking.infrastructure.persistence.mapper.session.PracticeSessionMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

class PracticeSessionRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(
						new MybatisConfiguration(),
						"practice-session-repository-test"),
				PracticeSessionEntity.class);
	}

	@Test
	void createsPracticeSessionWithBusinessFields() {
		PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
		when(mapper.insert(any(PracticeSessionEntity.class))).thenReturn(1);
		PracticeSessionRepository repository =
				new PracticeSessionRepository(mapper);
		UUID userId = UUID.randomUUID();

		repository.create(new PracticeSessionRecord(
				"custom_session_1",
				userId,
				"custom_scene1",
				SceneType.CUSTOM_SCENE,
				SessionStatus.CREATED,
				Instant.parse("2026-08-03T02:00:00Z"),
				null));

		ArgumentCaptor<PracticeSessionEntity> captor =
				ArgumentCaptor.forClass(PracticeSessionEntity.class);
		verify(mapper).insert(captor.capture());
		assertEquals("custom_session_1", captor.getValue().getSessionId());
		assertEquals(userId, captor.getValue().getUserId());
		assertEquals("custom_scene1", captor.getValue().getSceneId());
		assertEquals("CUSTOM_SCENE", captor.getValue().getSceneType());
		assertEquals("CREATED", captor.getValue().getStatus());
		assertNull(captor.getValue().getEndedAt());
		assertEquals(captor.getValue().getCreatedAt(),
				captor.getValue().getUpdatedAt());
	}

	@Test
	void mapsCompletedSessionsFromRequestedWindow() {
		PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
		PracticeSessionEntity entity = new PracticeSessionEntity();
		entity.setSessionId("freechat_session_1");
		entity.setUserId(UUID.randomUUID());
		entity.setSceneId("freechat_scene1");
		entity.setSceneType(SceneType.FREE_CHAT.name());
		entity.setStatus(SessionStatus.COMPLETED.name());
		entity.setStartedAt(Instant.parse("2026-08-03T02:00:00Z").atOffset(java.time.ZoneOffset.UTC));
		entity.setEndedAt(Instant.parse("2026-08-03T02:05:00Z").atOffset(java.time.ZoneOffset.UTC));
		when(mapper.selectList(any())).thenReturn(List.of(entity));
		PracticeSessionRepository repository =
				new PracticeSessionRepository(mapper);

		List<PracticeSessionRecord> records = repository.findCompletedOverlapping(
				entity.getUserId(),
				Instant.parse("2026-08-03T00:00:00Z"),
				Instant.parse("2026-08-04T00:00:00Z"));

		assertEquals(1, records.size());
		assertEquals(entity.getStartedAt().toInstant(), records.getFirst().startedAt());
		assertEquals(entity.getEndedAt().toInstant(), records.getFirst().endedAt());
	}

	@Test
	void completesAndFailsOnlyOwnedNonTerminalSessions() {
		PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
		when(mapper.update(isNull(), any(LambdaUpdateWrapper.class)))
				.thenReturn(1);
		PracticeSessionRepository repository =
				new PracticeSessionRepository(mapper);
		UUID userId = UUID.randomUUID();
		Instant completedAt = Instant.parse("2026-08-04T09:00:00Z");
		Instant failedAt = completedAt.plusSeconds(30);

		repository.complete("session_completed", userId, completedAt);
		repository.fail("session_failed", userId, failedAt);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<LambdaUpdateWrapper<PracticeSessionEntity>> captor =
				ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
		verify(mapper, org.mockito.Mockito.times(2))
				.update(isNull(), captor.capture());
		List<LambdaUpdateWrapper<PracticeSessionEntity>> updates =
				captor.getAllValues();
		assertTerminalUpdate(
				updates.get(0),
				"session_completed",
				userId,
				"COMPLETED",
				completedAt.atOffset(java.time.ZoneOffset.UTC));
		assertTerminalUpdate(
				updates.get(1),
				"session_failed",
				userId,
				"FAILED",
				failedAt.atOffset(java.time.ZoneOffset.UTC));
	}

	@Test
	void persistsTheProviderSessionIdUsedToBindOfficialSlsUsage() {
		PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
		when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
		PracticeSessionRepository repository = new PracticeSessionRepository(mapper);
		UUID userId = UUID.randomUUID();

		repository.bindProviderSession("session_local_1", userId, "sess_provider_1");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<LambdaUpdateWrapper<PracticeSessionEntity>> captor =
				ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
		verify(mapper).update(isNull(), captor.capture());
		String condition = captor.getValue().getSqlSegment().toLowerCase(java.util.Locale.ROOT);
		String assignments = captor.getValue().getSqlSet().toLowerCase(java.util.Locale.ROOT);
		assertTrue(condition.contains("session_id ="), condition);
		assertTrue(condition.contains("user_id ="), condition);
		assertTrue(assignments.contains("provider_session_id="), assignments);
		assertTrue(captor.getValue().getParamNameValuePairs().values().containsAll(List.of(
				"session_local_1", userId, "sess_provider_1")));
	}

	@Test
	void repeatedTerminalUpdateIsIdempotentOnlyForTheSameStatus() {
		PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
		when(mapper.update(isNull(), any(LambdaUpdateWrapper.class)))
				.thenReturn(0);
		when(mapper.selectCount(any(Wrapper.class))).thenReturn(1L, 0L);
		PracticeSessionRepository repository =
				new PracticeSessionRepository(mapper);
		UUID userId = UUID.randomUUID();
		Instant endedAt = Instant.parse("2026-08-04T09:00:00Z");

		repository.complete("already_completed", userId, endedAt);
		BusinessException rejected = assertThrows(
				BusinessException.class,
				() -> repository.fail("missing", userId, endedAt));

		assertEquals("PRACTICE_SESSION_PERSISTENCE_FAILED", rejected.code());
		verify(mapper, org.mockito.Mockito.times(2))
				.selectCount(any(Wrapper.class));
	}

	@Test
	void mapsNullEndTimeAndTranslatesAllDatabaseFailures() {
		PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
		PracticeSessionEntity entity = new PracticeSessionEntity();
		entity.setSessionId("active_session");
		entity.setUserId(UUID.randomUUID());
		entity.setSceneId("custom_1");
		entity.setSceneType(SceneType.CUSTOM_SCENE.name());
		entity.setStatus(SessionStatus.CREATED.name());
		entity.setStartedAt(Instant.parse("2026-08-04T08:00:00Z")
				.atOffset(java.time.ZoneOffset.UTC));
		entity.setEndedAt(null);
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity));
		PracticeSessionRepository repository =
				new PracticeSessionRepository(mapper);

		assertNull(repository.findCompletedOverlapping(
				entity.getUserId(),
				Instant.parse("2026-08-04T00:00:00Z"),
				Instant.parse("2026-08-05T00:00:00Z"))
				.getFirst().endedAt());

		when(mapper.selectList(any(Wrapper.class)))
				.thenThrow(new IllegalStateException("select"));
		assertEquals("PRACTICE_SESSION_PERSISTENCE_FAILED",
				assertThrows(BusinessException.class,
						() -> repository.findCompletedOverlapping(
								entity.getUserId(),
								Instant.EPOCH,
								Instant.now())).code());

		when(mapper.insert(any(PracticeSessionEntity.class)))
				.thenThrow(new IllegalStateException("insert"));
		assertEquals("PRACTICE_SESSION_PERSISTENCE_FAILED",
				assertThrows(BusinessException.class,
						() -> repository.create(record(entity.getUserId()))).code());
	}

	@Test
	void mapsExistingEndTimeAndRejectsFailedWritesAndUpdates() {
		PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
		when(mapper.insert(any(PracticeSessionEntity.class))).thenReturn(1);
		PracticeSessionRepository repository =
				new PracticeSessionRepository(mapper);
		UUID userId = UUID.randomUUID();
		Instant endedAt = Instant.parse("2026-08-04T09:00:00Z");
		PracticeSessionRecord completed = new PracticeSessionRecord(
				"session_completed",
				userId,
				"custom_1",
				SceneType.CUSTOM_SCENE,
				SessionStatus.COMPLETED,
				endedAt.minusSeconds(60),
				endedAt);

		repository.create(completed);
		ArgumentCaptor<PracticeSessionEntity> captor =
				ArgumentCaptor.forClass(PracticeSessionEntity.class);
		verify(mapper).insert(captor.capture());
		assertEquals(endedAt,
				captor.getValue().getEndedAt().toInstant());

		when(mapper.insert(any(PracticeSessionEntity.class))).thenReturn(0);
		assertEquals("PRACTICE_SESSION_PERSISTENCE_FAILED",
				assertThrows(BusinessException.class,
						() -> repository.create(completed)).code());

		when(mapper.update(isNull(), any(LambdaUpdateWrapper.class)))
				.thenThrow(new IllegalStateException("update"));
		assertEquals("PRACTICE_SESSION_PERSISTENCE_FAILED",
				assertThrows(BusinessException.class,
						() -> repository.complete(
								completed.sessionId(), userId, endedAt)).code());
	}

	private PracticeSessionRecord record(UUID userId) {
		return new PracticeSessionRecord(
				"session_1",
				userId,
				"custom_1",
				SceneType.CUSTOM_SCENE,
				SessionStatus.CREATED,
				Instant.parse("2026-08-04T08:00:00Z"),
				null);
	}

	private void assertTerminalUpdate(
			LambdaUpdateWrapper<PracticeSessionEntity> update,
			String sessionId,
			UUID userId,
			String terminalStatus,
			java.time.OffsetDateTime endedAt) {
		String condition = update.getSqlSegment().toLowerCase(java.util.Locale.ROOT);
		String assignments = update.getSqlSet().toLowerCase(java.util.Locale.ROOT);
		assertTrue(condition.contains("session_id ="), condition);
		assertTrue(condition.contains("user_id ="), condition);
		assertTrue(condition.contains("status not in"), condition);
		assertTrue(assignments.contains("status="), assignments);
		assertTrue(assignments.contains("ended_at="), assignments);
		assertTrue(assignments.contains("updated_at="), assignments);
		assertTrue(update.getParamNameValuePairs().values().containsAll(List.of(
				sessionId,
				userId,
				"COMPLETED",
				"FAILED",
				terminalStatus,
				endedAt)));
	}

	@Test
	void listsAllCompletedSessionsForAchievementMetrics() {
		PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
		PracticeSessionEntity entity = completedEntity();
		when(mapper.selectList(any())).thenReturn(List.of(entity));
		PracticeSessionRepository repository = new PracticeSessionRepository(mapper);

		List<PracticeSessionRecord> records =
				repository.findCompletedByUserId(entity.getUserId());

		assertEquals(1, records.size());
		assertEquals("freechat_session_1", records.getFirst().sessionId());
		assertEquals(SessionStatus.COMPLETED, records.getFirst().status());
	}

	@Test
	void countsCompletedSessionsOnDateForSceneType() {
		PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
		when(mapper.selectCount(any(Wrapper.class))).thenReturn(3L);
		PracticeSessionRepository repository = new PracticeSessionRepository(mapper);
		UUID userId = UUID.randomUUID();

		long count = repository.countCompletedOnDate(
				userId,
				SceneType.INTERVIEW_SCENE,
				LocalDate.parse("2026-08-09"));

		assertEquals(3L, count);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Wrapper<PracticeSessionEntity>> captor =
				ArgumentCaptor.forClass(Wrapper.class);
		verify(mapper).selectCount(captor.capture());
		String sql = captor.getValue().getSqlSegment()
				.toLowerCase(java.util.Locale.ROOT);
		assertTrue(sql.contains("user_id ="), sql);
		assertTrue(sql.contains("scene_type ="), sql);
		assertTrue(sql.contains("status ="), sql);
		assertTrue(sql.contains("ended_at"), sql);
	}

	@Test
	void countOnDateTranslatesDatabaseFailures() {
		PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
		when(mapper.selectCount(any(Wrapper.class)))
				.thenThrow(new IllegalStateException("count"));
		PracticeSessionRepository repository = new PracticeSessionRepository(mapper);

		assertEquals(
				"PRACTICE_SESSION_PERSISTENCE_FAILED",
				assertThrows(BusinessException.class,
						() -> repository.countCompletedOnDate(
								UUID.randomUUID(),
								SceneType.INTERVIEW_SCENE,
								LocalDate.now())).code());
	}

	private PracticeSessionEntity completedEntity() {
		PracticeSessionEntity entity = new PracticeSessionEntity();
		entity.setSessionId("freechat_session_1");
		entity.setUserId(UUID.randomUUID());
		entity.setSceneId("freechat_scene1");
		entity.setSceneType(SceneType.FREE_CHAT.name());
		entity.setStatus(SessionStatus.COMPLETED.name());
		entity.setStartedAt(Instant.parse("2026-08-03T02:00:00Z")
				.atOffset(java.time.ZoneOffset.UTC));
		entity.setEndedAt(Instant.parse("2026-08-03T02:05:00Z")
				.atOffset(java.time.ZoneOffset.UTC));
		return entity;
	}
}
