package com.unispeaking.controller;

import com.unispeaking.domain.dto.response.ApiResponse;
import com.unispeaking.domain.dto.translation.TranslateTextRequest;
import com.unispeaking.domain.dto.translation.TranslateTextResponse;
import com.unispeaking.service.translation.TranslationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/translations")
public class TranslationController {

	private final TranslationService translationService;

	public TranslationController(TranslationService translationService) {
		this.translationService = translationService;
	}

	@PostMapping
	public ApiResponse<TranslateTextResponse> translate(
			@Valid @RequestBody TranslateTextRequest request) {
		return ApiResponse.success(
				translationService.translateToSimplifiedChinese(request.text()));
	}
}
