package com.unispeaking.infrastructure.persistence.entity.session;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("session_message")
/**
 * The database primary key is (session_id, message_no). MyBatis-Plus does not
 * support composite {@code @TableId} mappings, so repository operations must
 * always address this entity with a wrapper containing both key columns.
 */
public class SessionMessageEntity {

	private String sceneId;
	private String sessionId;
	private Integer messageNo;
	private Integer owner;
	private String content;
	private String audioUrl;
	private OffsetDateTime createdAt;
	private OffsetDateTime updateAt;
}
