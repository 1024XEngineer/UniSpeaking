package com.unispeaking.infrastructure.persistence.entity.scene;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.unispeaking.infrastructure.persistence.typehandler.PostgresJsonbStringTypeHandler;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName(value = "ielts_question", autoResultMap = true)
public class IeltsQuestionEntity {

	@TableId(value = "id", type = IdType.INPUT)
	private String id;
	private String topicId;
	private String part;
	private Integer sortNo;
	private String questionText;
	@TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
	private String cuePoints;
	@TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
	private String recommendedExpressions;
}
