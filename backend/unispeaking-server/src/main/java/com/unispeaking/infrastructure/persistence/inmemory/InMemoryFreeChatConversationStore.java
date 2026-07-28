package com.unispeaking.infrastructure.persistence.inmemory;

import com.unispeaking.domain.po.conversation.ConversationMessage;
import com.unispeaking.repository.FreeChatConversationStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryFreeChatConversationStore implements FreeChatConversationStore {

	private final Map<String, List<ConversationMessage>> conversations = new ConcurrentHashMap<>();

	@Override
	public void append(ConversationMessage message) {
		conversations.computeIfAbsent(message.localSessionId(), ignored -> new CopyOnWriteArrayList<>()).add(message);
	}

	@Override
	public List<ConversationMessage> findByLocalSessionId(String localSessionId) {
		return List.copyOf(conversations.getOrDefault(localSessionId, List.of()));
	}

	@Override
	public void clear(String localSessionId) { conversations.remove(localSessionId); }
}
