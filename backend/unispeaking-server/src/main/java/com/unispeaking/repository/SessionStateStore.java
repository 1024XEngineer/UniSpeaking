package com.unispeaking.repository;

import com.unispeaking.domain.po.session.AbstractSceneSession;
import java.util.Optional;

public interface SessionStateStore {
	void save(AbstractSceneSession session);
	Optional<AbstractSceneSession> findById(String localSessionId);
	void remove(String localSessionId);
}
