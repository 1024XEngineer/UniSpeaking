package com.unispeaking.infrastructure.persistence.redis;

import com.unispeaking.domain.po.conversation.ConversationMessage;
import com.unispeaking.domain.vo.conversation.SpeakerType;
import com.unispeaking.infrastructure.config.FreeChatRedisProperties;
import com.unispeaking.repository.FreeChatConversationStore;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
@ConditionalOnProperty(
		prefix = "conversation.redis",
		name = "enabled",
		havingValue = "true",
		matchIfMissing = true)
public class RedisFreeChatConversationStore implements FreeChatConversationStore {

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final FreeChatRedisProperties properties;

	public RedisFreeChatConversationStore(
			StringRedisTemplate redis,
			ObjectMapper objectMapper,
			FreeChatRedisProperties properties) {
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	@Override
	public void append(ConversationMessage message) {
		if (message == null) {
			throw new IllegalArgumentException("conversation message is required");
		}
		String key = key(message.localSessionId());
		RedisMessage payload = new RedisMessage(
				message.id(),
				message.speaker() == SpeakerType.ASSISTANT ? 0 : 1,
				message.text(),
				message.createdAt().toString());
		redis.opsForList().rightPush(key, write(payload));
		redis.expire(key, properties.ttl());
	}

	@Override
	public List<ConversationMessage> findByLocalSessionId(String localSessionId) {
		List<String> payloads = redis.opsForList().range(key(localSessionId), 0, -1);
		if (payloads == null || payloads.isEmpty()) {
			return List.of();
		}
		return payloads.stream()
				.map(this::read)
				.map(payload -> new ConversationMessage(
						payload.messageId(),
						localSessionId,
						payload.owner() == 0 ? SpeakerType.ASSISTANT : SpeakerType.USER,
						payload.content(),
						null,
						Instant.parse(payload.createdAt())))
				.toList();
	}

	@Override
	public void clear(String localSessionId) {
		redis.delete(key(localSessionId));
	}

	private String key(String localSessionId) {
		if (localSessionId == null || localSessionId.isBlank()) {
			throw new IllegalArgumentException("localSessionId is required");
		}
		return properties.keyPrefix() + ":" + localSessionId.trim() + ":messages";
	}

	private String write(RedisMessage message) {
		try {
			return objectMapper.writeValueAsString(message);
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("cannot serialize free-chat message", exception);
		}
	}

	private RedisMessage read(String payload) {
		try {
			return objectMapper.readValue(payload, RedisMessage.class);
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("cannot deserialize free-chat message", exception);
		}
	}

	private record RedisMessage(
			String messageId,
			Integer owner,
			String content,
			String createdAt) {
	}
}
