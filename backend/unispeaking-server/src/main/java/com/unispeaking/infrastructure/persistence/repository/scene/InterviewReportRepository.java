package com.unispeaking.infrastructure.persistence.repository.scene;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.scene.InterviewReportRecord;
import com.unispeaking.domain.vo.scene.InterviewReportDimension;
import com.unispeaking.domain.vo.scene.InterviewReportType;
import com.unispeaking.infrastructure.persistence.entity.scene.InterviewReportEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.InterviewReportMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InterviewReportRepository {

	private final InterviewReportMapper mapper;

	public InterviewReportRepository(InterviewReportMapper mapper) {
		this.mapper = mapper;
	}

	public void save(InterviewReportRecord report) {
		try {
			if (mapper.insert(toEntity(report)) != 1) {
				throw persistenceFailure();
			}
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<InterviewReportRecord> findByInterviewId(
			String interviewId) {
		try {
			InterviewReportEntity entity = mapper.selectOne(
					new LambdaQueryWrapper<InterviewReportEntity>()
							.eq(
									InterviewReportEntity::getInterviewId,
									interviewId));
			return entity == null
					? Optional.empty()
					: Optional.of(toDomain(entity));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public int deleteByInterviewId(String interviewId) {
		try {
			return mapper.delete(
					new LambdaQueryWrapper<InterviewReportEntity>()
							.eq(
									InterviewReportEntity::getInterviewId,
									interviewId));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private InterviewReportEntity toEntity(InterviewReportRecord report) {
		InterviewReportEntity entity = new InterviewReportEntity();
		entity.setInterviewId(report.interviewId());
		entity.setReportType(report.reportType().name());
		entity.setOverallScore(report.overallScore());
		entity.setOverallSummary(report.overallSummary());
		writeDimension(entity, report);
		entity.setCreatedAt(report.createdAt());
		entity.setUpdatedAt(report.updatedAt());
		return entity;
	}

	private void writeDimension(
			InterviewReportEntity entity,
			InterviewReportRecord report) {
		entity.setFluencyScore(report.fluency().score());
		entity.setFluencyEvaluation(report.fluency().evaluation());
		entity.setFluencyActionSuggestion(
				report.fluency().actionSuggestion());
		entity.setLogicCoherenceScore(report.logicCoherence().score());
		entity.setLogicCoherenceEvaluation(
				report.logicCoherence().evaluation());
		entity.setLogicCoherenceActionSuggestion(
				report.logicCoherence().actionSuggestion());
		entity.setGrammarControlScore(report.grammarControl().score());
		entity.setGrammarControlEvaluation(
				report.grammarControl().evaluation());
		entity.setGrammarControlActionSuggestion(
				report.grammarControl().actionSuggestion());
		entity.setPronunciationIntelligibilityScore(
				report.pronunciationIntelligibility().score());
		entity.setPronunciationIntelligibilityEvaluation(
				report.pronunciationIntelligibility().evaluation());
		entity.setPronunciationIntelligibilityActionSuggestion(
				report.pronunciationIntelligibility().actionSuggestion());
		entity.setVocabularyExpressionScore(
				report.vocabularyExpression().score());
		entity.setVocabularyExpressionEvaluation(
				report.vocabularyExpression().evaluation());
		entity.setVocabularyExpressionActionSuggestion(
				report.vocabularyExpression().actionSuggestion());
	}

	private InterviewReportRecord toDomain(InterviewReportEntity entity) {
		return new InterviewReportRecord(
				entity.getInterviewId(),
				InterviewReportType.valueOf(entity.getReportType()),
				entity.getOverallScore(),
				entity.getOverallSummary(),
				dimension(
						entity.getFluencyScore(),
						entity.getFluencyEvaluation(),
						entity.getFluencyActionSuggestion()),
				dimension(
						entity.getLogicCoherenceScore(),
						entity.getLogicCoherenceEvaluation(),
						entity.getLogicCoherenceActionSuggestion()),
				dimension(
						entity.getGrammarControlScore(),
						entity.getGrammarControlEvaluation(),
						entity.getGrammarControlActionSuggestion()),
				dimension(
						entity.getPronunciationIntelligibilityScore(),
						entity.getPronunciationIntelligibilityEvaluation(),
						entity.getPronunciationIntelligibilityActionSuggestion()),
				dimension(
						entity.getVocabularyExpressionScore(),
						entity.getVocabularyExpressionEvaluation(),
						entity.getVocabularyExpressionActionSuggestion()),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private InterviewReportDimension dimension(
			java.math.BigDecimal score,
			String evaluation,
			String actionSuggestion) {
		return new InterviewReportDimension(
				score,
				evaluation,
				actionSuggestion);
	}

	private BusinessException persistenceFailure() {
		return new BusinessException(
				"INTERVIEW_REPORT_PERSISTENCE_FAILED",
				"Interview report persistence operation failed");
	}
}
