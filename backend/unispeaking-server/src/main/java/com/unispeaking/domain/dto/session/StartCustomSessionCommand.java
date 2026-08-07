package com.unispeaking.domain.dto.session;

/** 启动自定义场景会话所需的场景标识和实时对话参数。 */
public record StartCustomSessionCommand(
		String sceneId,
		StartCustomSceneDialogueRequest request) {
}
