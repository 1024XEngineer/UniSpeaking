package com.unispeaking.infrastructure.persistence.entity.evaluation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.unispeaking.infrastructure.persistence.typehandler.PostgresJsonbStringTypeHandler;
import com.unispeaking.infrastructure.persistence.typehandler.PostgresTextArrayTypeHandler;
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

	@TableId(value = "session_id", type = IdType.INPUT)
	private String sessionId;
	private String ieltsId;
	private String part;
	private String assessmentType;
	private BigDecimal overallBandScore;
	private BigDecimal fluencyCoherenceScore;
	private BigDecimal lexicalResourceScore;
	private BigDecimal grammaticalRangeAccuracyScore;
	private BigDecimal pronunciationScore;
	private String summary;
	@TableField(typeHandler = PostgresTextArrayTypeHandler.class)
	private String[] strengths;
	@TableField(typeHandler = PostgresTextArrayTypeHandler.class)
	private String[] improvements;
	@TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
	private String partEvaluations;
	@TableField(typeHandler = PostgresTextArrayTypeHandler.class)
	private String[] recommendedExpressions;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
