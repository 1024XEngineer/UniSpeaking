package com.unispeaking.infrastructure.persistence.mybatis.entity;

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
@TableName("phrase")
public class ScenePhraseEntity {

	@TableId(value = "id", type = IdType.INPUT)
	private String id;
	private String sceneId;
	private String phrase;
	private String phonetic;
	private String translation;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
