package com.unispeaking.infrastructure.persistence.repository.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unispeaking.common.persistence.codec.evaluation.EvaluationJsonbCodec;
import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationDetailsJson;
import com.unispeaking.infrastructure.persistence.entity.evaluation.CustomTurnEvaluation;
import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationWordDetail;
import com.unispeaking.infrastructure.persistence.entity.evaluation.TurnEvaluationEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.TurnEvaluationMapper;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class TurnEvaluationRepository {

	private final TurnEvaluationMapper mapper;
	private final EvaluationJsonbCodec jsonbCodec;

	public TurnEvaluationRepository(
			TurnEvaluationMapper mapper,
			EvaluationJsonbCodec jsonbCodec) {
		this.mapper = mapper;
		this.jsonbCodec = jsonbCodec;
	}

	public synchronized void upsert(CustomTurnEvaluation evaluation) {
		try {
			TurnEvaluationEntity existing = mapper.selectOne(query(
					evaluation.sessionId(),
					evaluation.turnNo()));
			TurnEvaluationEntity entity = toEntity(evaluation);
			if (existing == null) {
				entity.setCreatedAt(OffsetDateTime.now());
				entity.setUpdatedAt(entity.getCreatedAt());
				if (mapper.insert(entity) != 1) {
					throw persistenceFailure();
				}
				return;
			}
			entity.setCreatedAt(existing.getCreatedAt());
			entity.setUpdatedAt(OffsetDateTime.now());
			if (mapper.update(entity, query(
					evaluation.sessionId(),
					evaluation.turnNo())) != 1) {
				throw persistenceFailure();
			}
		}
		catch (EvaluationException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<CustomTurnEvaluation> findAll(String sessionId) {
		try {
			return mapper.selectList(new LambdaQueryWrapper<TurnEvaluationEntity>()
							.eq(TurnEvaluationEntity::getSessionId, sessionId)
							.orderByAsc(TurnEvaluationEntity::getTurnNo))
					.stream()
					.map(this::toDomain)
					.toList();
		}
		catch (EvaluationException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<CustomTurnEvaluation> findBefore(String sessionId, int turnNo) {
		try {
			return mapper.selectList(new LambdaQueryWrapper<TurnEvaluationEntity>()
							.eq(TurnEvaluationEntity::getSessionId, sessionId)
							.lt(TurnEvaluationEntity::getTurnNo, turnNo)
							.orderByAsc(TurnEvaluationEntity::getTurnNo))
					.stream()
					.map(this::toDomain)
					.toList();
		}
		catch (EvaluationException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public int deleteObsoleteForScene(
			String sceneId,
			String retainedSessionId) {
		try {
			return mapper.delete(
					new LambdaQueryWrapper<TurnEvaluationEntity>()
							.eq(TurnEvaluationEntity::getSceneId, sceneId)
							.ne(
									TurnEvaluationEntity::getSessionId,
									retainedSessionId));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<BigDecimal> findBestOverallScore(
			List<String> sessionIds,
			List<String> sceneIds) {
		List<String> ownedSessionIds = normalizedIds(sessionIds);
		List<String> ownedSceneIds = normalizedIds(sceneIds);
		if (ownedSessionIds.isEmpty() && ownedSceneIds.isEmpty()) {
			return Optional.empty();
		}
		try {
			LambdaQueryWrapper<TurnEvaluationEntity> query =
					new LambdaQueryWrapper<TurnEvaluationEntity>()
							.select(TurnEvaluationEntity::getOverallScore)
							.isNotNull(TurnEvaluationEntity::getOverallScore)
							.and(scope -> appendOwnershipScope(
									scope,
									ownedSessionIds,
									ownedSceneIds));
			return mapper.selectList(query).stream()
					.map(TurnEvaluationEntity::getOverallScore)
					.max(BigDecimal::compareTo);
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private void appendOwnershipScope(
			LambdaQueryWrapper<TurnEvaluationEntity> scope,
			List<String> sessionIds,
			List<String> sceneIds) {
		if (!sessionIds.isEmpty()) {
			scope.in(TurnEvaluationEntity::getSessionId, sessionIds);
		}
		if (!sceneIds.isEmpty()) {
			if (!sessionIds.isEmpty()) {
				scope.or();
			}
			scope.in(TurnEvaluationEntity::getSceneId, sceneIds);
		}
	}

	private List<String> normalizedIds(List<String> ids) {
		if (ids == null) {
			return List.of();
		}
		return ids.stream()
				.filter(id -> id != null && !id.isBlank())
				.distinct()
				.toList();
	}

	private LambdaQueryWrapper<TurnEvaluationEntity> query(
			String sessionId,
			int turnNo) {
		return new LambdaQueryWrapper<TurnEvaluationEntity>()
				.eq(TurnEvaluationEntity::getSessionId, sessionId)
				.eq(TurnEvaluationEntity::getTurnNo, turnNo);
	}

	private TurnEvaluationEntity toEntity(CustomTurnEvaluation evaluation) {
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
		entity.setPronunciationDetails(jsonbCodec.encodePronunciationDetails(
				new PronunciationDetailsJson(evaluation.words().stream()
						.map(this::toJsonWord)
						.toList())));
		return entity;
	}

	private CustomTurnEvaluation toDomain(TurnEvaluationEntity entity) {
		PronunciationDetailsJson details = jsonbCodec.decodePronunciationDetails(
				entity.getPronunciationDetails());
		return new CustomTurnEvaluation(
				entity.getSceneId(),
				entity.getSessionId(),
				entity.getTurnNo(),
				entity.getTranscript(),
				entity.getOverallScore(),
				entity.getRhythmScore(),
				entity.getToneScore(),
				entity.getIntegrityScore(),
				entity.getPronunciationScore(),
				entity.getFluencyScore(),
				entity.getFeedbackSummary(),
				entity.getSuggestedExpression(),
				details.words().stream()
						.map(this::toWord)
						.toList());
	}

	private PronunciationDetailsJson.Word toJsonWord(
			PronunciationWordDetail word) {
		return new PronunciationDetailsJson.Word(
				word.index(),
				word.text(),
				word.pronunciationScore(),
				word.phonemes().stream()
						.map(phoneme -> new PronunciationDetailsJson.Phoneme(
								phoneme.index(),
								phoneme.expectedPhoneme(),
								phoneme.actualPhoneme(),
								phoneme.pronunciationScore(),
								phoneme.startPosition(),
								phoneme.endPosition()))
						.toList());
	}

	private PronunciationWordDetail toWord(
			PronunciationDetailsJson.Word word) {
		return new PronunciationWordDetail(
				word.index(),
				word.text(),
				word.pronunciationScore(),
				word.phonemes().stream()
						.map(phoneme -> new PronunciationWordDetail.Phoneme(
								phoneme.index(),
								phoneme.expectedPhoneme(),
								phoneme.actualPhoneme(),
								phoneme.pronunciationScore(),
								phoneme.startPosition(),
								phoneme.endPosition()))
						.toList());
	}

	private EvaluationException persistenceFailure() {
		return new EvaluationException(EvaluationErrorCode.PERSISTENCE_FAILED);
	}
}
