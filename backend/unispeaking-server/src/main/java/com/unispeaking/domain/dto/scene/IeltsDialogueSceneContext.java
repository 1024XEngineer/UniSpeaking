package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsPart;

/** Fully prepared IELTS dialogue scene handed from Scene to Session. */
public record IeltsDialogueSceneContext(
		String userId,
		String ieltsId,
		IeltsContent content,
		IeltsPart activePart,
		String topicTitle,
		SceneFlowResponse flow,
		String prompt,
		String voiceId) {
}
