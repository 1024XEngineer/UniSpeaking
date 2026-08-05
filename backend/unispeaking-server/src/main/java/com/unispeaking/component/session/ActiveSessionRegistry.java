package com.unispeaking.component.session;

import com.unispeaking.domain.po.session.AbstractSceneSession;
import java.util.List;
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

	public boolean registerIfAbsent(AbstractSceneSession session) {
		return sessions.putIfAbsent(session.getId(), session) == null;
	}

	public Optional<AbstractSceneSession> findById(String sessionId) {
		return Optional.ofNullable(sessions.get(sessionId));
	}

	public <T extends AbstractSceneSession> Optional<T> findById(
			String sessionId,
			Class<T> sessionType) {
		return findById(sessionId).filter(sessionType::isInstance).map(sessionType::cast);
	}

	public List<AbstractSceneSession> snapshot() {
		return List.copyOf(sessions.values());
	}

	public void remove(String sessionId) {
		sessions.remove(sessionId);
	}

	public boolean remove(
			String sessionId,
			AbstractSceneSession session) {
		return sessions.remove(sessionId, session);
	}
}
