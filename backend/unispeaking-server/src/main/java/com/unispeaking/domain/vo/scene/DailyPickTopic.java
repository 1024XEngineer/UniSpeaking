package com.unispeaking.domain.vo.scene;

import java.util.Objects;

public record DailyPickTopic(
		String id,
		String title,
		String category,
		String duration,
		String level,
		String goal,
		String sceneInput) {

	public DailyPickTopic {
		id = requireText(id, "id");
		title = requireText(title, "title");
		category = requireText(category, "category");
		duration = requireText(duration, "duration");
		level = requireText(level, "level");
		goal = requireText(goal, "goal");
		sceneInput = requireText(sceneInput, "sceneInput");
	}

	private static String requireText(String value, String field) {
		String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return normalized;
	}
}
