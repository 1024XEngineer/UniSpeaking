package com.unispeaking.infrastructure.persistence.entity.session;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.unispeaking.common.persistence.typehandler.PostgresUuidTypeHandler;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName(value = "practice_session", autoResultMap = true)
public class PracticeSessionEntity {

	@TableId(value = "session_id", type = IdType.INPUT)
	private String sessionId;
	@TableField(typeHandler = PostgresUuidTypeHandler.class)
	private UUID userId;
	private String sceneId;
	private String sceneType;
	private String status;
	private OffsetDateTime startedAt;
	private OffsetDateTime endedAt;
	private String providerSessionId;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
