package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.feedback.CreateFeedbackRequest;
import com.unispeaking.domain.dto.feedback.CreateFeedbackResponse;
import com.unispeaking.domain.dto.feedback.FeedbackListResponse;
import com.unispeaking.domain.dto.feedback.FeedbackResponse;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.feedback.FeedbackService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedbacks")
public class FeedbackController {

	private final AuthService authService;
	private final FeedbackService feedbackService;

	public FeedbackController(
			AuthService authService,
			FeedbackService feedbackService) {
		this.authService = authService;
		this.feedbackService = feedbackService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<CreateFeedbackResponse>> create(
			@Valid @RequestBody CreateFeedbackRequest request) {
		String userId = authService.currentUserIdOrNull();
		CreateFeedbackResponse response = feedbackService.create(
				userId == null ? null : UUID.fromString(userId),
				request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(response));
	}

	@GetMapping("/lookup/{feedbackNo}")
	public ApiResponse<FeedbackResponse> lookup(
			@PathVariable String feedbackNo,
			@RequestHeader("X-Feedback-Lookup-Code") String lookupCode) {
		return ApiResponse.success(feedbackService.lookup(feedbackNo, lookupCode));
	}

	@GetMapping("/mine")
	public ApiResponse<FeedbackListResponse> mine() {
		UUID userId = UUID.fromString(authService.requireUserId(null));
		return ApiResponse.success(feedbackService.findMine(userId));
	}
}
