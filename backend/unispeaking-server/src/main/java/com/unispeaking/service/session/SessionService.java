package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartSessionResponse;

public interface SessionService {

	StartSessionResponse startSession(String prompt);

	void addMessage(Message message);

	void endSession(String sessionId, String stopTime);
}
