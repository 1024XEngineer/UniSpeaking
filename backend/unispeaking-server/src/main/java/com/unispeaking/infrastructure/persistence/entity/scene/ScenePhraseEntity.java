package com.unispeaking.infrastructure.persistence.entity.scene;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("phrase")
/**
 * The database primary key is (scene_id, phrase_id). MyBatis-Plus does not
 * support composite {@code @TableId} mappings, so repository operations must
 * always address this entity with a wrapper containing both key columns.
 */
public class ScenePhraseEntity {

	private String sceneId;
	private String phraseId;
	private String phrase;
	private String phonetic;
	private String translation;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
