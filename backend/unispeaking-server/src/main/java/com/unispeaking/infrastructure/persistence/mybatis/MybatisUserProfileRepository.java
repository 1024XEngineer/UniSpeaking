package com.unispeaking.infrastructure.persistence.mybatis;

import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.infrastructure.persistence.mybatis.entity.UserPreferenceEntity;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.UserPreferenceMapper;
import com.unispeaking.repository.UserProfileRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisUserProfileRepository implements UserProfileRepository {

	private final UserPreferenceMapper mapper;

	public MybatisUserProfileRepository(UserPreferenceMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public Optional<UserProfile> findByUserId(String userId) {
		return Optional.ofNullable(mapper.selectById(UUID.fromString(userId)))
				.map(this::toDomain);
	}

	@Override
	public UserProfile save(UserProfile profile) {
		UUID userId = UUID.fromString(profile.userId());
		UserPreferenceEntity entity = mapper.selectById(userId);
		if (entity == null) {
			entity = new UserPreferenceEntity();
			entity.setUserId(userId);
			entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
			copyPreferences(profile, entity);
			mapper.insert(entity);
		}
		else {
			copyPreferences(profile, entity);
			mapper.updateById(entity);
		}
		return profile;
	}

	private void copyPreferences(UserProfile profile, UserPreferenceEntity entity) {
		entity.setPreferredVoice(profile.voiceId());
		entity.setPreferredAiSpeechSpeed(profile.aiSpeechSpeed());
		entity.setMemoryText(profile.memoryText());
		entity.setCefrLevel(profile.level());
		entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
	}

	private UserProfile toDomain(UserPreferenceEntity entity) {
		return new UserProfile(
				entity.getUserId().toString(),
				entity.getCefrLevel(),
				entity.getPreferredVoice(),
				entity.getPreferredAiSpeechSpeed(),
				"zh-CN",
				entity.getMemoryText(),
				entity.getPreferences());
	}
}
