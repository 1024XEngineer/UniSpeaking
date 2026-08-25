package com.unispeaking.infrastructure.persistence.entity.evaluation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.unispeaking.common.persistence.typehandler.PostgresTextArrayTypeHandler;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName(value = "ielts_evaluation", autoResultMap = true)
public class IeltsEvaluationEntity {

	@TableId(value = "evaluation_id", type = IdType.INPUT)
	private String evaluationId;
	private String ieltsId;
	private BigDecimal overallBandScore;
	private BigDecimal fluencyCoherenceScore;
	private BigDecimal lexicalResourceScore;
	private BigDecimal grammaticalRangeAccuracyScore;
	private BigDecimal pronunciationScore;
	private String fluencyCoherenceReason;
	private String lexicalResourceReason;
	private String grammaticalRangeAccuracyReason;
	private String pronunciationReason;
	private String summary;
	@TableField(typeHandler = PostgresTextArrayTypeHandler.class)
	private String[] strengths;
	@TableField(typeHandler = PostgresTextArrayTypeHandler.class)
	private String[] improvements;
	@TableField(typeHandler = PostgresTextArrayTypeHandler.class)
	private String[] recommendedExpressions;
	private String evaluationStatus;
	private String failureReason;
	private String leaseToken;
	private OffsetDateTime leaseExpiresAt;
	private OffsetDateTime completedAt;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
