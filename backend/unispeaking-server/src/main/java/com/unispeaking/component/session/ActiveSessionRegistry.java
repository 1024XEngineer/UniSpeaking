package com.unispeaking.component.session;

import com.unispeaking.domain.po.session.AbstractSceneSession;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Holds only active realtime sessions. Completed sessions are removed; durable
 * custom-scene dialogue data is persisted through MyBatis-Plus repositories.
 */
@Component
public class ActiveSessionRegistry {

	private final Map<String, AbstractSceneSession> sessions =
			new ConcurrentHashMap<>();

	public void save(AbstractSceneSession session) {
		sessions.put(session.getId(), session);
	}

	public Optional<AbstractSceneSession> findById(String sessionId) {
		return Optional.ofNullable(sessions.get(sessionId));
	}

	public void remove(String sessionId) {
		sessions.remove(sessionId);
	}
}
