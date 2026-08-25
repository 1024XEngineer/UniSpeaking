package com.unispeaking.infrastructure.persistence.repository.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsPartEvaluationEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.IeltsEvaluationMapper;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.IeltsPartEvaluationMapper;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class IeltsEvaluationRepository {
	private static final Duration LEASE_DURATION = Duration.ofMinutes(2);
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

	public synchronized IeltsPartEvaluationEntity ensurePartPending(
			String ieltsId,
			String sessionId,
			IeltsPart part) {
		try {
			String id = "ielts_part_" + sessionId;
			IeltsPartEvaluationEntity existing = partMapper.selectById(id);
			if (existing != null && "COMPLETED".equals(existing.getEvaluationStatus())) {
				return existing;
			}
			OffsetDateTime now = OffsetDateTime.now();
			if (existing == null) {
				IeltsPartEvaluationEntity entity = new IeltsPartEvaluationEntity();
				entity.setPartEvaluationId(id);
				entity.setIeltsId(ieltsId);
				entity.setSessionId(sessionId);
				entity.setPart(part.name());
				entity.setStrengths(new String[0]);
				entity.setImprovements(new String[0]);
				entity.setRecommendedExpressions(new String[0]);
				entity.setEvaluationStatus("PENDING");
				entity.setCreatedAt(now);
				entity.setUpdatedAt(now);
				if (partMapper.insert(entity) != 1) throw persistenceFailure();
				return entity;
			}
			if ("FAILED".equals(existing.getEvaluationStatus())) {
				partMapper.update(
						null,
						new LambdaUpdateWrapper<IeltsPartEvaluationEntity>()
								.eq(IeltsPartEvaluationEntity::getPartEvaluationId, id)
								.set(IeltsPartEvaluationEntity::getEvaluationStatus, "PENDING")
								.set(IeltsPartEvaluationEntity::getFailureReason, null)
								.set(IeltsPartEvaluationEntity::getLeaseToken, null)
								.set(IeltsPartEvaluationEntity::getLeaseExpiresAt, null)
								.set(IeltsPartEvaluationEntity::getCompletedAt, null)
								.set(IeltsPartEvaluationEntity::getUpdatedAt, now));
				existing.setEvaluationStatus("PENDING");
				existing.setFailureReason(null);
				existing.setCompletedAt(null);
				existing.setUpdatedAt(now);
			}
			return existing;
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

	public synchronized IeltsEvaluationEntity ensureFinalPending(String ieltsId) {
		try {
			String id = "ielts_mock_" + ieltsId;
			IeltsEvaluationEntity existing = finalMapper.selectById(id);
			if (existing != null && "COMPLETED".equals(existing.getEvaluationStatus())) {
				return existing;
			}
			OffsetDateTime now = OffsetDateTime.now();
			if (existing == null) {
				IeltsEvaluationEntity entity = new IeltsEvaluationEntity();
				entity.setEvaluationId(id);
				entity.setIeltsId(ieltsId);
				entity.setStrengths(new String[0]);
				entity.setImprovements(new String[0]);
				entity.setRecommendedExpressions(new String[0]);
				entity.setEvaluationStatus("PENDING");
				entity.setCreatedAt(now);
				entity.setUpdatedAt(now);
				if (finalMapper.insert(entity) != 1) throw persistenceFailure();
				return entity;
			}
			if ("FAILED".equals(existing.getEvaluationStatus())) {
				finalMapper.update(
						null,
						new LambdaUpdateWrapper<IeltsEvaluationEntity>()
								.eq(IeltsEvaluationEntity::getEvaluationId, id)
								.set(IeltsEvaluationEntity::getEvaluationStatus, "PENDING")
								.set(IeltsEvaluationEntity::getFailureReason, null)
								.set(IeltsEvaluationEntity::getLeaseToken, null)
								.set(IeltsEvaluationEntity::getLeaseExpiresAt, null)
								.set(IeltsEvaluationEntity::getCompletedAt, null)
								.set(IeltsEvaluationEntity::getUpdatedAt, now));
				existing.setEvaluationStatus("PENDING");
				existing.setFailureReason(null);
				existing.setCompletedAt(null);
				existing.setUpdatedAt(now);
			}
			return existing;
		}
		catch (EvaluationException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public void markPartFailed(String sessionId, String failureReason) {
		try {
			partMapper.update(
					null,
					new LambdaUpdateWrapper<IeltsPartEvaluationEntity>()
							.eq(IeltsPartEvaluationEntity::getSessionId, sessionId)
							.eq(IeltsPartEvaluationEntity::getEvaluationStatus, "PENDING")
							.set(IeltsPartEvaluationEntity::getEvaluationStatus, "FAILED")
							.set(IeltsPartEvaluationEntity::getFailureReason, failureReason)
							.set(IeltsPartEvaluationEntity::getUpdatedAt, OffsetDateTime.now()));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public void markFinalFailed(String ieltsId, String failureReason) {
		try {
			finalMapper.update(
					null,
					new LambdaUpdateWrapper<IeltsEvaluationEntity>()
							.eq(IeltsEvaluationEntity::getIeltsId, ieltsId)
							.eq(IeltsEvaluationEntity::getEvaluationStatus, "PENDING")
							.set(IeltsEvaluationEntity::getEvaluationStatus, "FAILED")
							.set(IeltsEvaluationEntity::getFailureReason, failureReason)
							.set(IeltsEvaluationEntity::getUpdatedAt, OffsetDateTime.now()));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<String> claimPart(String sessionId) {
		String token = UUID.randomUUID().toString();
		OffsetDateTime now = OffsetDateTime.now();
		int affected = partMapper.update(
				null,
				new LambdaUpdateWrapper<IeltsPartEvaluationEntity>()
						.eq(IeltsPartEvaluationEntity::getSessionId, sessionId)
						.eq(IeltsPartEvaluationEntity::getEvaluationStatus, "PENDING")
						.and(wrapper -> wrapper
								.isNull(IeltsPartEvaluationEntity::getLeaseExpiresAt)
								.or()
								.lt(IeltsPartEvaluationEntity::getLeaseExpiresAt, now))
						.set(IeltsPartEvaluationEntity::getLeaseToken, token)
						.set(IeltsPartEvaluationEntity::getLeaseExpiresAt, now.plus(LEASE_DURATION))
						.set(IeltsPartEvaluationEntity::getUpdatedAt, now));
		return affected == 1 ? Optional.of(token) : Optional.empty();
	}

	public Optional<String> claimFinal(String ieltsId) {
		String token = UUID.randomUUID().toString();
		OffsetDateTime now = OffsetDateTime.now();
		int affected = finalMapper.update(
				null,
				new LambdaUpdateWrapper<IeltsEvaluationEntity>()
						.eq(IeltsEvaluationEntity::getIeltsId, ieltsId)
						.eq(IeltsEvaluationEntity::getEvaluationStatus, "PENDING")
						.and(wrapper -> wrapper
								.isNull(IeltsEvaluationEntity::getLeaseExpiresAt)
								.or()
								.lt(IeltsEvaluationEntity::getLeaseExpiresAt, now))
						.set(IeltsEvaluationEntity::getLeaseToken, token)
						.set(IeltsEvaluationEntity::getLeaseExpiresAt, now.plus(LEASE_DURATION))
						.set(IeltsEvaluationEntity::getUpdatedAt, now));
		return affected == 1 ? Optional.of(token) : Optional.empty();
	}

	public boolean renewPartLease(String sessionId, String leaseToken) {
		OffsetDateTime now = OffsetDateTime.now();
		return partMapper.update(null, new LambdaUpdateWrapper<IeltsPartEvaluationEntity>()
				.eq(IeltsPartEvaluationEntity::getSessionId, sessionId)
				.eq(IeltsPartEvaluationEntity::getEvaluationStatus, "PENDING")
				.eq(IeltsPartEvaluationEntity::getLeaseToken, leaseToken)
				.set(IeltsPartEvaluationEntity::getLeaseExpiresAt, now.plus(LEASE_DURATION))
				.set(IeltsPartEvaluationEntity::getUpdatedAt, now)) == 1;
	}

	public boolean renewFinalLease(String ieltsId, String leaseToken) {
		OffsetDateTime now = OffsetDateTime.now();
		return finalMapper.update(null, new LambdaUpdateWrapper<IeltsEvaluationEntity>()
				.eq(IeltsEvaluationEntity::getIeltsId, ieltsId)
				.eq(IeltsEvaluationEntity::getEvaluationStatus, "PENDING")
				.eq(IeltsEvaluationEntity::getLeaseToken, leaseToken)
				.set(IeltsEvaluationEntity::getLeaseExpiresAt, now.plus(LEASE_DURATION))
				.set(IeltsEvaluationEntity::getUpdatedAt, now)) == 1;
	}

	public boolean completePartIfClaimed(
			String sessionId,
			String leaseToken,
			IeltsEvaluationResult result) {
		OffsetDateTime now = OffsetDateTime.now();
		return partMapper.update(
				null,
				new LambdaUpdateWrapper<IeltsPartEvaluationEntity>()
						.eq(IeltsPartEvaluationEntity::getSessionId, sessionId)
						.eq(IeltsPartEvaluationEntity::getEvaluationStatus, "PENDING")
						.eq(IeltsPartEvaluationEntity::getLeaseToken, leaseToken)
						.set(IeltsPartEvaluationEntity::getFluencyCoherenceScore, result.fluencyCoherenceScore())
						.set(IeltsPartEvaluationEntity::getLexicalResourceScore, result.lexicalResourceScore())
						.set(IeltsPartEvaluationEntity::getGrammaticalRangeAccuracyScore, result.grammaticalRangeAccuracyScore())
						.set(IeltsPartEvaluationEntity::getPronunciationScore, result.pronunciationScore())
						.set(IeltsPartEvaluationEntity::getFluencyCoherenceReason, result.fluencyCoherenceReason())
						.set(IeltsPartEvaluationEntity::getLexicalResourceReason, result.lexicalResourceReason())
						.set(IeltsPartEvaluationEntity::getGrammaticalRangeAccuracyReason, result.grammaticalRangeAccuracyReason())
						.set(IeltsPartEvaluationEntity::getPronunciationReason, result.pronunciationReason())
						.set(IeltsPartEvaluationEntity::getSummary, result.summary())
						.set(IeltsPartEvaluationEntity::getStrengths, result.strengths().toArray(String[]::new))
						.set(IeltsPartEvaluationEntity::getImprovements, result.improvements().toArray(String[]::new))
						.set(IeltsPartEvaluationEntity::getRecommendedExpressions, result.recommendedExpressions().toArray(String[]::new))
						.set(IeltsPartEvaluationEntity::getEvaluationStatus, "COMPLETED")
						.set(IeltsPartEvaluationEntity::getCompletedAt, now)
						.set(IeltsPartEvaluationEntity::getLeaseToken, null)
						.set(IeltsPartEvaluationEntity::getLeaseExpiresAt, null)
						.set(IeltsPartEvaluationEntity::getUpdatedAt, now)) == 1;
	}

	public boolean completeFinalIfClaimed(
			String ieltsId,
			String leaseToken,
			IeltsEvaluationResult result) {
		OffsetDateTime now = OffsetDateTime.now();
		return finalMapper.update(
				null,
				new LambdaUpdateWrapper<IeltsEvaluationEntity>()
						.eq(IeltsEvaluationEntity::getIeltsId, ieltsId)
						.eq(IeltsEvaluationEntity::getEvaluationStatus, "PENDING")
						.eq(IeltsEvaluationEntity::getLeaseToken, leaseToken)
						.set(IeltsEvaluationEntity::getOverallBandScore, result.overallBandScore())
						.set(IeltsEvaluationEntity::getFluencyCoherenceScore, result.fluencyCoherenceScore())
						.set(IeltsEvaluationEntity::getLexicalResourceScore, result.lexicalResourceScore())
						.set(IeltsEvaluationEntity::getGrammaticalRangeAccuracyScore, result.grammaticalRangeAccuracyScore())
						.set(IeltsEvaluationEntity::getPronunciationScore, result.pronunciationScore())
						.set(IeltsEvaluationEntity::getFluencyCoherenceReason, result.fluencyCoherenceReason())
						.set(IeltsEvaluationEntity::getLexicalResourceReason, result.lexicalResourceReason())
						.set(IeltsEvaluationEntity::getGrammaticalRangeAccuracyReason, result.grammaticalRangeAccuracyReason())
						.set(IeltsEvaluationEntity::getPronunciationReason, result.pronunciationReason())
						.set(IeltsEvaluationEntity::getSummary, result.summary())
						.set(IeltsEvaluationEntity::getStrengths, result.strengths().toArray(String[]::new))
						.set(IeltsEvaluationEntity::getImprovements, result.improvements().toArray(String[]::new))
						.set(IeltsEvaluationEntity::getRecommendedExpressions, result.recommendedExpressions().toArray(String[]::new))
						.set(IeltsEvaluationEntity::getEvaluationStatus, "COMPLETED")
						.set(IeltsEvaluationEntity::getCompletedAt, now)
						.set(IeltsEvaluationEntity::getLeaseToken, null)
						.set(IeltsEvaluationEntity::getLeaseExpiresAt, null)
						.set(IeltsEvaluationEntity::getUpdatedAt, now)) == 1;
	}

	public void markPartFailedIfClaimed(String sessionId, String leaseToken, String reason) {
		partMapper.update(null, new LambdaUpdateWrapper<IeltsPartEvaluationEntity>()
				.eq(IeltsPartEvaluationEntity::getSessionId, sessionId)
				.eq(IeltsPartEvaluationEntity::getEvaluationStatus, "PENDING")
				.eq(IeltsPartEvaluationEntity::getLeaseToken, leaseToken)
				.set(IeltsPartEvaluationEntity::getEvaluationStatus, "FAILED")
				.set(IeltsPartEvaluationEntity::getFailureReason, reason)
				.set(IeltsPartEvaluationEntity::getLeaseToken, null)
				.set(IeltsPartEvaluationEntity::getLeaseExpiresAt, null)
				.set(IeltsPartEvaluationEntity::getUpdatedAt, OffsetDateTime.now()));
	}

	public void markFinalFailedIfClaimed(String ieltsId, String leaseToken, String reason) {
		finalMapper.update(null, new LambdaUpdateWrapper<IeltsEvaluationEntity>()
				.eq(IeltsEvaluationEntity::getIeltsId, ieltsId)
				.eq(IeltsEvaluationEntity::getEvaluationStatus, "PENDING")
				.eq(IeltsEvaluationEntity::getLeaseToken, leaseToken)
				.set(IeltsEvaluationEntity::getEvaluationStatus, "FAILED")
				.set(IeltsEvaluationEntity::getFailureReason, reason)
				.set(IeltsEvaluationEntity::getLeaseToken, null)
				.set(IeltsEvaluationEntity::getLeaseExpiresAt, null)
				.set(IeltsEvaluationEntity::getUpdatedAt, OffsetDateTime.now()));
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
