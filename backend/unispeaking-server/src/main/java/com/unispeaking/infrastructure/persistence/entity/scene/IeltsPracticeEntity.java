package com.unispeaking.infrastructure.persistence.entity.scene;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.unispeaking.infrastructure.persistence.typehandler.PostgresJsonbStringTypeHandler;
import com.unispeaking.infrastructure.persistence.typehandler.PostgresUuidTypeHandler;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName(value = "ielts", autoResultMap = true)
public class IeltsPracticeEntity {

	@TableId(value = "ielts_id", type = IdType.INPUT)
	private String ieltsId;
	@TableField(typeHandler = PostgresUuidTypeHandler.class)
	private UUID userId;
	private String mode;
	private String selectedPart;
	private String selectedTopicId;
	private String topicSelectionMethod;
	private String part1TopicId;
	private String part2TopicId;
	private String part3TopicId;
	@TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
	private String content;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
