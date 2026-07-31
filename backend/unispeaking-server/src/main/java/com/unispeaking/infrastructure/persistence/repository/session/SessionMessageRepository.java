package com.unispeaking.infrastructure.persistence.repository.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.persistence.entity.session.SessionMessageEntity;
import com.unispeaking.infrastructure.persistence.mapper.session.SessionMessageMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SessionMessageRepository {

	private final SessionMessageMapper mapper;

	public SessionMessageRepository(SessionMessageMapper mapper) {
		this.mapper = mapper;
	}

	public void append(
			String sceneId,
			String sessionId,
			int messageNo,
			Message message) {
		SessionMessageEntity entity = new SessionMessageEntity();
		entity.setSceneId(sceneId);
		entity.setSessionId(sessionId);
		entity.setMessageNo(messageNo);
		entity.setOwner(message.owner());
		entity.setContent(message.content().trim());
		entity.setAudioUrl(null);
		entity.setCreatedAt(OffsetDateTime.now());
		entity.setUpdateAt(entity.getCreatedAt());
		try {
			if (mapper.insert(entity) != 1) {
				throw persistenceFailure();
			}
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<Message> findMessages(String sessionId) {
		try {
			return mapper.selectList(new LambdaQueryWrapper<SessionMessageEntity>()
							.eq(SessionMessageEntity::getSessionId, sessionId)
							.orderByAsc(SessionMessageEntity::getMessageNo))
					.stream()
					.map(entity -> new Message(
							entity.getOwner(),
							entity.getContent(),
							null))
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<String> findSceneId(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			return Optional.empty();
		}
		try {
			return mapper.selectList(
							new LambdaQueryWrapper<SessionMessageEntity>()
									.eq(
											SessionMessageEntity::getSessionId,
											sessionId)
									.orderByAsc(
											SessionMessageEntity::getMessageNo))
					.stream()
					.findFirst()
					.map(SessionMessageEntity::getSceneId);
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public int deleteObsoleteForScene(
			String sceneId,
			String retainedSessionId) {
		try {
			return mapper.delete(
					new LambdaQueryWrapper<SessionMessageEntity>()
							.eq(SessionMessageEntity::getSceneId, sceneId)
							.ne(
									SessionMessageEntity::getSessionId,
									retainedSessionId));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private BusinessException persistenceFailure() {
		return new BusinessException(
				"SESSION_MESSAGE_PERSISTENCE_FAILED",
				"会话消息保存失败");
	}
}
