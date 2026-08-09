package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.ocr.OcrImage;
import com.unispeaking.domain.dto.scene.InterviewMaterialDraft;
import com.unispeaking.domain.dto.scene.InterviewMaterialPreparationInput;
import com.unispeaking.domain.dto.scene.InterviewResumeFile;
import com.unispeaking.domain.dto.scene.InterviewSceneRequest;
import com.unispeaking.domain.dto.scene.InterviewSceneResult;
import com.unispeaking.service.scene.InterviewSceneService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Interview 场景端点：generate + prepare-materials。 */
@RestController
@RequestMapping("/api/interview-scenes")
public class InterviewSceneController {

	private final InterviewSceneService interviewSceneService;

	public InterviewSceneController(InterviewSceneService interviewSceneService) {
		this.interviewSceneService = interviewSceneService;
	}

	@PostMapping
	public ApiResponse<InterviewSceneResult> generate(
			@Valid @RequestBody InterviewSceneRequest request) {
		return ApiResponse.success(interviewSceneService.generate(request));
	}

	@PostMapping(
			value = "/prepare-materials",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<InterviewMaterialDraft> prepareMaterials(
			@RequestParam(required = false) String resumeText,
			@RequestParam(required = false) MultipartFile resumeFile,
			@RequestParam(required = false) String jobDescriptionText,
			@RequestParam(required = false) MultipartFile jobDescriptionImage)
			throws IOException {
		return ApiResponse.success(
				interviewSceneService.prepareMaterials(
						new InterviewMaterialPreparationInput(
								resumeText,
								toResumeFile(resumeFile),
								jobDescriptionText,
								jobDescriptionImage == null
										? null
										: new OcrImage(jobDescriptionImage.getBytes()))));
	}

	private static InterviewResumeFile toResumeFile(MultipartFile file)
			throws IOException {
		if (file == null) {
			return null;
		}
		return new InterviewResumeFile(
				file.getOriginalFilename(),
				file.getContentType(),
				file.getBytes());
	}
}
