package com.unispeaking.infrastructure.persistence.entity.achievement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("user_achievement_state")
public class UserAchievementStateEntity {

	@TableId(value = "user_id", type = IdType.INPUT)
	private UUID userId;
	private OffsetDateTime initializedAt;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
