package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.session.IceServerConfigurationResponse;
import com.unispeaking.service.session.RealtimeNetworkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeNetworkController {

	private final RealtimeNetworkService realtimeNetworkService;

	public RealtimeNetworkController(RealtimeNetworkService realtimeNetworkService) {
		this.realtimeNetworkService = realtimeNetworkService;
	}

	@GetMapping("/ice-configuration")
	public ApiResponse<IceServerConfigurationResponse> iceConfiguration(
			@RequestParam(defaultValue = "false") boolean forceRelay) {
		return ApiResponse.success(realtimeNetworkService.getIceConfiguration(forceRelay));
	}
}
