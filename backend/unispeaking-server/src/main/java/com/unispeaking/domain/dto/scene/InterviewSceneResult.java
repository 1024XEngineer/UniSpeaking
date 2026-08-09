package com.unispeaking.domain.dto.scene;

/** 面试场景生成结果：后续会话流程所需的场景标识与实时面试官 systemPrompt。 */
public record InterviewSceneResult(
		String sceneId,
		String scenePrompt) {
}
