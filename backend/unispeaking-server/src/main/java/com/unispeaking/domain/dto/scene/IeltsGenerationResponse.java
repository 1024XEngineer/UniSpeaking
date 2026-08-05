package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;

public record IeltsGenerationResponse(
		String ieltsId,
		IeltsMode mode,
		IeltsPart selectedPart,
		String selectedTopicId,
		String title,
		IeltsContent content,
		String voiceId,
		String scenePrompt) {
}
