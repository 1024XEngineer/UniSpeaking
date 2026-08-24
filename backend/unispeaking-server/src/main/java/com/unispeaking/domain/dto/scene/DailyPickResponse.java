package com.unispeaking.domain.dto.scene;

public record DailyPickResponse(
		String id,
		int position,
		String title,
		String category,
		String duration,
		String level,
		String goal,
		String sceneInput) {
}
