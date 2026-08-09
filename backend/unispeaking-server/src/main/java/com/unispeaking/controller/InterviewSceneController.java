package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.domain.dto.evaluation.InterviewEndResponse;
import com.unispeaking.domain.dto.evaluation.InterviewReportResponse;
import com.unispeaking.domain.dto.ocr.OcrImage;
import com.unispeaking.domain.dto.scene.InterviewMaterialDraft;
import com.unispeaking.domain.dto.scene.InterviewMaterialPreparationInput;
import com.unispeaking.domain.dto.scene.InterviewResumeFile;
import com.unispeaking.domain.dto.scene.InterviewSceneRequest;
import com.unispeaking.domain.dto.scene.InterviewSceneResult;
import com.unispeaking.domain.dto.session.InterviewTurnRequest;
import com.unispeaking.domain.dto.session.InterviewTurnResult;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.service.scene.InterviewSceneService;
import com.unispeaking.service.session.InterviewSessionService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Interview 场景端点：generate + prepare-materials + 会话启动 + 结束/报告/录音。 */
@RestController
@RequestMapping("/api/interview-scenes")
public class InterviewSceneController {

	private final InterviewSceneService interviewSceneService;
	private final InterviewSessionService interviewSessionService;
	private final RecordingStore interviewRecordingStore;

	public InterviewSceneController(
			InterviewSceneService interviewSceneService,
			InterviewSessionService interviewSessionService,
			@Qualifier("interviewRecordingStore") RecordingStore interviewRecordingStore) {
		this.interviewSceneService = interviewSceneService;
		this.interviewSessionService = interviewSessionService;
		this.interviewRecordingStore = interviewRecordingStore;
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

	@PostMapping("/{sceneId}/sessions")
	public ApiResponse<StartSceneSessionResponse> startSession(
			@PathVariable String sceneId,
			@Valid @RequestBody StartCustomSceneDialogueRequest request) {
		return ApiResponse.success(
				interviewSessionService.startSession(sceneId, request));
	}

	@PostMapping(
			value = "/{sceneId}/sessions/{sessionId}/turns/{turnNo}",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<InterviewTurnResult> submitTurn(
			@PathVariable String sceneId,
			@PathVariable String sessionId,
			@PathVariable int turnNo,
			@ModelAttribute InterviewTurnRequest request)
			throws IOException {
		return ApiResponse.success(
				interviewSessionService.submitTurn(
						sceneId,
						sessionId,
						turnNo,
						request.transcript(),
						request.audio()));
	}

	@PostMapping("/{sceneId}/sessions/{sessionId}/end")
	public ApiResponse<InterviewEndResponse> endInterview(
			@PathVariable String sceneId,
			@PathVariable String sessionId) {
		return ApiResponse.success(
				interviewSessionService.endInterview(sceneId, sessionId));
	}

	@GetMapping("/{sceneId}/sessions/{sessionId}/report")
	public ApiResponse<InterviewReportResponse> getReport(
			@PathVariable String sceneId,
			@PathVariable String sessionId) {
		return ApiResponse.success(
				interviewSessionService.getReport(sceneId, sessionId));
	}

	@PostMapping("/{sceneId}/sessions/{sessionId}/report/retry")
	public ApiResponse<InterviewReportResponse> retryReport(
			@PathVariable String sceneId,
			@PathVariable String sessionId) {
		return ApiResponse.success(
				interviewSessionService.retryReport(sceneId, sessionId));
	}

	@PostMapping(
			value = "/{sceneId}/sessions/{sessionId}/ai-audio",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<String> uploadAiAudio(
			@PathVariable String sceneId,
			@PathVariable String sessionId,
			@RequestParam("audio") MultipartFile audio)
			throws IOException {
		return ApiResponse.success(
				interviewSessionService.uploadAiAudio(
						sceneId,
						sessionId,
						audio.getBytes()));
	}

	@GetMapping(
			value = "/{sceneId}/sessions/{sessionId}/recording",
			produces = "audio/wav")
	public ResponseEntity<Resource> getRecording(
			@PathVariable String sceneId,
			@PathVariable String sessionId) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("audio/wav"))
				.cacheControl(CacheControl.noStore().cachePrivate())
				.body(interviewRecordingStore.loadSessionRecording(
						sceneId,
						sessionId));
	}

	@GetMapping(
			value = "/{sceneId}/sessions/{sessionId}/recordings/{fileName:.+}",
			produces = "audio/wav")
	public ResponseEntity<Resource> getSegmentRecording(
			@PathVariable String sceneId,
			@PathVariable String sessionId,
			@PathVariable String fileName) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("audio/wav"))
				.cacheControl(CacheControl.noStore().cachePrivate())
				.body(interviewRecordingStore.loadOwned(
						sceneId,
						sessionId,
						fileName));
	}

	@DeleteMapping("/{sceneId}")
	public ApiResponse<Void> deleteScene(@PathVariable String sceneId) {
		interviewSceneService.deleteScene(sceneId);
		return ApiResponse.success(null);
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
