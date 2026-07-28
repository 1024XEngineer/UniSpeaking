package com.unispeaking.domain.po.conversation;

import com.unispeaking.domain.vo.conversation.SpeakerType;
import java.time.Instant;

public record ConversationMessage(
		String id,
		String localSessionId,
		SpeakerType speaker,
		String text,
		Instant createdAt) {
}
