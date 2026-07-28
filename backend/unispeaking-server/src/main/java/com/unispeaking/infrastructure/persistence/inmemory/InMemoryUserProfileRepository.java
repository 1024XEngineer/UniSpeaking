package com.unispeaking.infrastructure.persistence.inmemory;

import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.repository.UserProfileRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryUserProfileRepository implements UserProfileRepository {

	private final Map<String, UserProfile> profiles = new ConcurrentHashMap<>();

	@Override
	public Optional<UserProfile> findByUserId(String userId) {
		return Optional.ofNullable(profiles.get(userId));
	}

	@Override
	public UserProfile save(UserProfile profile) {
		profiles.put(profile.userId(), profile);
		return profile;
	}
}
