package com.unispeaking.service.profile.impl;

import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.repository.UserProfileRepository;
import com.unispeaking.service.profile.ProfileService;
import org.springframework.stereotype.Service;

@Service
public class ProfileServiceImpl implements ProfileService {

	private final UserProfileRepository repository;

	public ProfileServiceImpl(UserProfileRepository repository) {
		this.repository = repository;
	}

	@Override
	public UserProfile getProfile(String userId) {
		return repository.findByUserId(userId)
				.orElseGet(() -> repository.save(
						new UserProfile(userId, "INTERMEDIATE", "Katerina", "zh-CN", "")));
	}

	@Override
	public UserProfile updateMemory(String userId, String memory) {
		UserProfile profile = getProfile(userId);
		return repository.save(profile.withMemory(memory));
	}
}
