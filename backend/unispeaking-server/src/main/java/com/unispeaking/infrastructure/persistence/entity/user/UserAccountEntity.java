package com.unispeaking.infrastructure.persistence.entity.user;

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
@TableName("\"user\"")
public class UserAccountEntity {

	@TableId(value = "id", type = IdType.INPUT)
	private UUID id;
	private String username;
	private String passwordHash;
	private String nickname;
	private String avatarObjectKey;
	private String role;
	private String status;
	private Long authVersion;
	private OffsetDateTime lastLoginAt;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
