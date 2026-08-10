package com.unispeaking.domain.dto.session;

/** 结束自定义场景会话并生成评价所需的参数。 */
public record EndCustomSessionCommand(
		String sceneId,
		String sessionId,
		String stopTime) {
}
