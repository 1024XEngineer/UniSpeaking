package com.unispeaking.domain.dto.account;

import com.unispeaking.domain.po.user.UserAccount;
import java.util.UUID;

public record AccountProfileResponse(
		UUID id,
		String username,
		String nickname,
		String avatarUrl,
		String status) {

	public static AccountProfileResponse from(UserAccount user, String avatarUrl) {
		return new AccountProfileResponse(
				user.id(),
				user.username(),
				user.nickname(),
				avatarUrl,
				user.status().name());
	}
}
