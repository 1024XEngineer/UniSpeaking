package com.unispeaking.domain.po.scene;

import com.unispeaking.domain.vo.scene.InterviewQuestionType;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * An AI question that was actually asked during an Interview.
 */
public record InterviewQuestionRecord(
		String interviewId,
		int questionNo,
		InterviewQuestionType questionType,
		String questionText,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	public InterviewQuestionRecord {
		if (interviewId == null || interviewId.isBlank()) {
			throw new IllegalArgumentException("interviewId must not be blank");
		}
		if (questionNo < 1) {
			throw new IllegalArgumentException("questionNo must be positive");
		}
		Objects.requireNonNull(questionType, "questionType must not be null");
		if (questionText == null || questionText.isBlank()) {
			throw new IllegalArgumentException("questionText must not be blank");
		}
		Objects.requireNonNull(createdAt, "createdAt must not be null");
		Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}
}
