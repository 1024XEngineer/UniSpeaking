package com.unispeaking.service.feedback;

import com.unispeaking.domain.dto.feedback.CreateFeedbackRequest;
import com.unispeaking.domain.dto.feedback.CreateFeedbackResponse;
import com.unispeaking.domain.dto.feedback.FeedbackListResponse;
import com.unispeaking.domain.dto.feedback.FeedbackResponse;
import com.unispeaking.domain.dto.feedback.UpdateFeedbackRequest;
import com.unispeaking.domain.vo.feedback.FeedbackStatus;
import java.util.UUID;

public interface FeedbackService {

	CreateFeedbackResponse create(UUID userId, CreateFeedbackRequest request);

	FeedbackResponse lookup(String feedbackNo, String lookupCode);

	FeedbackListResponse findMine(UUID userId);

	FeedbackListResponse findAll(FeedbackStatus status);

	FeedbackResponse update(String feedbackNo, UpdateFeedbackRequest request);
}
