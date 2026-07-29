package com.unispeaking.infrastructure.persistence.mybatis.entity;

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
@TableName("user_preference")
public class UserPreferenceEntity {

	@TableId(value = "user_id", type = IdType.INPUT)
	private UUID userId;
	private String preferredVoice;
	private String preferredAiSpeechSpeed;
	private String memoryText;
	private String cefrLevel;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
