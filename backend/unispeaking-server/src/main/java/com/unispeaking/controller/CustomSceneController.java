package com.unispeaking.controller;

import com.unispeaking.domain.dto.asset.LearningAssetDetail;
import com.unispeaking.domain.dto.asset.LearningAssetSummary;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationResponse;
import com.unispeaking.domain.dto.scene.CustomSceneRequest;
import com.unispeaking.domain.dto.scene.TtsRequest;
import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.scene.AdvanceSceneStageRequest;
import com.unispeaking.domain.dto.session.AdvanceScenarioDialogueTurnRequest;
import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueResponse;
import com.unispeaking.domain.dto.session.EndCustomSessionCommand;
import com.unispeaking.domain.dto.scene.CompleteSceneFlowRequest;
import com.unispeaking.domain.dto.scene.CreateSceneFlowRequest;
import com.unispeaking.domain.dto.scene.CustomSceneGenerationTaskResponse;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartCustomSessionCommand;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.scene.TranslateTextRequest;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.service.asset.LearningAssetService;
import com.unispeaking.component.scene.CustomSceneGenerationCoordinator;
import com.unispeaking.service.evaluation.CustomEvaluationService;
import com.unispeaking.service.scene.CustomSceneFlowService;
import com.unispeaking.service.scene.CustomSceneService;
import com.unispeaking.service.session.CustomSessionService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/custom-scenes")
public class CustomSceneController {

	private final CustomSceneService customSceneService;
	private final CustomSceneFlowService sceneFlowService;
	private final CustomEvaluationService evaluationService;
	private final CustomSessionService customSessionService;
	private final LearningAssetService learningAssetService;
	private final CustomSceneGenerationCoordinator generationCoordinator;

	public CustomSceneController(
			CustomSceneService customSceneService,
			CustomSceneFlowService sceneFlowService,
			CustomEvaluationService evaluationService,
			CustomSessionService customSessionService,
			LearningAssetService learningAssetService,
			CustomSceneGenerationCoordinator generationCoordinator) {
		this.customSceneService = customSceneService;
		this.sceneFlowService = sceneFlowService;
		this.evaluationService = evaluationService;
		this.customSessionService = customSessionService;
		this.learningAssetService = learningAssetService;
		this.generationCoordinator = generationCoordinator;
	}

	@PostMapping("/generate")
	public ApiResponse<CustomSceneGenerationTaskResponse> generate(
			@Valid @RequestBody CustomSceneRequest request) {
		return ApiResponse.success(generationCoordinator.submit(request));
	}

	@GetMapping("/generation-tasks/{taskId}")
	public ApiResponse<CustomSceneGenerationTaskResponse> getGenerationTask(
			@PathVariable UUID taskId) {
		return ApiResponse.success(generationCoordinator.get(taskId));
	}

	@PostMapping("/flows")
	public ApiResponse<SceneFlowResponse> createFlow(
			@RequestBody CreateSceneFlowRequest request) {
		sceneFlowService.start(request.sceneId());
		return ApiResponse.success(sceneFlowService.response(request.sceneId()));
	}

	@PostMapping("/flows/advance")
	public ApiResponse<SceneFlowResponse> advanceStage(
			@RequestBody AdvanceSceneStageRequest request) {
		if (request.stage() == null) {
			sceneFlowService.next(request.sceneId());
		}
		else {
			sceneFlowService.next(request.sceneId(), request.stage());
		}
		return ApiResponse.success(sceneFlowService.response(request.sceneId()));
	}

	@PostMapping("/flows/complete")
	public ApiResponse<Void> completeFlow(
			@RequestBody CompleteSceneFlowRequest request) {
		if (Boolean.TRUE.equals(request.completed())) {
			while (!sceneFlowService.isCompleted(request.sceneId())) {
				sceneFlowService.next(request.sceneId());
			}
			sceneFlowService.clear(request.sceneId());
		}
		return ApiResponse.success(null);
	}

	@GetMapping("/flows/{sceneId}/content")
	public ApiResponse<List<LearningContentItem>> getByCurrentStage(
			@PathVariable String sceneId,
			@RequestParam(required = false) SceneFlowStage stage) {
		return ApiResponse.success(sceneFlowService.content(sceneId, stage));
	}

