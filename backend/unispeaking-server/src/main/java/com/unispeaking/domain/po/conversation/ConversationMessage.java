package com.unispeaking.domain.po.conversation;

import com.unispeaking.domain.vo.conversation.SpeakerType;
import java.time.Instant;

public record ConversationMessage(
		String id,
		String localSessionId,
		SpeakerType speaker,
		String text,
		byte[] audio,
		Instant createdAt) {

	public ConversationMessage {
		audio = audio == null ? null : audio.clone();
	}

	@Override
	public byte[] audio() {
		return audio == null ? null : audio.clone();
	}
}
