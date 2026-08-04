package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.feedback.FeedbackListResponse;
import com.unispeaking.domain.dto.feedback.FeedbackResponse;
import com.unispeaking.domain.dto.feedback.UpdateFeedbackRequest;
import com.unispeaking.domain.vo.feedback.FeedbackStatus;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.feedback.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/feedbacks")
public class AdminFeedbackController {

	private final AuthService authService;
	private final FeedbackService feedbackService;

	public AdminFeedbackController(
			AuthService authService,
			FeedbackService feedbackService) {
		this.authService = authService;
		this.feedbackService = feedbackService;
	}

	@GetMapping
	public ApiResponse<FeedbackListResponse> findAll(
			@RequestParam(required = false) FeedbackStatus status) {
		authService.requireAdminUserId();
		return ApiResponse.success(feedbackService.findAll(status));
	}

	@PatchMapping("/{feedbackNo}")
	public ApiResponse<FeedbackResponse> update(
			@PathVariable String feedbackNo,
			@Valid @RequestBody UpdateFeedbackRequest request) {
		authService.requireAdminUserId();
		return ApiResponse.success(feedbackService.update(feedbackNo, request));
	}
}
