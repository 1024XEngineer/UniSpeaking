package com.unispeaking.service.memory.impl;

import com.unispeaking.domain.po.conversation.ConversationMessage;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.service.conversation.FreeChatConversationService;
import com.unispeaking.service.memory.SessionMemoryService;
import com.unispeaking.provider.ConversationMemoryProvider;
import com.unispeaking.service.profile.ProfileService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SessionMemoryServiceImpl implements SessionMemoryService {

	private final FreeChatConversationService freeChatConversationService;
	private final ProfileService profileService;
	private final ConversationMemoryProvider memoryProvider;

	public SessionMemoryServiceImpl(
			FreeChatConversationService freeChatConversationService,
			ProfileService profileService,
			ConversationMemoryProvider memoryProvider) {
		this.freeChatConversationService = freeChatConversationService;
		this.profileService = profileService;
		this.memoryProvider = memoryProvider;
	}

	@Override
	public synchronized void updateAfterCompletion(AbstractSceneSession session) {
		List<ConversationMessage> messages = freeChatConversationService.getMessages(session.getId());
		String summary = memoryProvider.summarize(messages);
		if (summary.isBlank()) {
			return;
		}
		var profile = profileService.getProfile(session.getUserId());
		String updatedMemory = memoryProvider.merge(profile.memory(), summary);
		profileService.updateMemory(session.getUserId(), updatedMemory);
	}
}
