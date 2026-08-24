package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.scene.DailyPicksResponse;
import com.unispeaking.service.scene.DailyPickService;
import java.util.HashSet;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/daily-picks")
public class DailyPickController {

	private final DailyPickService dailyPickService;

	public DailyPickController(DailyPickService dailyPickService) {
		this.dailyPickService = dailyPickService;
	}

	@GetMapping
	public ApiResponse<DailyPicksResponse> getDailyPicks(
			@RequestParam(name = "exclude", required = false) List<String> excludedIds) {
		return ApiResponse.success(dailyPickService.getDailyPicks(
				excludedIds == null ? new HashSet<>() : new HashSet<>(excludedIds)));
	}
}
