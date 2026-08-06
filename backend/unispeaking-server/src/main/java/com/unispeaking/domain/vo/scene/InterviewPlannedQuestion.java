package com.unispeaking.domain.vo.scene;

import java.util.Objects;

public record InterviewPlannedQuestion(
		int questionNo,
		String questionText,
		int maxFollowUps) {

	public InterviewPlannedQuestion {
		if (questionNo < 1 || questionNo > 5) {
			throw new IllegalArgumentException("questionNo must be between 1 and 5");
		}
		if (questionText == null || questionText.isBlank()) {
			throw new IllegalArgumentException("questionText must not be blank");
		}
		if (maxFollowUps < 0 || maxFollowUps > 2) {
			throw new IllegalArgumentException("maxFollowUps must be between 0 and 2");
		}
		questionText = questionText.trim();
	}
}
