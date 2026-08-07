package com.unispeaking.domain.dto.scene;

/**
 * Input used to create a free-chat scene before a realtime session starts.
 */
public record FreeChatSceneRequest(String prompt) {
}
