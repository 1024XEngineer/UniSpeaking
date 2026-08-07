package com.unispeaking.infrastructure.persistence.repository.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsPartEvaluationEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.IeltsEvaluationMapper;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.IeltsPartEvaluationMapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class IeltsEvaluationRepository {
	private final IeltsEvaluationMapper finalMapper;
	private final IeltsPartEvaluationMapper partMapper;

	public IeltsEvaluationRepository(
			IeltsEvaluationMapper finalMapper,
			IeltsPartEvaluationMapper partMapper) {
		this.finalMapper = finalMapper;
		this.partMapper = partMapper;
	}

	public synchronized void save(
			String ieltsId,
			String sessionId,
			IeltsEvaluationResult result) {
		if ("FINAL".equals(result.assessmentType())) {
			saveFinal(ieltsId, result);
		}
		else {
			savePart(ieltsId, sessionId, result);
		}
	}

	public synchronized void savePart(
			String ieltsId,
			String sessionId,
			IeltsEvaluationResult result) {
		try {
			String id = "ielts_part_" + sessionId;
			IeltsPartEvaluationEntity existing = partMapper.selectById(id);
			IeltsPartEvaluationEntity entity = new IeltsPartEvaluationEntity();
			entity.setPartEvaluationId(id);
			entity.setIeltsId(ieltsId);
			entity.setSessionId(sessionId);
			entity.setPart(result.part().name());
			entity.setFluencyCoherenceScore(result.fluencyCoherenceScore());
			entity.setLexicalResourceScore(result.lexicalResourceScore());
			entity.setGrammaticalRangeAccuracyScore(
					result.grammaticalRangeAccuracyScore());
			entity.setPronunciationScore(result.pronunciationScore());
			entity.setFluencyCoherenceReason(result.fluencyCoherenceReason());
			entity.setLexicalResourceReason(result.lexicalResourceReason());
			entity.setGrammaticalRangeAccuracyReason(
					result.grammaticalRangeAccuracyReason());
			entity.setPronunciationReason(result.pronunciationReason());
			entity.setSummary(result.summary());
			entity.setStrengths(result.strengths().toArray(String[]::new));
			entity.setImprovements(result.improvements().toArray(String[]::new));
			entity.setRecommendedExpressions(
					result.recommendedExpressions().toArray(String[]::new));
			entity.setEvaluationStatus("COMPLETED");
			OffsetDateTime now = OffsetDateTime.now();
			entity.setCompletedAt(now);
			entity.setCreatedAt(existing == null ? now : existing.getCreatedAt());
			entity.setUpdatedAt(now);
			int affected = existing == null
					? partMapper.insert(entity)
					: partMapper.updateById(entity);
			if (affected != 1) throw persistenceFailure();
		}
		catch (EvaluationException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public synchronized void saveFinal(
			String ieltsId,
			IeltsEvaluationResult result) {
		try {
			String id = "ielts_mock_" + ieltsId;
			IeltsEvaluationEntity existing = finalMapper.selectById(id);
			IeltsEvaluationEntity entity = new IeltsEvaluationEntity();
			entity.setEvaluationId(id);
			entity.setIeltsId(ieltsId);
			entity.setOverallBandScore(result.overallBandScore());
			entity.setFluencyCoherenceScore(result.fluencyCoherenceScore());
			entity.setLexicalResourceScore(result.lexicalResourceScore());
			entity.setGrammaticalRangeAccuracyScore(
					result.grammaticalRangeAccuracyScore());
			entity.setPronunciationScore(result.pronunciationScore());
			entity.setFluencyCoherenceReason(result.fluencyCoherenceReason());
			entity.setLexicalResourceReason(result.lexicalResourceReason());
			entity.setGrammaticalRangeAccuracyReason(
					result.grammaticalRangeAccuracyReason());
			entity.setPronunciationReason(result.pronunciationReason());
			entity.setSummary(result.summary());
			entity.setStrengths(result.strengths().toArray(String[]::new));
			entity.setImprovements(result.improvements().toArray(String[]::new));
			entity.setRecommendedExpressions(
					result.recommendedExpressions().toArray(String[]::new));
			entity.setEvaluationStatus("COMPLETED");
			OffsetDateTime now = OffsetDateTime.now();
			entity.setCompletedAt(now);
			entity.setCreatedAt(existing == null ? now : existing.getCreatedAt());
			entity.setUpdatedAt(now);
			int affected = existing == null
					? finalMapper.insert(entity)
					: finalMapper.updateById(entity);
			if (affected != 1) throw persistenceFailure();
		}
		catch (EvaluationException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<IeltsPartEvaluationEntity> findPart(String sessionId) {
		try {
			return Optional.ofNullable(partMapper.selectOne(
					new LambdaQueryWrapper<IeltsPartEvaluationEntity>()
							.eq(
									IeltsPartEvaluationEntity::getSessionId,
									sessionId)));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<IeltsEvaluationEntity> findFinal(String ieltsId) {
		try {
			return Optional.ofNullable(finalMapper.selectOne(
					new LambdaQueryWrapper<IeltsEvaluationEntity>()
							.eq(IeltsEvaluationEntity::getIeltsId, ieltsId)));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<IeltsPartEvaluationEntity> findParts(String ieltsId) {
		try {
			return partMapper.selectList(
					new LambdaQueryWrapper<IeltsPartEvaluationEntity>()
							.eq(IeltsPartEvaluationEntity::getIeltsId, ieltsId)
							.eq(
									IeltsPartEvaluationEntity::getEvaluationStatus,
									"COMPLETED")
							.orderByAsc(IeltsPartEvaluationEntity::getPart));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private EvaluationException persistenceFailure() {
		return new EvaluationException(EvaluationErrorCode.PERSISTENCE_FAILED);
	}
}
