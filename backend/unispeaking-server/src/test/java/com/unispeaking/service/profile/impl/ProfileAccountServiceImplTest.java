package com.unispeaking.service.profile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.profile.AvatarResponse;
import com.unispeaking.domain.dto.profile.UpdateProfileRequest;
import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.domain.po.auth.UserRole;
import com.unispeaking.domain.po.auth.UserStatus;
import com.unispeaking.infrastructure.config.ObjectStorageProperties;
import com.unispeaking.infrastructure.persistence.repository.user.UserAccountRepository;
import com.unispeaking.infrastructure.storage.ObjectStorageProvider;
import com.unispeaking.component.profile.image.AvatarImageProcessor;
import com.unispeaking.component.profile.image.AvatarImageProcessor.ProcessedAvatar;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfileAccountServiceImplTest {

	private final UserAccountRepository accounts = mock(UserAccountRepository.class);
	private final ObjectStorageProvider storage = mock(ObjectStorageProvider.class);
	private final AvatarImageProcessor images = mock(AvatarImageProcessor.class);
	private final ObjectStorageProperties properties = new ObjectStorageProperties();
	private ProfileAccountServiceImpl service;

	@BeforeEach
	void setUp() {
		properties.setAvatarPrefix("/profile-avatars/");
		properties.setSignedUrlTtl(Duration.ofMinutes(30));
		service = new ProfileAccountServiceImpl(accounts, storage, properties, images);
	}

	@Test
	void trimsAndUpdatesNickname() {
		UUID userId = UUID.randomUUID();
		when(accounts.updateNickname(userId, "新昵称")).thenReturn(true);

		var response = service.updateNickname(
				userId.toString(),
				new UpdateProfileRequest("  新昵称  "));

		assertEquals("新昵称", response.nickname());
		assertEquals("新昵称", response.displayName());
	}

	@Test
	void rejectsBlankNickname() {
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.updateNickname(
						UUID.randomUUID().toString(),
						new UpdateProfileRequest("   ")));

		assertEquals("PROFILE_NICKNAME_REQUIRED", exception.code());
	}

	@Test
	void reportsConcurrentNicknameUpdate() {
		UUID userId = UUID.randomUUID();
		when(accounts.updateNickname(userId, "昵称")).thenReturn(false);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.updateNickname(
						userId.toString(),
						new UpdateProfileRequest("昵称")));

		assertEquals("PROFILE_UPDATE_CONFLICT", exception.code());
	}

	@Test
	void replacesAvatarAndDeletesPreviousObject() {
		UUID userId = UUID.randomUUID();
		byte[] original = {1, 2, 3};
		byte[] processed = {4, 5, 6};
		UserAccount user = user(userId, "old/avatar.png");
		when(storage.available()).thenReturn(true);
		when(accounts.findById(userId)).thenReturn(Optional.of(user));
		when(images.process(original))
				.thenReturn(new ProcessedAvatar(processed, "image/png", "png"));
		when(accounts.updateAvatarObjectKey(eq(userId), eq("old/avatar.png"), any()))
				.thenReturn(true);
		when(storage.signGetUrl(any(), eq(Duration.ofMinutes(30))))
				.thenReturn(URI.create("https://cdn.example/avatar.png"));

		AvatarResponse response = service.replaceAvatar(userId.toString(), original);

		assertEquals("https://cdn.example/avatar.png", response.avatarUrl());
		assertNotNull(response.avatarUrlExpiresAt());
		verify(storage).put(
				org.mockito.ArgumentMatchers.startsWith(
						"profile-avatars/" + userId + "/"),
				eq(processed),
				eq("image/png"));
		verify(storage).delete("old/avatar.png");
	}

	@Test
	void rejectsAvatarWhenStorageIsUnavailable() {
		when(storage.available()).thenReturn(false);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.replaceAvatar(UUID.randomUUID().toString(), new byte[] {1}));

		assertEquals("OBJECT_STORAGE_UNAVAILABLE", exception.code());
		verify(accounts, never()).findById(any());
	}

	@Test
	void rejectsAvatarForMissingUser() {
		UUID userId = UUID.randomUUID();
		when(storage.available()).thenReturn(true);
		when(accounts.findById(userId)).thenReturn(Optional.empty());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.replaceAvatar(userId.toString(), new byte[] {1}));

		assertEquals("USER_NOT_FOUND", exception.code());
	}

	@Test
	void deletesUploadedAvatarWhenConcurrentUpdateWins() {
		UUID userId = UUID.randomUUID();
		when(storage.available()).thenReturn(true);
		when(accounts.findById(userId)).thenReturn(Optional.of(user(userId, null)));
		when(images.process(any()))
				.thenReturn(new ProcessedAvatar(new byte[] {2}, "image/jpeg", "jpg"));
		when(accounts.updateAvatarObjectKey(eq(userId), eq(null), any()))
				.thenReturn(false);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.replaceAvatar(userId.toString(), new byte[] {1}));

		assertEquals("PROFILE_UPDATE_CONFLICT", exception.code());
		verify(storage).delete(org.mockito.ArgumentMatchers.contains(userId.toString()));
	}

	@Test
	void keepsSuccessfulAvatarUpdateWhenSigningAndCleanupFail() {
		UUID userId = UUID.randomUUID();
		when(storage.available()).thenReturn(true);
		when(accounts.findById(userId)).thenReturn(Optional.of(user(userId, "old.png")));
		when(images.process(any()))
				.thenReturn(new ProcessedAvatar(new byte[] {2}, "image/png", "png"));
		when(accounts.updateAvatarObjectKey(eq(userId), eq("old.png"), any()))
				.thenReturn(true);
		when(storage.signGetUrl(any(), any()))
				.thenThrow(new BusinessException("SIGN_FAILED", "签名失败"));
		doThrow(new BusinessException("DELETE_FAILED", "删除失败"))
				.when(storage).delete("old.png");

		AvatarResponse response = service.replaceAvatar(userId.toString(), new byte[] {1});

		assertNull(response.avatarUrl());
		assertNull(response.avatarUrlExpiresAt());
		verify(storage).delete("old.png");
	}

	private UserAccount user(UUID id, String avatarObjectKey) {
		Instant now = Instant.parse("2026-08-03T00:00:00Z");
		return new UserAccount(
				id,
				"learner@example.com",
				"hash",
				"学习者",
				avatarObjectKey,
				UserRole.USER,
				UserStatus.ACTIVE,
				1,
				now,
				now,
				now);
	}
}
