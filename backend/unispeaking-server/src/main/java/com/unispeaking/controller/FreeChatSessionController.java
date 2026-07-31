package com.unispeaking.controller;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.dto.session.StartFreeChatRequest;
import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.scene.TranslateTextRequest;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.session.SessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scene-sessions")
public class FreeChatSessionController {

	private final SessionService sessionService;
	private final AuthService authService;

	public FreeChatSessionController(
			SessionService sessionService,
			AuthService authService) {
		this.sessionService = sessionService;
		this.authService = authService;
	}

	@PostMapping
	public ApiResponse<StartSceneSessionResponse> start(
			@Valid @RequestBody StartFreeChatRequest request) {
		RealtimeFlowLog.info(
				"session.controller.start sceneType={} model={} voice={}",
				SceneType.FREE_CHAT,
				request.model(),
				request.voice());
		return ApiResponse.success(sessionService.startFreeChat(request));
	}

	@PostMapping("/{sessionId}/end")
	public ApiResponse<Void> end(@PathVariable String sessionId) {
		sessionService.endSession(
				authService.requireUserId(null),
				sessionId,
				null);
		return ApiResponse.success(null);
	}

	@PostMapping("/{sessionId}/translations")
	public ApiResponse<TranslateTextResponse> translate(
			@PathVariable String sessionId,
			@Valid @RequestBody TranslateTextRequest request) {
		return ApiResponse.success(
				sessionService.translate(sessionId, request.text()));
	}
}
