package com.unispeaking.domain.dto.scene;

import java.util.List;

public record InterviewHistoryResponse(List<InterviewHistoryItemResponse> interviews) {

	public InterviewHistoryResponse {
		interviews = interviews == null ? List.of() : List.copyOf(interviews);
	}
}
