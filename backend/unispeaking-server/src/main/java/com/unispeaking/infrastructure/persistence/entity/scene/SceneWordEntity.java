package com.unispeaking.infrastructure.persistence.entity.scene;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("\"word\"")
/**
 * The database primary key is (scene_id, word_id). MyBatis-Plus does not
 * support composite {@code @TableId} mappings, so repository operations must
 * always address this entity with a wrapper containing both key columns.
 */
public class SceneWordEntity {

	private String wordId;
	private String sceneId;
	private String word;
	private String phonetic;
	private String translation;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
