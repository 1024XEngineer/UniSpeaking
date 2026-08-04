package com.unispeaking.infrastructure.persistence.entity.scene;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("ielts_topic")
public class IeltsTopicEntity {

	@TableId(value = "id", type = IdType.INPUT)
	private String id;
	private String title;
	private String topicType;
	private String category;
	private String source;
	private String status;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
