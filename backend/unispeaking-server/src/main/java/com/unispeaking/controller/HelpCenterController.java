package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.help.HelpCenterResponse;
import com.unispeaking.service.help.HelpCenterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/help-center")
public class HelpCenterController {

	private final HelpCenterService helpCenterService;

	public HelpCenterController(HelpCenterService helpCenterService) {
		this.helpCenterService = helpCenterService;
	}

	@GetMapping
	public ApiResponse<HelpCenterResponse> getHelpCenter() {
		return ApiResponse.success(helpCenterService.getHelpCenter());
	}
}
