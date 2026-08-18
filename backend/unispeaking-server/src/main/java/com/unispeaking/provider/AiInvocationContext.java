package com.unispeaking.provider;

import java.util.UUID;

/** Business identity carried through one logical AI request and all failover attempts. */
public record AiInvocationContext(
		UUID logicalRequestId,
		String userId,
		String sessionId,
		String businessScene,
		String routeKey) {

	public AiInvocationContext {
		logicalRequestId = logicalRequestId == null ? UUID.randomUUID() : logicalRequestId;
		businessScene = textOrDefault(businessScene, "unspecified");
		routeKey = textOrDefault(routeKey, "default");
		userId = trimToNull(userId);
		sessionId = trimToNull(sessionId);
	}

	public static AiInvocationContext create(String userId, String sessionId, String businessScene) {
		return new AiInvocationContext(UUID.randomUUID(), userId, sessionId, businessScene, "default");
	}

	public static AiInvocationContext anonymous(String businessScene) {
		return create(null, null, businessScene);
	}

	private static String textOrDefault(String value, String fallback) {
		String normalized = trimToNull(value);
		return normalized == null ? fallback : normalized;
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
