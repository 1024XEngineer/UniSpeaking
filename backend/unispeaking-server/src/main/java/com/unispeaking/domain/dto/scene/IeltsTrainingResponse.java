package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.IeltsPart;
import java.util.List;

public record IeltsTrainingResponse(
		String topicId,
		String title,
		IeltsPart part,
		List<IeltsQuestionResponse> questions) {
}
