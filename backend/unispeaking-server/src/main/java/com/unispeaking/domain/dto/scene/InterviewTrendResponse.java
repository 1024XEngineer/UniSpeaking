package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import java.util.List;
import java.util.Objects;

public record InterviewTrendResponse(
		InterviewDifficulty difficulty,
		List<InterviewTrendPointResponse> points) {

	public InterviewTrendResponse {
		Objects.requireNonNull(difficulty, "difficulty");
		points = points == null ? List.of() : List.copyOf(points);
	}
}
