package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInterviewRequest(
		@NotBlank @Size(max = 255) String jobTitle,
		@NotNull InterviewDifficulty difficulty,
		@Size(max = 5000) String jobDescription,
		@Size(max = 20000) String resumeText) implements InterviewApiRequest {
}
