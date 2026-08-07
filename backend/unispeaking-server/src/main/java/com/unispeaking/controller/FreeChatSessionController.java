package com.unispeaking.controller;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.dto.session.StartFreeChatRequest;
import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.scene.TranslateTextRequest;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.session.FreeChatSessionService;
import com.unispeaking.service.scene.FreeChatSceneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scene-sessions")
public class FreeChatSessionController {

	private final FreeChatSessionService freeChatSessionService;
	private final FreeChatSceneService freeChatSceneService;

	public FreeChatSessionController(
			FreeChatSessionService freeChatSessionService,
			FreeChatSceneService freeChatSceneService) {
		this.freeChatSessionService = freeChatSessionService;
		this.freeChatSceneService = freeChatSceneService;
	}

	@PostMapping
	public ApiResponse<StartSceneSessionResponse> start(
			@Valid @RequestBody StartFreeChatRequest request) {
		RealtimeFlowLog.info(
				"session.controller.start sceneType={} model={} voice={}",
				SceneType.FREE_CHAT,
				request.model(),
				request.voice());
		return ApiResponse.success(freeChatSessionService.startSession(request));
	}

	@PostMapping("/{sessionId}/end")
	public ApiResponse<Void> end(@PathVariable String sessionId) {
		freeChatSessionService.endSession(sessionId);
		return ApiResponse.success(null);
	}

	@PostMapping("/{sessionId}/translations")
	public ApiResponse<TranslateTextResponse> translate(
			@PathVariable String sessionId,
			@Valid @RequestBody TranslateTextRequest request) {
		return ApiResponse.success(
				freeChatSceneService.translate(request.text()));
	}
}
