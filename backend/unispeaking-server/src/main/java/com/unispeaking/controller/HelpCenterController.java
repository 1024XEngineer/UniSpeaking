package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.help.HelpCenterResponse;
import com.unispeaking.domain.dto.help.HelpArticleResponse;
import com.unispeaking.domain.dto.help.HelpCategoryDetailResponse;
import com.unispeaking.service.help.HelpCenterService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

	@GetMapping("/categories/{categoryId}")
	public ApiResponse<HelpCategoryDetailResponse> getCategory(
			@PathVariable String categoryId) {
		return ApiResponse.success(helpCenterService.getCategory(categoryId)
				.orElseThrow(() -> notFound("帮助分类不存在")));
	}

	@GetMapping("/articles/{articleId}")
	public ApiResponse<HelpArticleResponse> getArticle(
			@PathVariable String articleId) {
		return ApiResponse.success(helpCenterService.getArticle(articleId)
				.orElseThrow(() -> notFound("帮助文章不存在")));
	}

	private ResponseStatusException notFound(String message) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
	}
}
