package com.unispeaking.service.conversation;

import com.unispeaking.domain.po.conversation.ConversationMessage;
import java.util.List;

public interface FreeChatConversationService {
	void appendMessage(ConversationMessage message);
	List<ConversationMessage> getMessages(String localSessionId);
	void clearConversation(String localSessionId);
}
