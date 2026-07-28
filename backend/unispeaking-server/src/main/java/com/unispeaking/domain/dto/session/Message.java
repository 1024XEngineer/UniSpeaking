package com.unispeaking.domain.dto.session;

public record Message(
		Integer owner,
		String content,
		byte[] audio) {
}
