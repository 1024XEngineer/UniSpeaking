package com.unispeaking.infrastructure.persistence.repository.evaluation;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.common.persistence.codec.evaluation.EvaluationJsonbCodec;
import com.unispeaking.infrastructure.persistence.entity.evaluation.ReadingDetailsJson;
import com.unispeaking.infrastructure.persistence.entity.evaluation.SentenceEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneSentenceEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.SentenceEvaluationMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneSentenceMapper;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Persists every custom-scene sentence reading as a separate evaluation row.
 */
@Repository
public class SceneSentenceReadingRepository {

	private final SceneSentenceMapper sentenceMapper;
	private final SentenceEvaluationMapper evaluationMapper;
	private final EvaluationJsonbCodec jsonbCodec;

	public SceneSentenceReadingRepository(
			SceneSentenceMapper sentenceMapper,
			SentenceEvaluationMapper evaluationMapper,
			EvaluationJsonbCodec jsonbCodec) {
		this.sentenceMapper = Objects.requireNonNull(
				sentenceMapper,
				"sentenceMapper must not be null");
		this.evaluationMapper = Objects.requireNonNull(
				evaluationMapper,
				"evaluationMapper must not be null");
		this.jsonbCodec = Objects.requireNonNull(
				jsonbCodec,
				"jsonbCodec must not be null");
	}

	public String saveAttempt(
			String sceneId,
			LearningContentItem sentence,
			PronunciationAssessmentResult assessment) {
		Objects.requireNonNull(sentence, "sentence must not be null");
		Objects.requireNonNull(assessment, "assessment must not be null");

		String readingId = "sentence_reading_" + compactId();
		SentenceEvaluationEntity entity = new SentenceEvaluationEntity();
		entity.setId(readingId);
		entity.setSentenceId(sentence.contentId());
		entity.setSceneId(sceneId);
		entity.setOverallScore(assessment.overallScore());
		entity.setScoreDetail(jsonbCodec.encodeReadingDetails(
				toReadingDetails(assessment)));

		try {
			if (evaluationMapper.insert(entity) != 1) {
				throw persistenceFailure();
			}
			return readingId;
		}
		catch (EvaluationException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<String> findSceneIdBySentenceId(String sentenceId) {
		if (sentenceId == null || sentenceId.isBlank()) {
			return Optional.empty();
		}
		try {
			List<SceneSentenceEntity> rows = sentenceMapper.selectList(
					new LambdaQueryWrapper<SceneSentenceEntity>()
							.eq(SceneSentenceEntity::getSentenceId, sentenceId)
							.orderByAsc(SceneSentenceEntity::getCreatedAt));
			return rows.isEmpty()
					? Optional.empty()
					: Optional.ofNullable(rows.getFirst().getSceneId());
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public long countAttemptsBySceneIds(List<String> sceneIds) {
		if (sceneIds == null || sceneIds.isEmpty()) {
			return 0;
		}
		List<String> ownedSceneIds = sceneIds.stream()
				.filter(sceneId -> sceneId != null && !sceneId.isBlank())
				.distinct()
				.toList();
		if (ownedSceneIds.isEmpty()) {
			return 0;
		}
		try {
			return evaluationMapper.selectCount(
					new LambdaQueryWrapper<SentenceEvaluationEntity>()
							.in(
									SentenceEvaluationEntity::getSceneId,
									ownedSceneIds));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private ReadingDetailsJson toReadingDetails(
			PronunciationAssessmentResult assessment) {
		List<ReadingDetailsJson.Word> words = assessment.words().stream()
				.map(this::toReadingWord)
				.toList();
		return new ReadingDetailsJson(
				assessment.overallScore(),
				assessment.pronunciationScore(),
				assessment.fluencyScore(),
				assessment.integrityScore(),
				assessment.rhythmScore(),
				assessment.endingTone(),
				words);
	}

	private ReadingDetailsJson.Word toReadingWord(
			PronunciationWordResult word) {
		return new ReadingDetailsJson.Word(
				word.index(),
				word.word(),
				word.readStatus(),
				word.overallScore(),
				word.pronunciationScore(),
				word.isProminent(),
				word.phonemes().stream()
						.map(this::toReadingPhoneme)
						.toList());
	}

	private ReadingDetailsJson.Phoneme toReadingPhoneme(
			PronunciationPhonemeResult phoneme) {
		return new ReadingDetailsJson.Phoneme(
				phoneme.index(),
				phoneme.expectedPhoneme(),
				phoneme.actualPhoneme(),
				phoneme.pronunciationScore(),
				phoneme.startPosition(),
				phoneme.endPosition());
	}

	private String compactId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private EvaluationException persistenceFailure() {
		return new EvaluationException(EvaluationErrorCode.PERSISTENCE_FAILED);
	}
}
