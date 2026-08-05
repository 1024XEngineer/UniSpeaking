package com.unispeaking.infrastructure.persistence.repository.evaluation;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsEvaluationEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.IeltsEvaluationMapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class IeltsEvaluationRepository {

	private final IeltsEvaluationMapper mapper;
	private final ObjectMapper objectMapper;

	public IeltsEvaluationRepository(
			IeltsEvaluationMapper mapper,
			ObjectMapper objectMapper) {
		this.mapper = mapper;
		this.objectMapper = objectMapper;
	}

	public synchronized void save(
			String ieltsId,
			String sessionId,
			IeltsEvaluationResult result) {
		try {
			IeltsEvaluationEntity existing = mapper.selectById(sessionId);
			IeltsEvaluationEntity entity = toEntity(ieltsId, sessionId, result);
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
			if (mapper.updateById(entity) != 1) {
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

	public Optional<IeltsEvaluationEntity> find(String sessionId) {
		try {
			return Optional.ofNullable(mapper.selectById(sessionId));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private IeltsEvaluationEntity toEntity(
			String ieltsId,
			String sessionId,
			IeltsEvaluationResult result) {
		IeltsEvaluationEntity entity = new IeltsEvaluationEntity();
		entity.setSessionId(sessionId);
		entity.setIeltsId(ieltsId);
		entity.setPart(result.part() == null ? null : result.part().name());
		entity.setAssessmentType(result.assessmentType());
		entity.setOverallBandScore(result.overallBandScore());
		entity.setFluencyCoherenceScore(result.fluencyCoherenceScore());
		entity.setLexicalResourceScore(result.lexicalResourceScore());
		entity.setGrammaticalRangeAccuracyScore(
				result.grammaticalRangeAccuracyScore());
		entity.setPronunciationScore(result.pronunciationScore());
		entity.setSummary(result.summary());
		entity.setStrengths(result.strengths().toArray(String[]::new));
		entity.setImprovements(result.improvements().toArray(String[]::new));
		try {
			entity.setPartEvaluations(objectMapper.writeValueAsString(
					result.partEvaluations()));
		}
		catch (JacksonException exception) {
			throw persistenceFailure();
		}
		entity.setRecommendedExpressions(
				result.recommendedExpressions().toArray(String[]::new));
		return entity;
	}

	private EvaluationException persistenceFailure() {
		return new EvaluationException(EvaluationErrorCode.PERSISTENCE_FAILED);
	}
}
