package com.unispeaking.repository;

import com.unispeaking.domain.po.user.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {
	Optional<UserAccount> findById(UUID id);
	Optional<UserAccount> findByUsername(String username);
	UserAccount create(UserAccount user);
	void updateLastLoginAt(UUID id, Instant lastLoginAt);
	UserAccount updateProfile(UUID id, String nickname, String avatarObjectKey);
	UserAccount updatePasswordAndAuthVersion(UUID id, String passwordHash, long authVersion);
	UserAccount requestDeletion(
			UUID id,
			long authVersion,
			Instant requestedAt,
			Instant scheduledAt);
	UserAccount reactivate(UUID id, long authVersion);
	List<UserAccount> findDeletionDueBefore(Instant cutoff, int limit);
	void deleteById(UUID id);
}
