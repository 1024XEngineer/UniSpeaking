package com.unispeaking.domain.dto.scene;

public record DeleteInterviewResponse(
		String interviewId,
		boolean deleted) {
}
