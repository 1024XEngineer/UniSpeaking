package com.unispeaking.infrastructure.persistence.entity.feedback;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.unispeaking.infrastructure.persistence.typehandler.PostgresUuidTypeHandler;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("user_feedback")
public class UserFeedbackEntity {

	@TableId(value = "id", type = IdType.INPUT)
	private UUID id;
	private String feedbackNo;
	@TableField(typeHandler = PostgresUuidTypeHandler.class)
	private UUID userId;
	private String lookupCodeHash;
	private String categoryId;
	private String title;
	private String description;
	private String environment;
	private String status;
	private String reply;
	private OffsetDateTime repliedAt;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
