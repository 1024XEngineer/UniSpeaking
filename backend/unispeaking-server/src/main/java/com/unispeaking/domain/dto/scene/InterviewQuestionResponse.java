package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.InterviewQuestionType;

public record InterviewQuestionResponse(
		int questionNo,
		InterviewQuestionType questionType,
		String text) {
}
