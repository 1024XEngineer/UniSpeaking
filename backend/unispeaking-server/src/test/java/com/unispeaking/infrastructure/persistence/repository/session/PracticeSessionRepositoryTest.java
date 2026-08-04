package com.unispeaking.infrastructure.persistence.repository.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.persistence.entity.session.PracticeSessionEntity;
import com.unispeaking.infrastructure.persistence.mapper.session.PracticeSessionMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PracticeSessionRepositoryTest {

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

		verify(mapper).insert(any(PracticeSessionEntity.class));
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
