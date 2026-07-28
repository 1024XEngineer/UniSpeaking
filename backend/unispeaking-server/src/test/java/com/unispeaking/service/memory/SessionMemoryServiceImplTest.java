package com.unispeaking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.po.conversation.ConversationMessage;
import com.unispeaking.domain.vo.conversation.SpeakerType;
import com.unispeaking.domain.po.session.FreeChatSceneSession;
import com.unispeaking.infrastructure.ai.local.LocalConversationMemoryProvider;
import com.unispeaking.infrastructure.persistence.inmemory.InMemoryFreeChatConversationStore;
import com.unispeaking.infrastructure.persistence.inmemory.InMemoryUserProfileRepository;
import com.unispeaking.service.conversation.impl.FreeChatConversationServiceImpl;
import com.unispeaking.service.memory.impl.SessionMemoryServiceImpl;
import com.unispeaking.service.profile.impl.ProfileServiceImpl;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionMemoryServiceImplTest {

	@Test
	void createsMemoryFromFirstSessionAndMergesTheNextSession() {
		var profileService = new ProfileServiceImpl(new InMemoryUserProfileRepository());
		var freeChatConversationService =
				new FreeChatConversationServiceImpl(new InMemoryFreeChatConversationStore());
		var memoryService = new SessionMemoryServiceImpl(
				freeChatConversationService,
				profileService,
				new LocalConversationMemoryProvider());

		assertEquals("", profileService.getProfile("user-1").memory());

		var firstSession = new FreeChatSceneSession("session-1", "user-1");
		freeChatConversationService.appendMessage(message(
				"message-1", "session-1", SpeakerType.USER, "I like coffee and want to practice ordering."));
		freeChatConversationService.appendMessage(message(
				"message-2", "session-1", SpeakerType.ASSISTANT, "Let's practice ordering a latte."));
		memoryService.updateAfterCompletion(firstSession);

		String firstMemory = profileService.getProfile("user-1").memory();
		assertTrue(firstMemory.contains("coffee"));
		assertTrue(firstMemory.contains("latte"));

		var secondSession = new FreeChatSceneSession("session-2", "user-1");
		freeChatConversationService.appendMessage(message(
				"message-3", "session-2", SpeakerType.USER, "I will travel to London next month."));
		memoryService.updateAfterCompletion(secondSession);

		String mergedMemory = profileService.getProfile("user-1").memory();
		assertTrue(mergedMemory.contains("coffee"));
		assertTrue(mergedMemory.contains("London"));
	}

	@Test
	void leavesInitialMemoryEmptyWhenTheSessionHasNoTranscript() {
		var profileService = new ProfileServiceImpl(new InMemoryUserProfileRepository());
		var memoryService = new SessionMemoryServiceImpl(
				new FreeChatConversationServiceImpl(new InMemoryFreeChatConversationStore()),
				profileService,
				new LocalConversationMemoryProvider());

		memoryService.updateAfterCompletion(new FreeChatSceneSession("empty-session", "user-2"));

		assertEquals("", profileService.getProfile("user-2").memory());
	}

	private ConversationMessage message(
			String id, String sessionId, SpeakerType speaker, String text) {
		return new ConversationMessage(id, sessionId, speaker, text, Instant.parse("2026-07-20T00:00:00Z"));
	}
}
