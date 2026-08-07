package com.unispeaking.domain.dto.scene;

/** Fully prepared free-chat scene handed from Scene to Session. */
public record FreeChatSceneContext(
		String userId,
		FreeChatSceneResult scene) {
}
