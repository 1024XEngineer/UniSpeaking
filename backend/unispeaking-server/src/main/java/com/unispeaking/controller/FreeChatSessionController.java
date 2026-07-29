package com.unispeaking.controller;

import com.unispeaking.mapper.SceneSessionMapper;
import com.unispeaking.domain.dto.request.StartFreeChatRequest;
import com.unispeaking.domain.dto.response.ApiResponse;
import com.unispeaking.domain.dto.scene.StartSceneSessionResponse;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.orchestration.SceneSessionCoordinator;
import com.unispeaking.orchestration.SessionServiceSelector;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scene-sessions")
public class FreeChatSessionController {

	private final SceneSessionCoordinator sceneSessionCoordinator;
	private final SessionServiceSelector sessionServiceSelector;
	private final SceneSessionMapper mapper;

	public FreeChatSessionController(
			SceneSessionCoordinator sceneSessionCoordinator,
			SessionServiceSelector sessionServiceSelector,
			SceneSessionMapper mapper) {
		this.sceneSessionCoordinator = sceneSessionCoordinator;
		this.sessionServiceSelector = sessionServiceSelector;
		this.mapper = mapper;
	}

	@PostMapping
	public ApiResponse<StartSceneSessionResponse> start(@Valid @RequestBody StartFreeChatRequest request) {
		RealtimeFlowLog.info("session.controller.start sceneType={} model={} voice={}",
				SceneType.FREE_CHAT, request.model(), request.voice());
		return ApiResponse.success(sceneSessionCoordinator.start(
				mapper.toFreeChatSessionRequest(request)));
	}

	@PostMapping("/{sessionId}/end")
	public ApiResponse<Void> end(@PathVariable String sessionId) {
		sessionServiceSelector.endSession(sessionId, null);
		return ApiResponse.success(null);
	}
}
