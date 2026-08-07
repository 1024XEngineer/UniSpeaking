package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.IeltsMode;

public record IeltsSceneResult(
		String sceneId,
		IeltsMode mode,
		Integer targetPart,
		IeltsPartContent part1,
		IeltsPartContent part2,
		IeltsPartContent part3) {
}
