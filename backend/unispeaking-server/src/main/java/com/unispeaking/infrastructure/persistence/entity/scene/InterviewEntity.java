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
@TableName(value = "interview", autoResultMap = true)
public class InterviewEntity {

	@TableId(value = "id", type = IdType.INPUT)
	private String id;
	@TableField(typeHandler = PostgresUuidTypeHandler.class)
	private UUID userId;
	private String sessionId;
	private String jobTitle;
	private String difficulty;
	@TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
	private String roleSummary;
	private String recordingObjectKey;
	private Integer recordingDurationSeconds;
	private OffsetDateTime completedAt;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
