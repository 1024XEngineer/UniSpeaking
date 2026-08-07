package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;

public record IeltsSceneRequest(
		IeltsMode mode,
		Integer targetPart,
		String topicId) {

	public IeltsGenerationRequest toGenerationRequest() {
		IeltsPart part = targetPart == null ? null : switch (targetPart) {
			case 1 -> IeltsPart.PART_1;
			case 2 -> IeltsPart.PART_2;
			case 3 -> IeltsPart.PART_3;
			default -> throw new IllegalArgumentException("targetPart must be 1, 2 or 3");
		};
		return new IeltsGenerationRequest(mode, part, topicId);
	}
}
