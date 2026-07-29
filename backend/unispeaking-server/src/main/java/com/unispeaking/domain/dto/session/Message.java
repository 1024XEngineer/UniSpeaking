package com.unispeaking.domain.dto.session;

public record Message(
		Integer owner,
		String content,
		byte[] audio) {

	public Message {
		audio = audio == null ? null : audio.clone();
	}

	@Override
	public byte[] audio() {
		return audio == null ? null : audio.clone();
	}
}
