package com.unispeaking.infrastructure.persistence.entity.evaluation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.unispeaking.infrastructure.persistence.typehandler.PostgresJsonbStringTypeHandler;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName(value = "sentence_evaluation", autoResultMap = true)
public class SentenceEvaluationEntity {

	@TableId(value = "id", type = IdType.INPUT)
	private String id;
	private String sceneId;
	private String sentenceId;
	private BigDecimal overallScore;
	@TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
	private String scoreDetail;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
