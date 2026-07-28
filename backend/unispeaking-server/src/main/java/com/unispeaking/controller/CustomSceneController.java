package com.unispeaking.controller;

import com.unispeaking.domain.dto.request.CustomSceneRequest;
import com.unispeaking.domain.dto.response.ApiResponse;
import com.unispeaking.domain.dto.scene.AdvanceSceneStageRequest;
import com.unispeaking.domain.dto.scene.CompleteSceneFlowRequest;
import com.unispeaking.domain.dto.scene.CreateSceneFlowRequest;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.scene.StartSceneSessionResponse;
import com.unispeaking.mapper.CustomSceneMapper;
import com.unispeaking.orchestration.SceneSessionCoordinator;
import com.unispeaking.service.scene.SceneFlowService;
import com.unispeaking.service.scene.SceneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/custom-scenes")
public class CustomSceneController {

	private final SceneService sceneService;
	private final SceneFlowService sceneFlowService;
	private final SceneSessionCoordinator sceneSessionCoordinator;
	private final CustomSceneMapper mapper;

	public CustomSceneController(
			SceneService sceneService,
			SceneFlowService sceneFlowService,
			SceneSessionCoordinator sceneSessionCoordinator,
			CustomSceneMapper mapper) {
		this.sceneService = sceneService;
		this.sceneFlowService = sceneFlowService;
		this.sceneSessionCoordinator = sceneSessionCoordinator;
		this.mapper = mapper;
	}

	@PostMapping("/generate")
	public ApiResponse<SceneGenerationResponse> generate(
			@Valid @RequestBody CustomSceneRequest request) {
		return ApiResponse.success(sceneService.generateScene(mapper.toGenerationRequest(request)));
	}

	@PostMapping("/start")
	public ApiResponse<StartSceneSessionResponse> start(
			@Valid @RequestBody CustomSceneRequest request) {
		return ApiResponse.success(sceneSessionCoordinator.start(mapper.toStartSessionRequest(request)));
	}

	@PostMapping("/flows")
	public ApiResponse<SceneFlowResponse> createFlow(
			@RequestBody CreateSceneFlowRequest request) {
		return ApiResponse.success(sceneFlowService.createFlow(request));
	}

	@PostMapping("/flows/advance")
	public ApiResponse<SceneFlowResponse> advanceStage(
			@RequestBody AdvanceSceneStageRequest request) {
		return ApiResponse.success(sceneFlowService.advanceStage(request));
	}

	@GetMapping("/flows/{flowId}")
	public ApiResponse<SceneFlowResponse> getFlow(@PathVariable String flowId) {
		return ApiResponse.success(sceneFlowService.getFlow(flowId));
	}

	@PostMapping("/flows/complete")
	public ApiResponse<Void> completeFlow(@RequestBody CompleteSceneFlowRequest request) {
		sceneFlowService.completeFlow(request);
		return ApiResponse.success(null);
	}
}
