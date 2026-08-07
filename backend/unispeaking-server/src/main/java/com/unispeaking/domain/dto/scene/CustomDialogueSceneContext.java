package com.unispeaking.domain.dto.scene;

/** Fully prepared custom dialogue scene handed from Scene to Session. */
public record CustomDialogueSceneContext(
		String userId,
		String sceneId,
		String title,
		String learningGoal,
		String successFactorJson,
		SceneGenerationResponse scene,
		String prompt) {
}
