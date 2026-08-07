package com.unispeaking.service.profile.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.profile.AvatarResponse;
import com.unispeaking.domain.dto.profile.UpdateProfileRequest;
import com.unispeaking.domain.dto.profile.UpdateProfileResponse;
import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.infrastructure.config.ObjectStorageProperties;
import com.unispeaking.infrastructure.persistence.repository.user.UserAccountRepository;
import com.unispeaking.infrastructure.storage.ObjectStorageProvider;
import com.unispeaking.service.profile.ProfileAccountService;
import com.unispeaking.component.profile.image.AvatarImageProcessor;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProfileAccountServiceImpl implements ProfileAccountService {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProfileAccountServiceImpl.class);
	private final UserAccountRepository accounts;
	private final ObjectStorageProvider storage;
	private final ObjectStorageProperties properties;
	private final AvatarImageProcessor images;

	public ProfileAccountServiceImpl(
			UserAccountRepository accounts,
			ObjectStorageProvider storage,
			ObjectStorageProperties properties,
			AvatarImageProcessor images) {
		this.accounts = accounts;
		this.storage = storage;
		this.properties = properties;
		this.images = images;
	}

	@Override
	public UpdateProfileResponse updateNickname(String userId, UpdateProfileRequest request) {
		String nickname = request.nickname().trim();
		if (nickname.isEmpty()) {
			throw new BusinessException("PROFILE_NICKNAME_REQUIRED", "昵称不能为空");
		}
		UUID id = UUID.fromString(userId);
		if (!accounts.updateNickname(id, nickname)) {
			throw new BusinessException("PROFILE_UPDATE_CONFLICT", "资料已发生变化，请重试");
		}
		return new UpdateProfileResponse(nickname, nickname);
	}

	@Override
	public AvatarResponse replaceAvatar(String userId, byte[] content) {
		if (!storage.available()) {
			throw new BusinessException("OBJECT_STORAGE_UNAVAILABLE", "对象存储尚未配置");
		}
		UUID id = UUID.fromString(userId);
		UserAccount user = accounts.findById(id)
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在"));
		var avatar = images.process(content);
		String prefix = properties.getAvatarPrefix();
		String key = prefix + "/" + userId + "/" + UUID.randomUUID() + "." + avatar.extension();
		storage.put(key, avatar.content(), avatar.contentType());
		if (!accounts.updateAvatarObjectKey(id, user.avatarObjectKey(), key)) {
			safeDelete(key);
			throw new BusinessException("PROFILE_UPDATE_CONFLICT", "头像已发生变化，请重试");
		}
		URI signed = signAvatar(key);
		if (user.avatarObjectKey() != null) safeDelete(user.avatarObjectKey());
		return new AvatarResponse(
				signed == null ? null : signed.toString(),
				signed == null ? null : Instant.now().plus(properties.getSignedUrlTtl()));
	}

	private URI signAvatar(String key) {
		try {
			return storage.signGetUrl(key, properties.getSignedUrlTtl());
		}
		catch (BusinessException exception) {
			LOGGER.warn("avatar url signing failed");
			return null;
		}
	}

	private void safeDelete(String key) {
		try {
			storage.delete(key);
		}
		catch (BusinessException exception) {
			LOGGER.warn("avatar object cleanup failed");
		}
	}
}
