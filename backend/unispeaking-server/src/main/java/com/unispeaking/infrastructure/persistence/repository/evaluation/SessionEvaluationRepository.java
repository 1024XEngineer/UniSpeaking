package com.unispeaking.infrastructure.persistence.repository.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unispeaking.domain.dto.asset.SessionEvaluationRecord;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.infrastructure.persistence.entity.evaluation.SessionEvaluationEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.SessionEvaluationMapper;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SessionEvaluationRepository {

	private final SessionEvaluationMapper mapper;

	public SessionEvaluationRepository(SessionEvaluationMapper mapper) {
		this.mapper = mapper;
	}

	public synchronized void save(
			String sceneId,
			String sessionId,
			DialogueReportResult report) {
		try {
			SessionEvaluationEntity existing = mapper.selectById(sessionId);
			SessionEvaluationEntity entity = toEntity(
					sceneId,
					sessionId,
					report);
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

	public Optional<DialogueReportResult> find(String sessionId) {
		try {
			SessionEvaluationEntity entity = mapper.selectById(sessionId);
			return entity == null
					? Optional.empty()
					: Optional.of(toDomain(entity));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<SessionEvaluationRecord> findRecord(String sessionId) {
		try {
			SessionEvaluationEntity entity = mapper.selectById(sessionId);
			return entity == null
					|| entity.getSceneId() == null
					|| entity.getSceneId().isBlank()
					? Optional.empty()
					: Optional.of(toRecord(entity));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<SessionEvaluationRecord> findBySceneId(String sceneId) {
		try {
			return mapper.selectList(
							new LambdaQueryWrapper<SessionEvaluationEntity>()
									.eq(SessionEvaluationEntity::getSceneId, sceneId)
									.orderByDesc(
											SessionEvaluationEntity::getCreatedAt))
					.stream()
					.map(this::toRecord)
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private SessionEvaluationEntity toEntity(
			String sceneId,
			String sessionId,
			DialogueReportResult report) {
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
		return entity;
	}

	private SessionEvaluationRecord toRecord(
			SessionEvaluationEntity entity) {
		return new SessionEvaluationRecord(
				entity.getSceneId(),
				entity.getSessionId(),
				toDomain(entity),
				entity.getCreatedAt());
	}

	private DialogueReportResult toDomain(SessionEvaluationEntity entity) {
		return new DialogueReportResult(
				entity.getAccuracyScore(),
				entity.getFluencyScore(),
				entity.getGrammarScore(),
				entity.getVocabularyScore(),
				entity.getNaturalnessScore(),
				entity.getFinalScore(),
				entity.getSummary(),
				entity.getStrengths() == null
						? java.util.List.of()
						: Arrays.asList(entity.getStrengths()),
				entity.getImprovements() == null
						? java.util.List.of()
						: Arrays.asList(entity.getImprovements()));
	}

	private EvaluationException persistenceFailure() {
		return new EvaluationException(EvaluationErrorCode.PERSISTENCE_FAILED);
	}
}
