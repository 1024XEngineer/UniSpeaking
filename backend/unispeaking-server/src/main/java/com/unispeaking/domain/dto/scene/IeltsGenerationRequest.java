package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import jakarta.validation.constraints.NotNull;

public record IeltsGenerationRequest(
		@NotNull IeltsMode mode,
		IeltsPart part,
		String topicId) {
}
