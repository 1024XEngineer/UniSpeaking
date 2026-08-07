package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.SessionDetail;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import java.util.List;

/**
 * Scene-neutral session lifecycle contract.
 */
public interface SessionService {

	StartSessionResponse startSession(StartSessionCommand command);

	void addMessage(String sessionId, Message message);

	void endSession(String sessionId);

	SessionDetail getSession(String sessionId);

	List<SessionDetail> getBySceneId(String sceneId);
}
