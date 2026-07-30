package com.unispeaking.service.account.impl;

import com.unispeaking.domain.dto.account.AccountProfileResponse;
import com.unispeaking.domain.dto.account.AvatarResponse;
import com.unispeaking.domain.po.user.UserAccount;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.repository.UserAccountRepository;
import com.unispeaking.service.account.AccountService;
import com.unispeaking.service.account.AvatarStorage;
import com.unispeaking.service.account.AvatarUrlResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AccountServiceImpl.class);
	private static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;

	private final UserAccountRepository repository;
	private final AvatarStorage avatarStorage;
	private final AvatarUrlResolver avatarUrlResolver;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	@Autowired
	public AccountServiceImpl(
			UserAccountRepository repository,
			AvatarStorage avatarStorage,
			AvatarUrlResolver avatarUrlResolver,
			PasswordEncoder passwordEncoder) {
		this(
				repository,
				avatarStorage,
				avatarUrlResolver,
				passwordEncoder,
				Clock.systemUTC());
	}

	public AccountServiceImpl(
			UserAccountRepository repository,
			AvatarStorage avatarStorage,
			AvatarUrlResolver avatarUrlResolver,
			PasswordEncoder passwordEncoder,
			Clock clock) {
		this.repository = repository;
		this.avatarStorage = avatarStorage;
		this.avatarUrlResolver = avatarUrlResolver;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	@Override
	public AccountProfileResponse updateNickname(UUID userId, String nickname) {
		UserAccount current = requireUser(userId);
		String normalized = nickname == null ? "" : nickname.trim();
		if (normalized.isEmpty() || normalized.length() > 32) {
			throw new BusinessException(
					"VALIDATION_ERROR",
					"昵称长度必须为 1–32 个字符");
		}
		UserAccount updated = repository.updateProfile(
				userId,
				normalized,
				current.avatarObjectKey());
		return AccountProfileResponse.from(
				updated,
				avatarUrlResolver.resolve(updated.avatarObjectKey()));
	}

	@Override
	public AvatarResponse uploadAvatar(
			UUID userId,
			String originalFilename,
			String contentType,
			byte[] bytes) {
		UserAccount current = requireUser(userId);
		DetectedImage detectedImage = detectImage(bytes);
		String normalizedContentType = contentType == null
				? ""
				: contentType.trim().toLowerCase(Locale.ROOT);
		if (!detectedImage.contentType.equals(normalizedContentType)) {
			throw invalidAvatar();
		}
		String objectKey = "avatars/%s/%s.%s".formatted(
				userId,
				UUID.randomUUID(),
				detectedImage.extension);
		avatarStorage.put(objectKey, detectedImage.contentType, bytes);
		UserAccount updated;
		try {
			updated = repository.updateProfile(
					userId,
					current.nickname(),
					objectKey);
		}
		catch (RuntimeException exception) {
			tryDeleteCompensation(objectKey);
			throw exception;
		}
		if (current.avatarObjectKey() != null
				&& !current.avatarObjectKey().equals(objectKey)) {
			tryDeleteOldAvatar(current.avatarObjectKey());
		}
		return new AvatarResponse(avatarUrlResolver.resolve(updated.avatarObjectKey()));
	}

	@Override
	public void deleteAvatar(UUID userId) {
		UserAccount current = requireUser(userId);
		if (current.avatarObjectKey() == null) {
			return;
		}
		repository.updateProfile(userId, current.nickname(), null);
		tryDeleteOldAvatar(current.avatarObjectKey());
	}

	@Override
	@Transactional
	public void changePassword(
			UUID userId,
			String currentPassword,
			String newPassword) {
		UserAccount current = requireUser(userId);
		if (!passwordEncoder.matches(currentPassword, current.passwordHash())) {
			throw new BusinessException(
					"CURRENT_PASSWORD_INVALID",
					"当前密码不正确");
		}
		if (passwordEncoder.matches(newPassword, current.passwordHash())) {
			throw new BusinessException(
					"NEW_PASSWORD_SAME_AS_CURRENT",
					"新密码不能与当前密码相同");
		}
		repository.updatePasswordAndAuthVersion(
				userId,
				passwordEncoder.encode(newPassword),
				current.authVersion() + 1);
	}

	@Override
	@Transactional
	public void requestDeletion(UUID userId, String currentPassword) {
		UserAccount current = requireUser(userId);
		if (!passwordEncoder.matches(currentPassword, current.passwordHash())) {
			throw new BusinessException(
					"CURRENT_PASSWORD_INVALID",
					"当前密码不正确");
		}
		Instant requestedAt = clock.instant();
		repository.requestDeletion(
				userId,
				current.authVersion() + 1,
				requestedAt,
				requestedAt.plus(30, ChronoUnit.DAYS));
	}

	private UserAccount requireUser(UUID userId) {
		return repository.findById(Objects.requireNonNull(userId, "userId"))
				.orElseThrow(() -> new BusinessException(
						"USER_NOT_FOUND",
						"用户不存在"));
	}

	private DetectedImage detectImage(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			throw invalidAvatar();
		}
		if (bytes.length > MAX_AVATAR_BYTES) {
			throw new BusinessException(
					"AVATAR_TOO_LARGE",
					"头像不能超过 2 MiB");
		}
		if (startsWith(bytes, new int[] {0xFF, 0xD8, 0xFF})) {
			return new DetectedImage("image/jpeg", "jpg");
		}
		if (startsWith(bytes, new int[] {
			0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
		})) {
			return new DetectedImage("image/png", "png");
		}
		if (bytes.length >= 12
				&& asciiEquals(bytes, 0, "RIFF")
				&& asciiEquals(bytes, 8, "WEBP")) {
			return new DetectedImage("image/webp", "webp");
		}
		throw invalidAvatar();
	}

	private boolean startsWith(byte[] bytes, int[] signature) {
		if (bytes.length < signature.length) {
			return false;
		}
		for (int index = 0; index < signature.length; index++) {
			if ((bytes[index] & 0xFF) != signature[index]) {
				return false;
			}
		}
		return true;
	}

	private boolean asciiEquals(byte[] bytes, int offset, String expected) {
		for (int index = 0; index < expected.length(); index++) {
			if (bytes[offset + index] != expected.charAt(index)) {
				return false;
			}
		}
		return true;
	}

	private void tryDeleteCompensation(String objectKey) {
		try {
			avatarStorage.delete(objectKey);
		}
		catch (RuntimeException cleanupFailure) {
			LOGGER.error("Failed to compensate newly uploaded avatar object");
		}
	}

	private void tryDeleteOldAvatar(String objectKey) {
		try {
			avatarStorage.delete(objectKey);
		}
		catch (RuntimeException cleanupFailure) {
			LOGGER.warn("Failed to clean up replaced avatar object");
		}
	}

	private BusinessException invalidAvatar() {
		return new BusinessException(
				"INVALID_AVATAR_FILE",
				"头像必须是有效的 JPEG、PNG 或 WebP 图片");
	}

	private record DetectedImage(String contentType, String extension) {
	}
}
