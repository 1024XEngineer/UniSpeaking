package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.InterviewQuestionType;

public record InterviewAiQuestionResponse(
		int questionNo,
		InterviewQuestionType questionType,
		String text,
		InterviewAudioResponse audio) {
}
