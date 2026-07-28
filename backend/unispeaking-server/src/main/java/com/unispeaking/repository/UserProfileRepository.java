package com.unispeaking.repository;

import com.unispeaking.domain.po.profile.UserProfile;
import java.util.Optional;

public interface UserProfileRepository {
	Optional<UserProfile> findByUserId(String userId);
	UserProfile save(UserProfile profile);
}
