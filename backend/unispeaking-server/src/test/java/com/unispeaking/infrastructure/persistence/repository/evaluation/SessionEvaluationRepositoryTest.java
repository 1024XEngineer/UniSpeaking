package com.unispeaking.infrastructure.persistence.repository.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.dto.asset.SessionEvaluationRecord;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.po.achievement.AchievementEvaluationFact;
import com.unispeaking.infrastructure.persistence.entity.evaluation.SessionEvaluationEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.SessionEvaluationMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SessionEvaluationRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
				SessionEvaluationEntity.class);
	}

	@Test
	void insertsNewEvaluationWithAllReportFields() {
		SessionEvaluationMapper mapper = mock(SessionEvaluationMapper.class);
		when(mapper.selectById("session-1")).thenReturn(null);
		when(mapper.insert(any(SessionEvaluationEntity.class))).thenReturn(1);
		SessionEvaluationRepository repository = new SessionEvaluationRepository(mapper);

		repository.save("scene-1", "session-1", report());

		ArgumentCaptor<SessionEvaluationEntity> captor =
				ArgumentCaptor.forClass(SessionEvaluationEntity.class);
		verify(mapper).insert(captor.capture());
		SessionEvaluationEntity saved = captor.getValue();
		assertEquals("scene-1", saved.getSceneId());
		assertEquals(new BigDecimal("91"), saved.getFinalScore());
		assertEquals(List.of("表达清楚"), List.of(saved.getStrengths()));
		assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
	}

	@Test
	void updatesExistingEvaluationAndKeepsCreatedTime() {
		SessionEvaluationMapper mapper = mock(SessionEvaluationMapper.class);
		SessionEvaluationEntity existing = entity("scene-1", "session-1");
		when(mapper.selectById("session-1")).thenReturn(existing);
		when(mapper.updateById(any(SessionEvaluationEntity.class))).thenReturn(1);
		SessionEvaluationRepository repository = new SessionEvaluationRepository(mapper);

		repository.save("scene-1", "session-1", report());

		ArgumentCaptor<SessionEvaluationEntity> captor =
				ArgumentCaptor.forClass(SessionEvaluationEntity.class);
		verify(mapper).updateById(captor.capture());
		assertEquals(existing.getCreatedAt(), captor.getValue().getCreatedAt());
	}

	@Test
	void translatesInsertAndDatabaseFailures() {
		SessionEvaluationMapper mapper = mock(SessionEvaluationMapper.class);
		when(mapper.insert(any(SessionEvaluationEntity.class))).thenReturn(0);
		SessionEvaluationRepository repository = new SessionEvaluationRepository(mapper);

		EvaluationException rejected = assertThrows(
				EvaluationException.class,
				() -> repository.save("scene", "session", report()));
		assertEquals(EvaluationErrorCode.PERSISTENCE_FAILED, rejected.errorCode());

		when(mapper.selectById("broken")).thenThrow(new IllegalStateException("database"));
		EvaluationException failed = assertThrows(
				EvaluationException.class,
				() -> repository.find("broken"));
		assertEquals(EvaluationErrorCode.PERSISTENCE_FAILED, failed.errorCode());
	}

	@Test
	void findsReportAndHandlesMissingArrays() {
		SessionEvaluationMapper mapper = mock(SessionEvaluationMapper.class);
		SessionEvaluationEntity entity = entity("scene-1", "session-1");
		entity.setStrengths(null);
		entity.setImprovements(null);
		when(mapper.selectById("session-1")).thenReturn(entity);
		when(mapper.selectById("missing")).thenReturn(null);
		SessionEvaluationRepository repository = new SessionEvaluationRepository(mapper);

		DialogueReportResult result = repository.find("session-1").orElseThrow();

		assertEquals(new BigDecimal("91"), result.finalScore());
		assertTrue(result.strengths().isEmpty());
		assertTrue(result.improvements().isEmpty());
		assertTrue(repository.find("missing").isEmpty());
	}

	@Test
	void findsOnlyCompleteEvaluationRecords() {
		SessionEvaluationMapper mapper = mock(SessionEvaluationMapper.class);
		SessionEvaluationEntity valid = entity("scene-1", "session-1");
		SessionEvaluationEntity blankScene = entity(" ", "session-2");
		when(mapper.selectById("session-1")).thenReturn(valid);
		when(mapper.selectById("session-2")).thenReturn(blankScene);
		when(mapper.selectById("missing")).thenReturn(null);
		SessionEvaluationRepository repository = new SessionEvaluationRepository(mapper);

		SessionEvaluationRecord record = repository.findRecord("session-1").orElseThrow();

		assertEquals("scene-1", record.sceneId());
		assertEquals("session-1", record.sessionId());
		assertTrue(repository.findRecord("session-2").isEmpty());
		assertTrue(repository.findRecord("missing").isEmpty());
	}

	@Test
	void listsSceneRecordsAndCreationDates() {
		SessionEvaluationMapper mapper = mock(SessionEvaluationMapper.class);
		SessionEvaluationEntity first = entity("scene-1", "session-1");
		SessionEvaluationEntity second = entity("scene-1", "session-2");
		second.setCreatedAt(first.getCreatedAt().plusMinutes(5));
		when(mapper.selectList(any())).thenReturn(List.of(first, second));
		SessionEvaluationRepository repository = new SessionEvaluationRepository(mapper);

		assertEquals(2, repository.findBySceneId("scene-1").size());
		assertEquals(
				List.of(first.getCreatedAt(), second.getCreatedAt()),
				repository.findCreatedAtBySceneIdsBetween(
						List.of("scene-1"),
						first.getCreatedAt().minusDays(1),
						second.getCreatedAt().plusDays(1)));
		assertTrue(repository.findCreatedAtBySceneIdsBetween(
				List.of(), first.getCreatedAt(), second.getCreatedAt()).isEmpty());
		assertTrue(repository.findCreatedAtBySceneIdsBetween(
				null, first.getCreatedAt(), second.getCreatedAt()).isEmpty());
	}

	@Test
	void listsAchievementFactsByOwnedSessionsOrScenes() {
		SessionEvaluationMapper mapper = mock(SessionEvaluationMapper.class);
		SessionEvaluationEntity first = entity("scene-1", "session-1");
		when(mapper.selectList(any())).thenReturn(List.of(first));
		SessionEvaluationRepository repository = new SessionEvaluationRepository(mapper);

		List<AchievementEvaluationFact> facts = repository.findAchievementFacts(
				List.of("session-1", "session-1", " "),
				List.of("scene-1"));

		assertEquals(1, facts.size());
		assertEquals("session-1", facts.getFirst().sessionId());
		assertEquals(new BigDecimal("91"), facts.getFirst().finalScore());
		assertTrue(repository.findAchievementFacts(List.of(), null).isEmpty());
	}

	private DialogueReportResult report() {
		return new DialogueReportResult(
				new BigDecimal("90"),
				new BigDecimal("89"),
				new BigDecimal("88"),
				new BigDecimal("87"),
				new BigDecimal("86"),
				new BigDecimal("91"),
				"整体表现良好",
				List.of("表达清楚"),
				List.of("注意时态"));
	}

	private SessionEvaluationEntity entity(String sceneId, String sessionId) {
		DialogueReportResult report = report();
		SessionEvaluationEntity entity = new SessionEvaluationEntity();
		entity.setSceneId(sceneId);
		entity.setSessionId(sessionId);
		entity.setAccuracyScore(report.accuracyScore());
		entity.setFluencyScore(report.fluencyScore());
		entity.setGrammarScore(report.grammarScore());
		entity.setVocabularyScore(report.vocabularyScore());
		entity.setNaturalnessScore(report.naturalnessScore());
		entity.setFinalScore(report.finalScore());
		entity.setSummary(report.summary());
		entity.setStrengths(report.strengths().toArray(String[]::new));
		entity.setImprovements(report.improvements().toArray(String[]::new));
		entity.setCreatedAt(OffsetDateTime.of(
				2026, 8, 3, 3, 0, 0, 0, ZoneOffset.UTC));
		entity.setUpdatedAt(entity.getCreatedAt());
		return entity;
	}
}
