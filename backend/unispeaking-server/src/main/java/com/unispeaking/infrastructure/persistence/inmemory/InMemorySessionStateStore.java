package com.unispeaking.infrastructure.persistence.inmemory;

import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.repository.SessionStateStore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemorySessionStateStore implements SessionStateStore {

	private final Map<String, AbstractSceneSession> sessions = new ConcurrentHashMap<>();

	@Override
	public void save(AbstractSceneSession session) { sessions.put(session.getId(), session); }

	@Override
	public Optional<AbstractSceneSession> findById(String localSessionId) {
		return Optional.ofNullable(sessions.get(localSessionId));
	}

	@Override
	public void remove(String localSessionId) { sessions.remove(localSessionId); }
}
