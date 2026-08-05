package com.unispeaking.domain.vo.scene;

import java.util.Objects;

public record InterviewQuestionPrompt(
		int questionNo,
		InterviewQuestionType questionType,
		String questionText) {

	public InterviewQuestionPrompt {
		if (questionNo < 1) {
			throw new IllegalArgumentException("questionNo must be positive");
		}
		questionType = Objects.requireNonNull(questionType, "questionType");
		if (questionText == null || questionText.isBlank()) {
			throw new IllegalArgumentException("questionText must not be blank");
		}
		questionText = questionText.trim();
	}
}
