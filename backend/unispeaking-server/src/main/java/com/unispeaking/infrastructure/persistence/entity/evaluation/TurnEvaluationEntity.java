package com.unispeaking.infrastructure.persistence.entity.evaluation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.unispeaking.common.persistence.typehandler.PostgresJsonbStringTypeHandler;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("turn_evaluation")
/**
 * The database primary key is (session_id, turn_no). MyBatis-Plus does not
 * support composite {@code @TableId} mappings, so repository operations must
 * always address this entity with a wrapper containing both key columns.
 */
public class TurnEvaluationEntity {

	private String sessionId;
	private Integer turnNo;
	private String sceneId;
	private String transcript;
	private BigDecimal overallScore;
	private BigDecimal rhythmScore;
	private BigDecimal toneScore;
	private BigDecimal integrityScore;
	private BigDecimal pronunciationScore;
	private BigDecimal fluencyScore;
	private String feedbackSummary;
	private String suggestedExpression;
	@TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
	private String pronunciationDetails;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
