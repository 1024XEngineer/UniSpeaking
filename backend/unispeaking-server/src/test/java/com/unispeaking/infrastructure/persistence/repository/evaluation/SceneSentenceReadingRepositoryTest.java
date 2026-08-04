package com.unispeaking.infrastructure.persistence.repository.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.codec.evaluation.EvaluationJsonbCodec;
import com.unispeaking.infrastructure.persistence.entity.evaluation.SentenceEvaluationEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.SentenceEvaluationMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneSentenceMapper;
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
		assertEquals(
				assessment.overallScore(),
				codec.decodeReadingDetails(first.getScoreDetail()).overallScore());
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
		PronunciationWordResult word = new PronunciationWordResult(
				0,
				"headache",
				WordReadStatus.NORMAL,
				new BigDecimal("82"),
				new BigDecimal("83"),
				false,
				List.of(phoneme));
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
