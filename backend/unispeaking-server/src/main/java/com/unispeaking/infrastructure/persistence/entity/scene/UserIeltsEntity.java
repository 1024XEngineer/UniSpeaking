package com.unispeaking.infrastructure.persistence.entity.scene;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("user_ielts")
public class UserIeltsEntity {

	@TableId(value = "user_id", type = IdType.INPUT)
	private UUID userId;
	private BigDecimal targetScore;
	private Integer todayCompletedCount;
	private String preferredVoice;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
