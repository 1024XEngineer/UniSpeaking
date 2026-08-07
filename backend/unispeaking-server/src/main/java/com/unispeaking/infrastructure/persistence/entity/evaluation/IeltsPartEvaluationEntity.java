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
@TableName(value = "ielts_part_evaluation", autoResultMap = true)
public class IeltsPartEvaluationEntity {
	@TableId(value = "part_evaluation_id", type = IdType.INPUT)
	private String partEvaluationId;
	private String ieltsId;
	private String sessionId;
	private String part;
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
	private OffsetDateTime completedAt;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
