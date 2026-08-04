package com.unispeaking.infrastructure.persistence.entity.scene;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("interview_report")
public class InterviewReportEntity {

	@TableId(value = "interview_id", type = IdType.INPUT)
	private String interviewId;
	private String reportType;
	private BigDecimal overallScore;
	private String overallSummary;
	private BigDecimal fluencyScore;
	private String fluencyEvaluation;
	private String fluencyActionSuggestion;
	private BigDecimal logicCoherenceScore;
	private String logicCoherenceEvaluation;
	private String logicCoherenceActionSuggestion;
	private BigDecimal grammarControlScore;
	private String grammarControlEvaluation;
	private String grammarControlActionSuggestion;
	private BigDecimal pronunciationIntelligibilityScore;
	private String pronunciationIntelligibilityEvaluation;
	private String pronunciationIntelligibilityActionSuggestion;
	private BigDecimal vocabularyExpressionScore;
	private String vocabularyExpressionEvaluation;
	private String vocabularyExpressionActionSuggestion;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
