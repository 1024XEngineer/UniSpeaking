package com.unispeaking.domain.dto.evaluation;

import com.unispeaking.domain.dto.session.Message;
import java.util.List;
import java.util.Objects;

/**
 * 已保存会话内容及其单轮评分。
 */
public record DialogueEvaluationResult(
		List<Message> dialogue,
		List<DialogueTurnEvaluationResult> turnEvaluation) {

	public DialogueEvaluationResult {
		dialogue = List.copyOf(Objects.requireNonNull(
				dialogue,
				"dialogue must not be null"));
		turnEvaluation = List.copyOf(Objects.requireNonNull(
				turnEvaluation,
				"turnEvaluation must not be null"));
	}
}
