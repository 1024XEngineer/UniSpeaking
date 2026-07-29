package com.unispeaking.repository;

import com.unispeaking.domain.po.user.UserAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {
	Optional<UserAccount> findById(UUID id);
	Optional<UserAccount> findByUsername(String username);
	UserAccount create(UserAccount user);
	void updateLastLoginAt(UUID id, Instant lastLoginAt);
}
