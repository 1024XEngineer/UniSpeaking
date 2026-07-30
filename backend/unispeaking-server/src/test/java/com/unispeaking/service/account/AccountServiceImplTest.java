package com.unispeaking.service.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.user.UserAccount;
import com.unispeaking.domain.po.user.UserRole;
import com.unispeaking.domain.po.user.UserStatus;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.repository.UserAccountRepository;
import com.unispeaking.service.account.impl.AccountServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

	private static final UUID USER_ID =
			UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final byte[] PNG_BYTES = new byte[] {
		(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
	};
	private static final Instant NOW = Instant.parse("2026-07-30T04:00:00Z");

	@Mock
	private UserAccountRepository repository;
	@Mock
	private AvatarStorage avatarStorage;
	@Mock
	private AvatarUrlResolver avatarUrlResolver;
	@Mock
	private PasswordEncoder passwordEncoder;

	@Test
	void trimsNicknameBeforeUpdatingAccount() {
		UserAccount current = user("Old name", null);
		UserAccount updated = user("Yufan", null);
		when(repository.findById(USER_ID)).thenReturn(Optional.of(current));
		when(repository.updateProfile(USER_ID, "Yufan", null)).thenReturn(updated);

		var response = service().updateNickname(USER_ID, "  Yufan  ");

		assertEquals("Yufan", response.nickname());
	}

	@Test
	void uploadsBeforeSavingAndDeletesOldObjectAfterSave() {
		String oldKey = "avatars/" + USER_ID + "/old.png";
		UserAccount current = user("Yufan", oldKey);
		when(repository.findById(USER_ID)).thenReturn(Optional.of(current));
		when(repository.updateProfile(eq(USER_ID), eq("Yufan"), any(String.class)))
				.thenAnswer(invocation -> user("Yufan", invocation.getArgument(2)));
		when(avatarUrlResolver.resolve(any(String.class)))
				.thenAnswer(invocation -> "https://avatar.example/"
						+ invocation.getArgument(0));

		var response = service().uploadAvatar(
				USER_ID,
				"avatar.png",
				"image/png",
				PNG_BYTES);

		var ordered = inOrder(repository, avatarStorage);
		ordered.verify(repository).findById(USER_ID);
		ordered.verify(avatarStorage).put(
				org.mockito.ArgumentMatchers.matches(
						"avatars/" + USER_ID + "/[0-9a-f-]+\\.png"),
				eq("image/png"),
				eq(PNG_BYTES));
		ordered.verify(repository).updateProfile(
				eq(USER_ID),
				eq("Yufan"),
				org.mockito.ArgumentMatchers.matches(
						"avatars/" + USER_ID + "/[0-9a-f-]+\\.png"));
		ordered.verify(avatarStorage).delete(oldKey);
		assertEquals(true, response.avatarUrl().startsWith("https://avatar.example/"));
	}

	@Test
	void rejectsMimeTypeThatDoesNotMatchFileSignature() {
		when(repository.findById(USER_ID)).thenReturn(Optional.of(user("Yufan", null)));
		byte[] jpegBytes = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service().uploadAvatar(
						USER_ID,
						"avatar.png",
						"image/png",
						jpegBytes));

		assertEquals("INVALID_AVATAR_FILE", exception.code());
	}

	@Test
	void rejectsIncorrectCurrentPassword() {
		UserAccount current = user("Yufan", null, 4);
		when(repository.findById(USER_ID)).thenReturn(Optional.of(current));
		when(passwordEncoder.matches("incorrect", current.passwordHash())).thenReturn(false);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service().changePassword(USER_ID, "incorrect", "new-secret"));

		assertEquals("CURRENT_PASSWORD_INVALID", exception.code());
		verify(repository, never()).updatePasswordAndAuthVersion(any(), any(), any(Long.class));
	}

	@Test
	void incrementsAuthVersionWhenPasswordChanges() {
		UserAccount current = user("Yufan", null, 4);
		when(repository.findById(USER_ID)).thenReturn(Optional.of(current));
		when(passwordEncoder.matches("old-secret", current.passwordHash())).thenReturn(true);
		when(passwordEncoder.matches("new-secret", current.passwordHash())).thenReturn(false);
		when(passwordEncoder.encode("new-secret")).thenReturn("new-bcrypt-hash");

		service().changePassword(USER_ID, "old-secret", "new-secret");

		verify(repository).updatePasswordAndAuthVersion(
				USER_ID,
				"new-bcrypt-hash",
				5);
	}

	@Test
	void schedulesDeletionThirtyDaysLaterAndRevokesExistingTokens() {
		UserAccount current = user("Yufan", null, 7);
		when(repository.findById(USER_ID)).thenReturn(Optional.of(current));
		when(passwordEncoder.matches("current-secret", current.passwordHash())).thenReturn(true);

		service().requestDeletion(USER_ID, "current-secret");

		verify(repository).requestDeletion(
				USER_ID,
				8,
				NOW,
				NOW.plus(30, ChronoUnit.DAYS));
	}

	private AccountServiceImpl service() {
		return new AccountServiceImpl(
				repository,
				avatarStorage,
				avatarUrlResolver,
				passwordEncoder,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private UserAccount user(String nickname, String avatarObjectKey) {
		return user(nickname, avatarObjectKey, 0);
	}

	private UserAccount user(String nickname, String avatarObjectKey, long authVersion) {
		return new UserAccount(
				USER_ID,
				"learner@example.com",
				"bcrypt-hash",
				nickname,
				avatarObjectKey,
				UserRole.USER,
				UserStatus.ACTIVE,
				authVersion,
				null,
				null,
				null,
				NOW,
				NOW);
	}
}
