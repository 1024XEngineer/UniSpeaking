package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.scene.IeltsTopicSearchResponse;
import com.unispeaking.domain.dto.scene.IeltsTrainingResponse;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.service.scene.IELTSSceneService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ielts")
@Validated
public class IELTSSceneController {

	private final IELTSSceneService ieltsSceneService;

	public IELTSSceneController(IELTSSceneService ieltsSceneService) {
		this.ieltsSceneService = ieltsSceneService;
	}

	@GetMapping("/topics")
	public ApiResponse<IeltsTopicSearchResponse> searchTopics(
			@RequestParam IeltsPart part,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "10") @Min(1) @Max(50) int pageSize) {
		return ApiResponse.success(ieltsSceneService.searchTopics(
				part,
				category,
				keyword,
				page,
				pageSize));
	}

	@GetMapping("/training")
	public ApiResponse<IeltsTrainingResponse> prepareTraining(
			@RequestParam IeltsPart part,
			@RequestParam(required = false) String topicId) {
		return ApiResponse.success(ieltsSceneService.prepareTraining(part, topicId));
	}
}
