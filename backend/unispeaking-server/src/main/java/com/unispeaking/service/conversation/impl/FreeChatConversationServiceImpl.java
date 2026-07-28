package com.unispeaking.service.conversation.impl;

import com.unispeaking.domain.po.conversation.ConversationMessage;
import com.unispeaking.repository.FreeChatConversationStore;
import com.unispeaking.service.conversation.FreeChatConversationService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FreeChatConversationServiceImpl implements FreeChatConversationService {

	private final FreeChatConversationStore store;

	public FreeChatConversationServiceImpl(FreeChatConversationStore store) {
		this.store = store;
	}

	@Override
	public void appendMessage(ConversationMessage message) {
		store.append(message);
	}

	@Override
	public List<ConversationMessage> getMessages(String localSessionId) {
		return store.findByLocalSessionId(localSessionId);
	}

	@Override
	public void clearConversation(String localSessionId) {
		store.clear(localSessionId);
	}
}
