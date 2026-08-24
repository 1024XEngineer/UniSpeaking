package com.unispeaking.infrastructure.persistence.entity.auth;

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
@TableName("auth_refresh_tokens")
public class AuthRefreshTokenEntity {
    @TableId
    private String tokenDigest;
    private UUID userId;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastUsedAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
}
