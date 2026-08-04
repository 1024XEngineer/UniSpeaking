package com.unispeaking.infrastructure.persistence.entity.achievement;

import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName(value = "user_achievement_unlock", autoResultMap = true)
/**
 * The database primary key is {@code (user_id, achievement_id)}. Repository
 * updates must always include both columns because MyBatis-Plus does not
 * support composite {@code @TableId} mappings.
 */
public class UserAchievementUnlockEntity {

	@TableField(typeHandler = PostgresUuidTypeHandler.class)
	private UUID userId;
	private String achievementId;
	private OffsetDateTime unlockedAt;
	private OffsetDateTime acknowledgedAt;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
