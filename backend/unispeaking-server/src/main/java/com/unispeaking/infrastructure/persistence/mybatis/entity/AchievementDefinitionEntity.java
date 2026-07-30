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
@TableName("achievement_definitions")
public class AchievementDefinitionEntity {

	@TableId(value = "id", type = IdType.INPUT)
	private UUID id;
	private String code;
	private String name;
	private String description;
	private String category;
	private String metricKey;
	private Long targetValue;
	private String iconKey;
	private Integer sortOrder;
	private String status;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
