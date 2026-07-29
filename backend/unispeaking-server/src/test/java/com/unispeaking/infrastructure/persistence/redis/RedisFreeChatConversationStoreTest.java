package com.unispeaking.infrastructure.persistence.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.conversation.ConversationMessage;
import com.unispeaking.domain.vo.conversation.SpeakerType;
import com.unispeaking.infrastructure.config.FreeChatRedisProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RedisFreeChatConversationStoreTest {

	@Mock
	private StringRedisTemplate redis;

	@Mock
	private ListOperations<String, String> listOperations;

	@Test
	void appendsTextInOrderWithTtlAndReadsItBack() {
		when(redis.opsForList()).thenReturn(listOperations);
		var properties = new FreeChatRedisProperties(
				"unispeaking:free-chat:session",
				Duration.ofHours(24));
		var store = new RedisFreeChatConversationStore(
				redis,
				new ObjectMapper(),
				properties);
		var userMessage = new ConversationMessage(
				"msg-user",
				"session-1",
				SpeakerType.USER,
				"Hello.",
				new byte[] {1, 2, 3},
				Instant.parse("2026-07-28T09:00:00Z"));
		var assistantMessage = new ConversationMessage(
				"msg-assistant",
				"session-1",
				SpeakerType.ASSISTANT,
				"Hello from the coach.",
				null,
				Instant.parse("2026-07-28T09:00:01Z"));
		ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);

		store.append(userMessage);
		store.append(assistantMessage);

		String key = "unispeaking:free-chat:session:session-1:messages";
		verify(listOperations, times(2)).rightPush(eq(key), payload.capture());
		verify(redis, times(2)).expire(key, Duration.ofHours(24));
		assertTrue(payload.getAllValues().get(0).contains("\"owner\":1"));
		assertTrue(payload.getAllValues().get(0).contains("\"content\":\"Hello.\""));
		assertTrue(payload.getAllValues().get(1).contains("\"owner\":0"));
		assertTrue(payload.getAllValues().get(1).contains("\"content\":\"Hello from the coach.\""));
		assertFalse(payload.getAllValues().get(0).contains("audio"));
		assertFalse(payload.getAllValues().get(1).contains("audio"));

		when(listOperations.range(key, 0, -1)).thenReturn(payload.getAllValues());
		List<ConversationMessage> messages = store.findByLocalSessionId("session-1");

		assertEquals(2, messages.size());
		assertEquals(SpeakerType.USER, messages.get(0).speaker());
		assertEquals("Hello.", messages.get(0).text());
		assertEquals(SpeakerType.ASSISTANT, messages.get(1).speaker());
		assertEquals("Hello from the coach.", messages.get(1).text());
		assertNull(messages.getFirst().audio());
	}

	@Test
	void clearsOnlyTheRequestedSessionKey() {
		var store = new RedisFreeChatConversationStore(
				redis,
				new ObjectMapper(),
				new FreeChatRedisProperties(null, null));

		store.clear("session-2");

		verify(redis).delete("unispeaking:free-chat:session:session-2:messages");
	}
}
