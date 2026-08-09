package com.unispeaking.infrastructure.persistence.repository.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.domain.po.evaluation.InterviewReportRecord;
import com.unispeaking.domain.vo.evaluation.ReportStatus;
import com.unispeaking.infrastructure.persistence.entity.evaluation.InterviewReportEntity;
import com.unispeaking.infrastructure.persistence.mapper.evaluation.InterviewReportMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class MybatisInterviewReportRepository implements InterviewReportRepository {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			MybatisInterviewReportRepository.class);

	private final InterviewReportMapper mapper;

	public MybatisInterviewReportRepository(InterviewReportMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public boolean createIfAbsent(String sessionId, String sceneId, String userId) {
		if (sessionId == null || sessionId.isBlank()
				|| sceneId == null || sceneId.isBlank()
				|| userId == null || userId.isBlank()) {
			throw new BusinessException(
					InterviewErrorCode.INTERVIEW_REQUEST_INVALID,
					"报告行标识不完整");
		}
		InterviewReportEntity entity = new InterviewReportEntity();
		entity.setSessionId(sessionId);
		entity.setSceneId(sceneId);
		entity.setUserId(UUID.fromString(userId));
		entity.setStatus(ReportStatus.PROCESSING.name());
		entity.setRetryCount(0);
		OffsetDateTime now = OffsetDateTime.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		try {
			mapper.insert(entity);
			return true;
		}
		catch (DuplicateKeyException exception) {
			// PK 冲突 → 该会话已有报告行（并发 end/重试），视为未创建。
			return false;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	@Override
	public Optional<InterviewReportRecord> findById(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			return Optional.empty();
		}
		try {
			InterviewReportEntity entity = mapper.selectById(sessionId);
			return entity == null ? Optional.empty() : Optional.of(toRecord(entity));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	@Override
	public void markCompleted(InterviewReportRecord completed) {
		try {
			int updated = mapper.update(
					null,
					new LambdaUpdateWrapper<InterviewReportEntity>()
							.eq(InterviewReportEntity::getSessionId, completed.sessionId())
							.eq(InterviewReportEntity::getStatus, ReportStatus.PROCESSING.name())
							.set(InterviewReportEntity::getStatus, ReportStatus.COMPLETED.name())
							.set(InterviewReportEntity::getOverallScore, completed.overallScore())
							.set(InterviewReportEntity::getSummary, completed.summary())
							.set(InterviewReportEntity::getFluencyScore, completed.fluencyScore())
							.set(InterviewReportEntity::getFluencyEvaluation, completed.fluencyEvaluation())
							.set(InterviewReportEntity::getFluencyAdvice, completed.fluencyAdvice())
							.set(InterviewReportEntity::getPronunciationIntelligibilityScore,
									completed.pronunciationIntelligibilityScore())
							.set(InterviewReportEntity::getPronunciationIntelligibilityEvaluation,
									completed.pronunciationIntelligibilityEvaluation())
							.set(InterviewReportEntity::getPronunciationIntelligibilityAdvice,
									completed.pronunciationIntelligibilityAdvice())
							.set(InterviewReportEntity::getLogicCoherenceScore, completed.logicCoherenceScore())
							.set(InterviewReportEntity::getLogicCoherenceEvaluation, completed.logicCoherenceEvaluation())
							.set(InterviewReportEntity::getLogicCoherenceAdvice, completed.logicCoherenceAdvice())
							.set(InterviewReportEntity::getGrammarControlScore, completed.grammarControlScore())
							.set(InterviewReportEntity::getGrammarControlEvaluation, completed.grammarControlEvaluation())
							.set(InterviewReportEntity::getGrammarControlAdvice, completed.grammarControlAdvice())
							.set(InterviewReportEntity::getVocabularyExpressionScore,
									completed.vocabularyExpressionScore())
							.set(InterviewReportEntity::getVocabularyExpressionEvaluation,
									completed.vocabularyExpressionEvaluation())
							.set(InterviewReportEntity::getVocabularyExpressionAdvice,
									completed.vocabularyExpressionAdvice())
							.set(InterviewReportEntity::getFailureReason, null));
			if (updated != 1) {
				LOGGER.warn(
						"interview report already terminal; skip completed write sessionId={}",
						completed.sessionId());
			}
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	@Override
	public void markFailed(String sessionId, String failureReason) {
		try {
			mapper.update(
					null,
					new LambdaUpdateWrapper<InterviewReportEntity>()
							.eq(InterviewReportEntity::getSessionId, sessionId)
							.eq(InterviewReportEntity::getStatus, ReportStatus.PROCESSING.name())
							.set(InterviewReportEntity::getStatus, ReportStatus.FAILED.name())
							.set(InterviewReportEntity::getFailureReason, failureReason));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	@Override
	public boolean retryFromFailed(String sessionId, int expectedRetryCount) {
		try {
			return mapper.update(
					null,
					new LambdaUpdateWrapper<InterviewReportEntity>()
							.eq(InterviewReportEntity::getSessionId, sessionId)
							.eq(InterviewReportEntity::getStatus, ReportStatus.FAILED.name())
							.eq(InterviewReportEntity::getRetryCount, expectedRetryCount)
							.set(InterviewReportEntity::getStatus, ReportStatus.PROCESSING.name())
							.set(InterviewReportEntity::getRetryCount, expectedRetryCount + 1)
							.set(InterviewReportEntity::getFailureReason, null)) == 1;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	@Override
	public boolean casFailedToProcessing(String sessionId) {
		try {
			return mapper.update(
					null,
					new LambdaUpdateWrapper<InterviewReportEntity>()
							.eq(InterviewReportEntity::getSessionId, sessionId)
							.eq(InterviewReportEntity::getStatus, ReportStatus.FAILED.name())
							.set(InterviewReportEntity::getStatus, ReportStatus.PROCESSING.name())
							.set(InterviewReportEntity::getFailureReason, null)) == 1;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	@Override
	public List<InterviewReportRecord> findStuckProcessingBefore(OffsetDateTime cutoff) {
		try {
			return mapper.selectList(new LambdaQueryWrapper<InterviewReportEntity>()
							.eq(InterviewReportEntity::getStatus, ReportStatus.PROCESSING.name())
							.lt(InterviewReportEntity::getUpdatedAt, cutoff))
					.stream()
					.map(this::toRecord)
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	private InterviewReportEntity toEntity(InterviewReportRecord record) {
		InterviewReportEntity entity = new InterviewReportEntity();
		entity.setSessionId(record.sessionId());
		entity.setSceneId(record.sceneId());
		entity.setUserId(UUID.fromString(record.userId()));
		entity.setStatus(record.status().name());
		entity.setSummary(record.summary());
		entity.setOverallScore(record.overallScore());
		entity.setFluencyScore(record.fluencyScore());
		entity.setFluencyEvaluation(record.fluencyEvaluation());
		entity.setFluencyAdvice(record.fluencyAdvice());
		entity.setPronunciationIntelligibilityScore(record.pronunciationIntelligibilityScore());
		entity.setPronunciationIntelligibilityEvaluation(record.pronunciationIntelligibilityEvaluation());
		entity.setPronunciationIntelligibilityAdvice(record.pronunciationIntelligibilityAdvice());
		entity.setLogicCoherenceScore(record.logicCoherenceScore());
		entity.setLogicCoherenceEvaluation(record.logicCoherenceEvaluation());
		entity.setLogicCoherenceAdvice(record.logicCoherenceAdvice());
		entity.setGrammarControlScore(record.grammarControlScore());
		entity.setGrammarControlEvaluation(record.grammarControlEvaluation());
		entity.setGrammarControlAdvice(record.grammarControlAdvice());
		entity.setVocabularyExpressionScore(record.vocabularyExpressionScore());
		entity.setVocabularyExpressionEvaluation(record.vocabularyExpressionEvaluation());
		entity.setVocabularyExpressionAdvice(record.vocabularyExpressionAdvice());
		entity.setRetryCount(record.retryCount());
		entity.setFailureReason(record.failureReason());
		entity.setCreatedAt(record.createdAt());
		entity.setUpdatedAt(record.updatedAt());
		return entity;
	}

	private InterviewReportRecord toRecord(InterviewReportEntity entity) {
		return new InterviewReportRecord(
				entity.getSessionId(),
				entity.getSceneId(),
				entity.getUserId() == null ? null : entity.getUserId().toString(),
				entity.getStatus() == null ? null : ReportStatus.valueOf(entity.getStatus()),
				entity.getOverallScore(),
				entity.getSummary(),
				entity.getFluencyScore(),
				entity.getFluencyEvaluation(),
				entity.getFluencyAdvice(),
				entity.getPronunciationIntelligibilityScore(),
				entity.getPronunciationIntelligibilityEvaluation(),
				entity.getPronunciationIntelligibilityAdvice(),
				entity.getLogicCoherenceScore(),
				entity.getLogicCoherenceEvaluation(),
				entity.getLogicCoherenceAdvice(),
				entity.getGrammarControlScore(),
				entity.getGrammarControlEvaluation(),
				entity.getGrammarControlAdvice(),
				entity.getVocabularyExpressionScore(),
				entity.getVocabularyExpressionEvaluation(),
				entity.getVocabularyExpressionAdvice(),
				entity.getRetryCount() == null ? 0 : entity.getRetryCount(),
				entity.getFailureReason(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private BusinessException persistenceFailure(Throwable cause) {
		return new BusinessException(
				InterviewErrorCode.INTERVIEW_REPORT_PERSISTENCE_FAILED,
				"面试报告保存失败");
	}
}
