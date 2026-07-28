package com.unispeaking.infrastructure.ai.local;

import com.unispeaking.domain.po.conversation.ConversationMessage;
import com.unispeaking.domain.vo.conversation.SpeakerType;
import com.unispeaking.provider.ConversationMemoryProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LocalConversationMemoryProvider implements ConversationMemoryProvider {

	private static final int MAX_MESSAGES_PER_SUMMARY = 12;
	private static final int MAX_TEXT_PER_MESSAGE = 240;
	private static final int MAX_SUMMARY_LENGTH = 1_600;
	private static final int MAX_MEMORY_LENGTH = 4_000;

	@Override
	public String summarize(List<ConversationMessage> messages) {
		if (messages == null || messages.isEmpty()) {
			return "";
		}

		int start = Math.max(0, messages.size() - MAX_MESSAGES_PER_SUMMARY);
		StringBuilder summary = new StringBuilder("本次会话摘要（本地）：");
		for (ConversationMessage message : messages.subList(start, messages.size())) {
			String text = normalize(message.text());
			if (text.isBlank()) {
				continue;
			}
			summary.append("\n")
					.append(roleName(message.speaker()))
					.append("：")
					.append(limit(text, MAX_TEXT_PER_MESSAGE));
		}
		return summary.length() == "本次会话摘要（本地）：".length()
				? ""
				: limit(summary.toString(), MAX_SUMMARY_LENGTH);
	}

	@Override
	public String merge(String existingMemory, String sessionSummary) {
		String history = existingMemory == null ? "" : existingMemory.trim();
		String summary = sessionSummary == null ? "" : sessionSummary.trim();
		if (summary.isBlank()) {
			return history;
		}
		String merged = history.isBlank() ? summary : history + "\n\n" + summary;
		if (merged.length() <= MAX_MEMORY_LENGTH) {
			return merged;
		}
		return "…" + merged.substring(merged.length() - MAX_MEMORY_LENGTH + 1);
	}

	private String roleName(SpeakerType speaker) {
		return switch (speaker) {
			case USER -> "用户";
			case ASSISTANT -> "助手";
			case SYSTEM -> "系统";
		};
	}

	private String normalize(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").trim();
	}

	private String limit(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
	}
}
