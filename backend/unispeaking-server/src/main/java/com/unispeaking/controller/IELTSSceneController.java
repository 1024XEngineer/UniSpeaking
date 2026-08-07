package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.component.recording.IeltsRecordingStore;
import com.unispeaking.domain.dto.scene.CreateSceneFlowRequest;
import com.unispeaking.domain.dto.scene.IeltsGenerationRequest;
import com.unispeaking.domain.dto.scene.IeltsGenerationResponse;
import com.unispeaking.domain.dto.scene.IeltsSettingsResponse;
import com.unispeaking.domain.dto.scene.IeltsTopicSearchResponse;
import com.unispeaking.domain.dto.scene.IeltsTrainingResponse;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.scene.UpdateIeltsSettingsRequest;
import com.unispeaking.domain.dto.session.StartIeltsDialogueRequest;
import com.unispeaking.domain.dto.session.StartIeltsSessionResponse;
import com.unispeaking.domain.dto.session.IeltsDialogueStateResponse;
import com.unispeaking.domain.dto.session.IeltsPart2StateRequest;
import com.unispeaking.domain.dto.session.IeltsPart2StateResponse;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationHistoryItem;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.service.scene.impl.IeltsSceneServiceImpl;
import com.unispeaking.service.scene.impl.IeltsSceneFlowServiceImpl;
import com.unispeaking.service.evaluation.impl.IeltsEvaluationServiceImpl;
import com.unispeaking.service.session.impl.IeltsSessionServiceImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/ielts")
@Validated
public class IELTSSceneController {

	private final IeltsSceneServiceImpl ieltsSceneService;
	private final IeltsSceneFlowServiceImpl sceneFlowService;
	private final IeltsEvaluationServiceImpl evaluationService;
	private final IeltsSessionServiceImpl ieltsSessionService;
	private final IeltsRecordingStore recordingStore;

	public IELTSSceneController(
			IeltsSceneServiceImpl ieltsSceneService,
			IeltsSceneFlowServiceImpl sceneFlowService,
			IeltsEvaluationServiceImpl evaluationService,
			IeltsSessionServiceImpl ieltsSessionService,
			IeltsRecordingStore recordingStore) {
		this.ieltsSceneService = ieltsSceneService;
		this.sceneFlowService = sceneFlowService;
		this.evaluationService = evaluationService;
		this.ieltsSessionService = ieltsSessionService;
		this.recordingStore = recordingStore;
	}

	@GetMapping(value = "/recordings/{sessionId}/{fileName:.+}", produces = "audio/wav")
	public ResponseEntity<Resource> getRecording(
			@PathVariable String sessionId,
			@PathVariable String fileName) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("audio/wav"))
				.cacheControl(CacheControl.noStore().cachePrivate())
				.body(recordingStore.loadOwned(sessionId, fileName));
	}

	@GetMapping("/settings")
	public ApiResponse<IeltsSettingsResponse> getSettings() {
		IeltsSettingsResponse settings = ieltsSceneService.getSettings();
		return ApiResponse.success(new IeltsSettingsResponse(
				settings.targetScore(),
				settings.todayCompletedCount(),
				settings.examinerId(),
				settings.preferredVoice(),
				evaluationService.getLatestEstimatedScore(),
				settings.currentStreakDays(),
				settings.totalCheckInDays(),
				settings.lastCheckInDate()));
	}

	@PutMapping("/settings")
	public ApiResponse<IeltsSettingsResponse> updateSettings(
			@Valid @RequestBody UpdateIeltsSettingsRequest request) {
		IeltsSettingsResponse settings = ieltsSceneService.updateSettings(request);
		return ApiResponse.success(new IeltsSettingsResponse(
				settings.targetScore(),
				settings.todayCompletedCount(),
				settings.examinerId(),
				settings.preferredVoice(),
				evaluationService.getLatestEstimatedScore(),
				settings.currentStreakDays(),
				settings.totalCheckInDays(),
				settings.lastCheckInDate()));
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
		return ApiResponse.success(
				ieltsSceneService.prepareTraining(part, topicId));
	}

	@PostMapping("/generate")
	public ApiResponse<IeltsGenerationResponse> generate(
			@Valid @RequestBody IeltsGenerationRequest request) {
		return ApiResponse.success(ieltsSceneService.generate(request));
	}

	@PostMapping("/flows")
	public ApiResponse<SceneFlowResponse> createFlow(
			@RequestBody CreateSceneFlowRequest request) {
		sceneFlowService.start(request.sceneId());
		return ApiResponse.success(sceneFlowService.response(request.sceneId()));
	}

	@PostMapping("/{ieltsId}/sessions")
	public ApiResponse<StartIeltsSessionResponse> startSession(
			@PathVariable String ieltsId,
			@Valid @RequestBody StartIeltsDialogueRequest request) {
		return ApiResponse.success(ieltsSessionService.startSession(ieltsId, request));
	}

	@PostMapping(
			value = "/{ieltsId}/sessions/{sessionId}/turns/{turnNo}/evaluation",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<DialogueTurnEvaluationResult> evaluateTurn(
			@PathVariable String ieltsId,
			@PathVariable String sessionId,
			@PathVariable int turnNo,
			@RequestParam String transcript,
			@RequestParam(required = false) MultipartFile audio)
			throws IOException {
		return ApiResponse.success(evaluationService.evaluateTurn(
				new DialogueTurnEvaluationCommand(
						sessionId,
						turnNo,
						audio == null ? null : audio.getBytes(),
						transcript)));
	}

	@PostMapping("/{ieltsId}/sessions/{sessionId}/turns/{turnNo}/state")
	public ApiResponse<IeltsDialogueStateResponse> advanceDialogueState(
			@PathVariable String ieltsId,
			@PathVariable String sessionId,
			@PathVariable int turnNo,
			@RequestParam(defaultValue = "false") boolean timedOut) {
		return ApiResponse.success(ieltsSessionService.advanceState(
				ieltsId,
				sessionId,
				turnNo,
				timedOut));
	}

	@GetMapping("/{ieltsId}/sessions/{sessionId}/state")
	public ApiResponse<IeltsDialogueStateResponse> getDialogueState(
			@PathVariable String ieltsId,
			@PathVariable String sessionId) {
		return ApiResponse.success(
				ieltsSessionService.getState(ieltsId, sessionId));
	}

	@PostMapping("/{ieltsId}/sessions/{sessionId}/part2/state")
	public ApiResponse<IeltsPart2StateResponse> advancePart2State(
			@PathVariable String ieltsId,
			@PathVariable String sessionId,
			@Valid @RequestBody IeltsPart2StateRequest request) {
		return ApiResponse.success(ieltsSessionService.advancePart2State(
				ieltsId,
				sessionId,
				request.event()));
	}

	@GetMapping("/{ieltsId}/sessions/{sessionId}/part2/state")
	public ApiResponse<IeltsPart2StateResponse> getPart2State(
			@PathVariable String ieltsId,
			@PathVariable String sessionId) {
		return ApiResponse.success(
				ieltsSessionService.getPart2State(ieltsId, sessionId));
	}

	@PostMapping("/{ieltsId}/sessions/{sessionId}/evaluation")
	public ApiResponse<IeltsEvaluationResult> generateEvaluation(
			@PathVariable String ieltsId,
			@PathVariable String sessionId) {
		return ApiResponse.success(
				evaluationService.generateEvaluation(ieltsId, sessionId));
	}

	@GetMapping("/evaluations")
	public ApiResponse<List<IeltsEvaluationHistoryItem>> getEvaluationHistory() {
		return ApiResponse.success(
				evaluationService.getHistory());
	}
}
