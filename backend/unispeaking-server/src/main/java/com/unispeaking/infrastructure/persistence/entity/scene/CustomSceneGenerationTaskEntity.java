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
@TableName(value = "custom_scene_generation_task", autoResultMap = true)
public class CustomSceneGenerationTaskEntity {

	@TableId(value = "task_id", type = IdType.INPUT)
	private String taskId;
	@TableField(typeHandler = PostgresUuidTypeHandler.class)
	private UUID userId;
	private String sceneId;
	private String sceneInput;
	private String userPreference;
	private String status;
	@TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
	private String resultJson;
	private String failureReason;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
