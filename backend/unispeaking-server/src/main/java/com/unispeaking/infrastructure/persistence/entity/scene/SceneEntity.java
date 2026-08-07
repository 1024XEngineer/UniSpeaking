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
@TableName(value = "scene", autoResultMap = true)
public class SceneEntity {

	@TableId(value = "id", type = IdType.INPUT)
	private String id;
	@TableField(typeHandler = PostgresUuidTypeHandler.class)
	private UUID userId;
	private String title;
	private String background;
	private String aiRole;
	private String userRole;
	private String learningGoal;
	private String customInstruction;
	@TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
	private String successFactor;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
	private OffsetDateTime deletedAt;
}
