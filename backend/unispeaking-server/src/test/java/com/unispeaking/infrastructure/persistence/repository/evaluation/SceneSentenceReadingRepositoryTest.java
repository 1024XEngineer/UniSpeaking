package com.unispeaking.infrastructure.persistence.repository.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.common.persistence.codec.evaluation.EvaluationJsonbCodec;
import com.unispeaking.infrastructure.persistence.entity.evaluation.SentenceEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.ReadingDetailsJson;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.SentenceEvaluationMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneSentenceMapper;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneSentenceEntity;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class SceneSentenceReadingRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
				SentenceEvaluationEntity.class);
	}

	@Test
	void insertsANewSentenceEvaluationRowForEveryReading() {
		SceneSentenceMapper sentenceMapper = mock(SceneSentenceMapper.class);
		SentenceEvaluationMapper evaluationMapper =
				mock(SentenceEvaluationMapper.class);
		when(evaluationMapper.insert(any(SentenceEvaluationEntity.class)))
				.thenReturn(1);
		EvaluationJsonbCodec codec =
				new EvaluationJsonbCodec(new ObjectMapper());
		SceneSentenceReadingRepository repository =
				new SceneSentenceReadingRepository(
						sentenceMapper,
						evaluationMapper,
						codec);
		LearningContentItem sentence = new LearningContentItem(
				"sentence_abc",
				"I need something for a headache.",
				"我需要一些治头痛的药。",
				"");
		PronunciationAssessmentResult assessment = assessment();

		String firstId = repository.saveAttempt(
				"custom_scene1",
				sentence,
				assessment);
		String secondId = repository.saveAttempt(
				"custom_scene1",
				sentence,
				assessment);

		assertTrue(firstId.startsWith("sentence_reading_"));
		assertTrue(secondId.startsWith("sentence_reading_"));
		assertNotEquals(firstId, secondId);
		ArgumentCaptor<SentenceEvaluationEntity> rows =
				ArgumentCaptor.forClass(SentenceEvaluationEntity.class);
		verify(evaluationMapper, times(2)).insert(rows.capture());
		SentenceEvaluationEntity first = rows.getAllValues().getFirst();
		assertEquals("sentence_abc", first.getSentenceId());
		assertEquals("custom_scene1", first.getSceneId());
		assertEquals(new BigDecimal("82"), first.getOverallScore());
		ReadingDetailsJson decoded =
				codec.decodeReadingDetails(first.getScoreDetail());
		assertEquals(
				assessment.overallScore(),
				decoded.overallScore());
		assertEquals(
				-1,
				decoded.words().getFirst().phonemes().get(1).startPosition());
		assertEquals(
				-1,
				decoded.words().getFirst().phonemes().get(1).endPosition());
	}

	@Test
	void findsFirstSceneForSentenceAndHandlesMissingIdentifiers() {
		SceneSentenceMapper sentenceMapper = mock(SceneSentenceMapper.class);
		SceneSentenceEntity row = new SceneSentenceEntity();
		row.setSceneId("custom_scene1");
		when(sentenceMapper.selectList(any())).thenReturn(List.of(row), List.of());
		SceneSentenceReadingRepository repository = repository(
				sentenceMapper,
				mock(SentenceEvaluationMapper.class));

		assertTrue(repository.findSceneIdBySentenceId(null).isEmpty());
		assertTrue(repository.findSceneIdBySentenceId(" ").isEmpty());
		assertEquals("custom_scene1",
				repository.findSceneIdBySentenceId("sentence_1").orElseThrow());
		assertTrue(repository.findSceneIdBySentenceId("missing").isEmpty());
	}

	@Test
	void translatesInsertAndLookupFailures() {
		SceneSentenceMapper sentenceMapper = mock(SceneSentenceMapper.class);
		SentenceEvaluationMapper evaluationMapper =
				mock(SentenceEvaluationMapper.class);
		when(evaluationMapper.insert(any(SentenceEvaluationEntity.class)))
				.thenReturn(0);
		SceneSentenceReadingRepository repository = repository(
				sentenceMapper,
				evaluationMapper);

		assertPersistenceFailure(() -> repository.saveAttempt(
				"custom_scene1",
				new LearningContentItem("sentence_1", "Text", "文本", ""),
				assessment()));

		when(sentenceMapper.selectList(any()))
				.thenThrow(new IllegalStateException("database"));
		assertPersistenceFailure(() -> repository.findSceneIdBySentenceId(
				"sentence_1"));
	}

	private SceneSentenceReadingRepository repository(
			SceneSentenceMapper sentenceMapper,
			SentenceEvaluationMapper evaluationMapper) {
		return new SceneSentenceReadingRepository(
				sentenceMapper,
				evaluationMapper,
				new EvaluationJsonbCodec(new ObjectMapper()));
	}

	private void assertPersistenceFailure(
			org.junit.jupiter.api.function.Executable action) {
		EvaluationException exception = assertThrows(EvaluationException.class, action);
		assertEquals(EvaluationErrorCode.PERSISTENCE_FAILED, exception.errorCode());
	}

	@Test
	void countsAttemptsAcrossOwnedScenes() {
		SentenceEvaluationMapper evaluationMapper =
				mock(SentenceEvaluationMapper.class);
		when(evaluationMapper.selectCount(any())).thenReturn(12L);
		SceneSentenceReadingRepository repository =
				new SceneSentenceReadingRepository(
						mock(SceneSentenceMapper.class),
						evaluationMapper,
						new EvaluationJsonbCodec(new ObjectMapper()));

		assertEquals(
				12,
				repository.countAttemptsBySceneIds(
						List.of("scene-1", "scene-1", "scene-2")));
		assertEquals(0, repository.countAttemptsBySceneIds(List.of()));
		assertEquals(0, repository.countAttemptsBySceneIds(null));
	}

	private PronunciationAssessmentResult assessment() {
		PronunciationPhonemeResult phoneme =
				new PronunciationPhonemeResult(
						0,
						"h",
						"h",
						new BigDecimal("83"),
						0,
						18);
		PronunciationPhonemeResult unmatchedPhoneme =
				new PronunciationPhonemeResult(
						1,
						"eɪ",
						"eɪ",
						BigDecimal.ZERO,
						-1,
						-1);
		PronunciationWordResult word = new PronunciationWordResult(
				0,
				"headache",
				WordReadStatus.NORMAL,
				new BigDecimal("82"),
				new BigDecimal("83"),
				false,
				List.of(phoneme, unmatchedPhoneme));
		return new PronunciationAssessmentResult(
				new BigDecimal("82"),
				new BigDecimal("80"),
				new BigDecimal("78"),
				new BigDecimal("85"),
				new BigDecimal("83"),
				new BigDecimal("81"),
				EndingTone.FALL,
				List.of(word));
	}
}
