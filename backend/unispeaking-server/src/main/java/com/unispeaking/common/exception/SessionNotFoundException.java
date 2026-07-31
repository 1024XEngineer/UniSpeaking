package com.unispeaking.common.exception;

public class SessionNotFoundException extends BusinessException {

	public SessionNotFoundException(String sessionId) {
		super("SESSION_NOT_FOUND", "Session not found: " + sessionId);
	}
}
