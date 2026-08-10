package com.unispeaking.infrastructure.persistence.entity.scene;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.unispeaking.common.persistence.typehandler.PostgresJsonbStringTypeHandler;
import com.unispeaking.common.persistence.typehandler.PostgresUuidTypeHandler;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName(value = "interview_scene", autoResultMap = true)
public class InterviewSceneEntity {

	@TableId(value = "scene_id", type = IdType.INPUT)
	private String sceneId;
	@TableField(typeHandler = PostgresUuidTypeHandler.class)
	private UUID userId;
	@TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
	private String confirmedMaterial;
	private String finalText;
	@TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
	private String interviewContext;
	private String difficulty;
	private String scenePrompt;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
	private OffsetDateTime deletedAt;
}
