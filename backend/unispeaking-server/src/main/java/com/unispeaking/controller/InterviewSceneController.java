package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.scene.InterviewSceneRequest;
import com.unispeaking.domain.dto.scene.InterviewSceneResult;
import com.unispeaking.service.scene.InterviewSceneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Interview 场景端点：本刀只暴露 POST /api/interview-scenes（generate）。 */
@RestController
@RequestMapping("/api/interview-scenes")
public class InterviewSceneController {

	private final InterviewSceneService interviewSceneService;

	public InterviewSceneController(InterviewSceneService interviewSceneService) {
		this.interviewSceneService = interviewSceneService;
	}

	@PostMapping
	public ApiResponse<InterviewSceneResult> generate(
			@Valid @RequestBody InterviewSceneRequest request) {
		return ApiResponse.success(interviewSceneService.generate(request));
	}
}
