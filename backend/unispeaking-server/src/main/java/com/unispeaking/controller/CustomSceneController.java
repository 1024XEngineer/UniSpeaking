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
import com.unispeaking.domain.dto.scene.CompleteSceneFlowRequest;
import com.unispeaking.domain.dto.scene.CreateSceneFlowRequest;
import com.unispeaking.domain.dto.scene.CustomSceneGenerationResponse;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.scene.TranslateTextRequest;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.service.asset.LearningAssetService;
import com.unispeaking.service.evaluation.EvaluationService;
import com.unispeaking.service.scene.SceneFlowService;
import com.unispeaking.service.scene.CustomSceneService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
	private final SceneFlowService sceneFlowService;
	private final EvaluationService evaluationService;
	private final LearningAssetService learningAssetService;

	public CustomSceneController(
			CustomSceneService customSceneService,
			SceneFlowService sceneFlowService,
			EvaluationService evaluationService,
			LearningAssetService learningAssetService) {
		this.customSceneService = customSceneService;
		this.sceneFlowService = sceneFlowService;
		this.evaluationService = evaluationService;
		this.learningAssetService = learningAssetService;
	}

	@PostMapping("/generate")
	public ApiResponse<CustomSceneGenerationResponse> generate(
			@Valid @RequestBody CustomSceneRequest request) {
		return ApiResponse.success(customSceneService.generate(request));
	}

	@PostMapping("/flows")
	public ApiResponse<SceneFlowResponse> createFlow(
			@RequestBody CreateSceneFlowRequest request) {
		return ApiResponse.success(sceneFlowService.createFlow(request.sceneId()));
	}

	@PostMapping("/flows/advance")
	public ApiResponse<SceneFlowResponse> advanceStage(
			@RequestBody AdvanceSceneStageRequest request) {
		return ApiResponse.success(sceneFlowService.advanceStage(
				request.sceneId(),
				request.stage()));
	}

	@PostMapping("/flows/complete")
	public ApiResponse<Void> completeFlow(
			@RequestBody CompleteSceneFlowRequest request) {
		sceneFlowService.completeFlow(request.sceneId(), request.completed());
		return ApiResponse.success(null);
	}

	@GetMapping("/flows/{sceneId}/content")
	public ApiResponse<List<LearningContentItem>> getByCurrentStage(
			@PathVariable String sceneId,
			@RequestParam(required = false) SceneFlowStage stage) {
		return ApiResponse.success(
				sceneFlowService.getByCurrentStage(sceneId, stage));
	}

	@PostMapping("/{sceneId}/sessions")
	public ApiResponse<StartSceneSessionResponse> startDialogue(
			@PathVariable String sceneId,
			@Valid @RequestBody StartCustomSceneDialogueRequest request) {
		return ApiResponse.success(
				customSceneService.startSession(sceneId, request));
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
				evaluationService.evaluateDialogueTurn(
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
				customSceneService.advanceSessionState(
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
		return ApiResponse.success(customSceneService.completeSession(
				sceneId,
				sessionId,
				request == null ? null : request.stopTime()));
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

	@GetMapping("/{sceneId}/sessions/{sessionId}/state")
	public ApiResponse<ScenarioDialogueStateResponse> getDialogueState(
			@PathVariable String sceneId,
			@PathVariable String sessionId) {
		return ApiResponse.success(
				customSceneService.getSessionState(sceneId, sessionId));
	}

	@PostMapping(
			value = "/{sceneId}/sentences/{sentenceId}/evaluation",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<SentenceEvaluationResponse> evaluateSentence(
			@PathVariable String sceneId,
			@PathVariable String sentenceId,
			@RequestPart("audio") MultipartFile audio) throws IOException {
		return ApiResponse.success(
				evaluationService.evaluateSentenceReading(
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
