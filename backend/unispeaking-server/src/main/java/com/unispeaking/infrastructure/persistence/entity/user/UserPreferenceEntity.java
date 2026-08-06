package com.unispeaking.infrastructure.persistence.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.unispeaking.infrastructure.persistence.typehandler.PostgresJsonbStringTypeHandler;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("user_preference")
public class UserPreferenceEntity {

	@TableId(value = "user_id", type = IdType.INPUT)
	private UUID userId;
	private String preferredVoice;
	private String preferredAiSpeechSpeed;
	@TableField(typeHandler = PostgresJsonbStringTypeHandler.class)
	private String preferences;
	private String memoryText;
	private String cefrLevel;
	private Integer weeklyDurationTargetMinutes;
	private Integer weeklyTrainingCountTarget;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
