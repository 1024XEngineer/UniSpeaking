package com.unispeaking.infrastructure.persistence.repository.user;

import com.unispeaking.domain.po.auth.UserAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {
	Optional<UserAccount> findById(UUID id);
	Optional<UserAccount> findByUsername(String username);
	UserAccount create(UserAccount user);
	void updateLastLoginAt(UUID id, Instant lastLoginAt);
	boolean updateNickname(UUID id, String nickname);
	boolean updateAvatarObjectKey(UUID id, String expectedObjectKey, String newObjectKey);
	boolean updatePasswordAndAuthVersion(
			UUID id,
			long expectedAuthVersion,
			String passwordHash);
}
