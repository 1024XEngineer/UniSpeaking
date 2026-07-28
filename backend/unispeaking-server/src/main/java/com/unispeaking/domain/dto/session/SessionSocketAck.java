package com.unispeaking.domain.dto.session;

public record SessionSocketAck(
		String type,
		String sessionId,
		boolean success,
		String code,
		String message,
		Object data) {

	public static SessionSocketAck success(String type, String sessionId, Object data) {
		return new SessionSocketAck(type, sessionId, true, "OK", "success", data);
	}

	public static SessionSocketAck failure(String type, String sessionId, String code, String message) {
		return new SessionSocketAck(type, sessionId, false, code, message, null);
	}
}
