package com.unispeaking.infrastructure.persistence.repository.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.common.persistence.codec.evaluation.EvaluationJsonbCodec;
import com.unispeaking.infrastructure.persistence.entity.evaluation.CustomTurnEvaluation;
import com.unispeaking.infrastructure.persistence.entity.evaluation.TurnEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationDetailsJson;
import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationWordDetail;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.TurnEvaluationMapper;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class TurnEvaluationRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
				TurnEvaluationEntity.class);
	}

	@Test
	void insertsBySessionIdAndTurnNoWithoutSyntheticId() {
		TurnEvaluationMapper mapper = mock(TurnEvaluationMapper.class);
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
		when(mapper.insert(any(TurnEvaluationEntity.class))).thenReturn(1);
		TurnEvaluationRepository repository = repository(mapper);

		repository.upsert(evaluation());

		ArgumentCaptor<TurnEvaluationEntity> row =
				ArgumentCaptor.forClass(TurnEvaluationEntity.class);
		verify(mapper).insert(row.capture());
		assertEquals("custom_session_1", row.getValue().getSessionId());
		assertEquals(2, row.getValue().getTurnNo());
	}

	@Test
	void updatesByCompositeKeyInsteadOfUpdateById() {
		TurnEvaluationMapper mapper = mock(TurnEvaluationMapper.class);
		TurnEvaluationEntity existing = new TurnEvaluationEntity();
		existing.setSessionId("custom_session_1");
		existing.setTurnNo(2);
		existing.setCreatedAt(OffsetDateTime.parse("2026-07-31T10:00:00Z"));
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing);
		when(mapper.update(
				any(TurnEvaluationEntity.class),
				any(Wrapper.class))).thenReturn(1);
		TurnEvaluationRepository repository = repository(mapper);

		repository.upsert(evaluation());

		verify(mapper).update(
				any(TurnEvaluationEntity.class),
				any(Wrapper.class));
		verify(mapper, never()).updateById(any(TurnEvaluationEntity.class));
	}

	@Test
	void readsOrderedEvaluationsAndBeforeWindowWithFullPronunciationDetails() {
		TurnEvaluationMapper mapper = mock(TurnEvaluationMapper.class);
		EvaluationJsonbCodec codec = new EvaluationJsonbCodec(new ObjectMapper());
		TurnEvaluationEntity entity = entity(codec);
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity));
		TurnEvaluationRepository repository =
				new TurnEvaluationRepository(mapper, codec);

		CustomTurnEvaluation all = repository.findAll("custom_session_1")
				.getFirst();
		CustomTurnEvaluation before = repository.findBefore(
				"custom_session_1",
				3).getFirst();

		assertEquals(all, before);
		assertEquals("coffee", all.words().getFirst().text());
		assertEquals("k",
				all.words().getFirst().phonemes().getFirst().expectedPhoneme());
		verify(mapper, org.mockito.Mockito.times(2))
				.selectList(any(Wrapper.class));
	}

	@Test
	void deletesObsoleteSceneEvaluationsAndTranslatesFailures() {
		TurnEvaluationMapper mapper = mock(TurnEvaluationMapper.class);
		when(mapper.delete(any(Wrapper.class))).thenReturn(2);
		TurnEvaluationRepository repository = repository(mapper);

		assertEquals(2, repository.deleteObsoleteForScene(
				"custom_scene1",
				"custom_session_1"));

		when(mapper.delete(any(Wrapper.class)))
				.thenThrow(new IllegalStateException("database"));
		assertPersistenceFailure(() -> repository.deleteObsoleteForScene(
				"custom_scene1",
				"custom_session_1"));
	}

	@Test
	void rejectsUnexpectedUpsertCountsAndReadFailures() {
		TurnEvaluationMapper insertMapper = mock(TurnEvaluationMapper.class);
		when(insertMapper.selectOne(any(Wrapper.class))).thenReturn(null);
		when(insertMapper.insert(any(TurnEvaluationEntity.class))).thenReturn(0);
		assertPersistenceFailure(() -> repository(insertMapper).upsert(evaluation()));

		TurnEvaluationMapper updateMapper = mock(TurnEvaluationMapper.class);
		TurnEvaluationEntity existing = new TurnEvaluationEntity();
		existing.setCreatedAt(OffsetDateTime.now());
		when(updateMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
		when(updateMapper.update(any(TurnEvaluationEntity.class), any(Wrapper.class)))
				.thenReturn(0);
		assertPersistenceFailure(() -> repository(updateMapper).upsert(evaluation()));

		TurnEvaluationMapper readMapper = mock(TurnEvaluationMapper.class);
		when(readMapper.selectList(any(Wrapper.class)))
				.thenThrow(new IllegalStateException("database"));
		assertPersistenceFailure(() -> repository(readMapper)
				.findAll("custom_session_1"));
		assertPersistenceFailure(() -> repository(readMapper)
				.findBefore("custom_session_1", 2));
	}

	@Test
	void translatesUpsertAndJsonDecodeRuntimeFailures() {
		TurnEvaluationMapper upsertMapper = mock(TurnEvaluationMapper.class);
		when(upsertMapper.selectOne(any(Wrapper.class)))
				.thenThrow(new IllegalStateException("select"));
		assertPersistenceFailure(() -> repository(upsertMapper).upsert(evaluation()));

		TurnEvaluationMapper readMapper = mock(TurnEvaluationMapper.class);
		when(readMapper.selectList(any(Wrapper.class)))
				.thenReturn(List.of(new TurnEvaluationEntity()));
		EvaluationJsonbCodec codec = mock(EvaluationJsonbCodec.class);
		EvaluationException failure = new EvaluationException(
				EvaluationErrorCode.PERSISTENCE_FAILED);
		doThrow(failure).when(codec).decodePronunciationDetails(null);
		TurnEvaluationRepository repository =
				new TurnEvaluationRepository(readMapper, codec);

		assertPersistenceFailure(() -> repository.findAll("session_1"));
		assertPersistenceFailure(() -> repository.findBefore("session_1", 2));
	}

	@Test
	void findsBestScoreAcrossOwnedSessionsAndLegacyScenes() {
		TurnEvaluationMapper mapper = mock(TurnEvaluationMapper.class);
		TurnEvaluationEntity first = new TurnEvaluationEntity();
		first.setOverallScore(new BigDecimal("84.5"));
		TurnEvaluationEntity best = new TurnEvaluationEntity();
		best.setOverallScore(new BigDecimal("96.2"));
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, best));
		TurnEvaluationRepository repository = repository(mapper);

		assertEquals(
				new BigDecimal("96.2"),
				repository.findBestOverallScore(
						List.of("session-1", "session-1"),
						List.of("scene-1"))
						.orElseThrow());
		assertTrue(repository.findBestOverallScore(null, List.of()).isEmpty());
	}

	private TurnEvaluationRepository repository(
			TurnEvaluationMapper mapper) {
		return new TurnEvaluationRepository(
				mapper,
				new EvaluationJsonbCodec(new ObjectMapper()));
	}

	private CustomTurnEvaluation evaluation() {
		return new CustomTurnEvaluation(
				"custom_scene1",
				"custom_session_1",
				2,
				"I would like some coffee.",
				new BigDecimal("84"),
				new BigDecimal("82"),
				new BigDecimal("80"),
				new BigDecimal("100"),
				new BigDecimal("86"),
				new BigDecimal("83"),
				"表达清楚。",
				"I'd like some coffee, please.",
				List.of(new PronunciationWordDetail(
						0,
						"coffee",
						new BigDecimal("86"),
						List.of(new PronunciationWordDetail.Phoneme(
								0,
								"k",
								"k",
								new BigDecimal("90"),
								0,
								1)))));
	}

	private TurnEvaluationEntity entity(EvaluationJsonbCodec codec) {
		CustomTurnEvaluation evaluation = evaluation();
		TurnEvaluationEntity entity = new TurnEvaluationEntity();
		entity.setSceneId(evaluation.sceneId());
		entity.setSessionId(evaluation.sessionId());
		entity.setTurnNo(evaluation.turnNo());
		entity.setTranscript(evaluation.transcript());
		entity.setOverallScore(evaluation.overallScore());
		entity.setRhythmScore(evaluation.rhythmScore());
		entity.setToneScore(evaluation.toneScore());
		entity.setIntegrityScore(evaluation.integrityScore());
		entity.setPronunciationScore(evaluation.pronunciationScore());
		entity.setFluencyScore(evaluation.fluencyScore());
		entity.setFeedbackSummary(evaluation.feedbackSummary());
		entity.setSuggestedExpression(evaluation.suggestedExpression());
		PronunciationWordDetail word = evaluation.words().getFirst();
		PronunciationWordDetail.Phoneme phoneme = word.phonemes().getFirst();
		entity.setPronunciationDetails(codec.encodePronunciationDetails(
				new PronunciationDetailsJson(List.of(
						new PronunciationDetailsJson.Word(
								word.index(),
								word.text(),
								word.pronunciationScore(),
								List.of(new PronunciationDetailsJson.Phoneme(
										phoneme.index(),
										phoneme.expectedPhoneme(),
										phoneme.actualPhoneme(),
										phoneme.pronunciationScore(),
										phoneme.startPosition(),
										phoneme.endPosition())))))));
		return entity;
	}

	private void assertPersistenceFailure(
			org.junit.jupiter.api.function.Executable action) {
		EvaluationException exception = assertThrows(EvaluationException.class, action);
		assertEquals(EvaluationErrorCode.PERSISTENCE_FAILED, exception.errorCode());
	}
}