	@PostMapping("/{sceneId}/sessions")
	public ApiResponse<StartSceneSessionResponse> startDialogue(
			@PathVariable String sceneId,
			@Valid @RequestBody StartCustomSceneDialogueRequest request) {
		return ApiResponse.success(
				customSessionService.startSession(
						new StartCustomSessionCommand(sceneId, request)));
	}

	@PostMapping(
			value = "/{sceneId}/sessions/{sessionId}/turns/{turnNo}/evaluation",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<DialogueTurnEvaluationResult> evaluateDialogueTurn(
			@PathVariable String sceneId,
			@PathVariable String sessionId,
			@PathVariable int turnNo,
			@RequestParam String transcript,
			@RequestParam(required = false) MultipartFile audio)
			throws IOException {
		return ApiResponse.success(
				evaluationService.evaluateTurn(
						new DialogueTurnEvaluationCommand(
								sessionId,
								turnNo,
								audio == null ? null : audio.getBytes(),
								transcript)));
	}

	@PostMapping("/{sceneId}/sessions/{sessionId}/turns/{turnNo}/state")
	public ApiResponse<ScenarioDialogueStateResponse> advanceDialogueState(
			@PathVariable String sceneId,
			@PathVariable String sessionId,
			@PathVariable int turnNo,
			@Valid @RequestBody AdvanceScenarioDialogueTurnRequest request) {
		return ApiResponse.success(
				sceneFlowService.advanceDialogueState(
						sceneId,
						sessionId,
						turnNo,
						request.transcript()));
	}

	@PostMapping("/{sceneId}/sessions/{sessionId}/complete")
	public ApiResponse<CompleteCustomSceneDialogueResponse> completeDialogue(
			@PathVariable String sceneId,
			@PathVariable String sessionId,
			@RequestBody(required = false)
					CompleteCustomSceneDialogueRequest request) {
		return ApiResponse.success(customSessionService.endSession(
				new EndCustomSessionCommand(
						sceneId,
						sessionId,
						request == null ? null : request.stopTime())));
	}

	@GetMapping("/{sceneId}/sessions/{sessionId}/evaluation")
	public ApiResponse<DialogueReportResult> getDialogueEvaluation(
			@PathVariable String sceneId,
			@PathVariable String sessionId) {
		return ApiResponse.success(
				learningAssetService.getReport(sceneId, sessionId));
	}

	@GetMapping("/assets")
	public ApiResponse<List<LearningAssetSummary>> listLearningAssets() {
		return ApiResponse.success(learningAssetService.listAssets());
	}

	@GetMapping("/{sceneId}/assets")
	public ApiResponse<LearningAssetDetail> getLearningAsset(
			@PathVariable String sceneId) {
		return ApiResponse.success(learningAssetService.getAsset(sceneId));
	}

	@DeleteMapping("/{sceneId}/assets")
	public ApiResponse<Void> deleteLearningAsset(@PathVariable String sceneId) {
		learningAssetService.deleteAsset(sceneId);
		return ApiResponse.success(null);
	}

	@GetMapping("/{sceneId}/sessions/{sessionId}/state")
	public ApiResponse<ScenarioDialogueStateResponse> getDialogueState(
			@PathVariable String sceneId,
			@PathVariable String sessionId) {
		return ApiResponse.success(
				sceneFlowService.getDialogueState(sceneId, sessionId));
	}

	@PostMapping(
			value = "/{sceneId}/sentences/{sentenceId}/evaluation",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<SentenceEvaluationResponse> evaluateSentence(
			@PathVariable String sceneId,
			@PathVariable String sentenceId,
			@RequestPart("audio") MultipartFile audio) throws IOException {
		return ApiResponse.success(
				evaluationService.evaluateSentence(
						sentenceId,
						audio.getBytes()));
	}

	@PostMapping(
			value = "/{sceneId}/speech",
			produces = "audio/wav")
	public ResponseEntity<byte[]> synthesizeSpeech(
			@PathVariable String sceneId,
			@Valid @RequestBody TtsRequest request) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.contentType(MediaType.parseMediaType("audio/wav"))
				.body(customSceneService.synthesizeSpeech(
						sceneId,
						request.text(),
						request.model()));
	}

	@PostMapping("/{sceneId}/translations")
	public ApiResponse<TranslateTextResponse> translate(
			@PathVariable String sceneId,
			@Valid @RequestBody TranslateTextRequest request) {
		return ApiResponse.success(
				customSceneService.translate(sceneId, request.text()));
	}
}
