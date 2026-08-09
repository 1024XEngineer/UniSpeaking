package com.unispeaking.infrastructure.persistence.entity.evaluation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.unispeaking.common.persistence.typehandler.PostgresUuidTypeHandler;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** interview_report 表实体；复合异步态（status/retry_count/failure_reason）+ 五维分数。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("interview_report")
public class InterviewReportEntity {

	@TableId(value = "session_id", type = IdType.INPUT)
	private String sessionId;
	private String sceneId;
	@TableField(typeHandler = PostgresUuidTypeHandler.class)
	private UUID userId;
	private String status;
	private String summary;
	private BigDecimal overallScore;
	private BigDecimal fluencyScore;
	private String fluencyEvaluation;
	private String fluencyAdvice;
	private BigDecimal pronunciationIntelligibilityScore;
	private String pronunciationIntelligibilityEvaluation;
	private String pronunciationIntelligibilityAdvice;
	private BigDecimal logicCoherenceScore;
	private String logicCoherenceEvaluation;
	private String logicCoherenceAdvice;
	private BigDecimal grammarControlScore;
	private String grammarControlEvaluation;
	private String grammarControlAdvice;
	private BigDecimal vocabularyExpressionScore;
	private String vocabularyExpressionEvaluation;
	private String vocabularyExpressionAdvice;
	private Integer retryCount;
	private String failureReason;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
