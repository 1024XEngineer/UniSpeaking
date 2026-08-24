package com.unispeaking.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.GlobalExceptionHandler;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.scene.CustomSceneRequest;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueResponse;
import com.unispeaking.domain.dto.session.EndCustomSessionCommand;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartCustomSessionCommand;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.service.asset.LearningAssetService;
import com.unispeaking.service.evaluation.CustomEvaluationService;
import com.unispeaking.service.scene.CustomSceneFlowService;
import com.unispeaking.service.scene.CustomSceneService;
import com.unispeaking.service.session.CustomSessionService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomSceneCompletionEndpointTest {

	@Test
	void deletesLearningAssetThroughItsAssetEndpoint() throws Exception {
		LearningAssetService assets = mock(LearningAssetService.class);
		CustomSceneController controller = new CustomSceneController(
				mock(CustomSceneService.class),
				mock(CustomSceneFlowService.class),
				mock(CustomEvaluationService.class),
				mock(CustomSessionService.class),
				assets);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

		mvc.perform(delete("/api/custom-scenes/custom_2001/assets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(assets).deleteAsset("custom_2001");
	}

	@Test
	void activeHangupReturnsPersistedFiveDimensionReport() throws Exception {
		CustomSceneService customSceneService = mock(CustomSceneService.class);
		CustomSessionService customSessionService = mock(CustomSessionService.class);
		DialogueReportResult report = new DialogueReportResult(
				new BigDecimal("84.0"),
				new BigDecimal("81.0"),
				new BigDecimal("86.0"),
				new BigDecimal("79.0"),
				new BigDecimal("83.0"),
				new BigDecimal("83.0"),
				"本次场景练习已完成。",
				List.of("表达清楚"),
				List.of("增加词汇变化"));
		when(customSessionService.endSession(eq(
				new EndCustomSessionCommand(
						"custom_2001",
						"scene_5001",
						"2026-07-30T10:42:00Z"))))
				.thenReturn(new CompleteCustomSceneDialogueResponse(
						"custom_2001",
						"scene_5001",
						"2026-07-30T10:42:00Z",
						report,
						null));
		CustomSceneController controller = new CustomSceneController(
				customSceneService,
				mock(CustomSceneFlowService.class),
				mock(CustomEvaluationService.class),
				customSessionService,
				mock(LearningAssetService.class));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

		mvc.perform(post(
						"/api/custom-scenes/custom_2001/sessions/scene_5001/complete")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"stopTime":"2026-07-30T10:42:00Z"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.sessionId").value("scene_5001"))
				.andExpect(jsonPath("$.data.evaluation.accuracyScore").value(84.0))
				.andExpect(jsonPath("$.data.evaluation.fluencyScore").value(81.0))
				.andExpect(jsonPath("$.data.evaluation.grammarScore").value(86.0))
				.andExpect(jsonPath("$.data.evaluation.vocabularyScore").value(79.0))
				.andExpect(jsonPath("$.data.evaluation.naturalnessScore").value(83.0))
				.andExpect(jsonPath("$.data.evaluation.finalScore").value(83.0));

		verify(customSessionService).endSession(
				new EndCustomSessionCommand(
						"custom_2001",
						"scene_5001",
						"2026-07-30T10:42:00Z"));
	}

	@Test
	void startDialogueDelegatesSceneIdAndValidatedRealtimeRequest() throws Exception {
		CustomSessionService sessions = mock(CustomSessionService.class);
		when(sessions.startSession(eq(new StartCustomSessionCommand(
				"custom_2001",
				new com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest(
						"offer-sdp", ProviderType.QWEN, "model", "voice", true)))))
				.thenReturn(null);
		CustomSceneController controller = controller(
				mock(CustomSceneService.class), mock(CustomSceneFlowService.class),
				mock(CustomEvaluationService.class), sessions,
				mock(LearningAssetService.class));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();

		mvc.perform(post("/api/custom-scenes/custom_2001/sessions")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"offerSdp":"offer-sdp","provider":"QWEN","model":"model","voice":"voice","translationEnabled":true}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(sessions).startSession(eq(new StartCustomSessionCommand(
				"custom_2001",
				new StartCustomSceneDialogueRequest(
						"offer-sdp", ProviderType.QWEN, "model", "voice", true))));
	}

	@Test
	void evaluationAndSpeechEndpointsForwardMultipartAndTtsArguments() throws Exception {
		CustomEvaluationService evaluation = mock(CustomEvaluationService.class);
		CustomSceneService scenes = mock(CustomSceneService.class);
		when(scenes.synthesizeSpeech("custom_2001", "hello", "qwen-tts"))
				.thenReturn(new byte[] {1, 2, 3});
		CustomSceneController controller = controller(
				scenes, mock(CustomSceneFlowService.class), evaluation,
				mock(CustomSessionService.class), mock(LearningAssetService.class));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		MockMultipartFile audio = new MockMultipartFile(
				"audio", "answer.wav", "audio/wav", new byte[] {4, 5});

		mvc.perform(multipart(
					"/api/custom-scenes/custom_2001/sessions/session_1/turns/2/evaluation")
					.param("transcript", "hello")
					.file(audio))
				.andExpect(status().isOk());
		mvc.perform(post("/api/custom-scenes/custom_2001/speech")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"text\":\"hello\",\"model\":\"qwen-tts\"}"))
				.andExpect(status().isOk());

		verify(evaluation).evaluateTurn(argThat(command ->
				"session_1".equals(command.sessionId())
						&& command.turnNo().equals(2)
						&& "hello".equals(command.transcript())
						&& java.util.Arrays.equals(command.audio(), new byte[] {4, 5})));
		verify(scenes).synthesizeSpeech("custom_2001", "hello", "qwen-tts");
	}

	@Test
	void sentenceEvaluationAndTranslationDelegateToTheirServices() throws Exception {
		CustomEvaluationService evaluation = mock(CustomEvaluationService.class);
		CustomSceneService scenes = mock(CustomSceneService.class);
		when(scenes.translate("custom_2001", "你好"))
				.thenReturn(new TranslateTextResponse("你好", "hello", "en"));
		CustomSceneController controller = controller(
				scenes, mock(CustomSceneFlowService.class), evaluation,
				mock(CustomSessionService.class), mock(LearningAssetService.class));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		MockMultipartFile audio = new MockMultipartFile(
				"audio", "sentence.wav", "audio/wav", new byte[] {7});

		mvc.perform(multipart("/api/custom-scenes/custom_2001/sentences/sentence_1/evaluation")
					.file(audio))
				.andExpect(status().isOk());
		mvc.perform(post("/api/custom-scenes/custom_2001/translations")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"text\":\"你好\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.translatedText").value("hello"));

		verify(evaluation).evaluateSentence("sentence_1", new byte[] {7});
		verify(scenes).translate("custom_2001", "你好");
	}

	@Test
	void remainingEndpointsDelegateTheirArguments() {
		CustomSceneService scenes = mock(CustomSceneService.class);
		CustomEvaluationService evaluation = mock(CustomEvaluationService.class);
		CustomSessionService sessions = mock(CustomSessionService.class);
		LearningAssetService assets = mock(LearningAssetService.class);
		CustomSceneController controller = controller(
				scenes, mock(CustomSceneFlowService.class), evaluation,
				sessions, assets);
		CustomSceneRequest request = new CustomSceneRequest(
				"user_1", "preference", "a cafe", null,
				ProviderType.QWEN, "model", "voice", true);

		controller.generate(request);
		controller.completeDialogue("custom_2001", "session_1", null);
		controller.getDialogueEvaluation("custom_2001", "session_1");
		controller.listLearningAssets();
		controller.getLearningAsset("custom_2001");

		verify(scenes).generate(request);
		verify(sessions).endSession(new EndCustomSessionCommand(
				"custom_2001", "session_1", null));
		verify(assets).getReport("custom_2001", "session_1");
		verify(assets).listAssets();
		verify(assets).getAsset("custom_2001");
	}

	@Test
	void invalidValidatedRequestsReturnValidationErrorWithoutCallingServices() throws Exception {
		CustomSceneService scenes = mock(CustomSceneService.class);
		CustomSceneController controller = controller(
				scenes, mock(CustomSceneFlowService.class),
				mock(CustomEvaluationService.class), mock(CustomSessionService.class),
				mock(LearningAssetService.class));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();

		mvc.perform(post("/api/custom-scenes/custom_2001/speech")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"text\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(post("/api/custom-scenes/custom_2001/translations")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"text\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(post("/api/custom-scenes/custom_2001/sessions")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"offerSdp\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		verify(scenes, never()).synthesizeSpeech(
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any());
		verify(scenes, never()).translate(
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void businessExceptionFromTranslationIsMappedByGlobalHandler() throws Exception {
		CustomSceneService scenes = mock(CustomSceneService.class);
		when(scenes.translate("custom_2001", "你好"))
				.thenThrow(new BusinessException("SCENE_NOT_FOUND", "scene missing"));
		CustomSceneController controller = controller(
				scenes, mock(CustomSceneFlowService.class),
				mock(CustomEvaluationService.class), mock(CustomSessionService.class),
				mock(LearningAssetService.class));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();

		mvc.perform(post("/api/custom-scenes/custom_2001/translations")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"text\":\"你好\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("SCENE_NOT_FOUND"));
	}

	private CustomSceneController controller(
			CustomSceneService scenes,
			CustomSceneFlowService flow,
			CustomEvaluationService evaluation,
			CustomSessionService sessions,
			LearningAssetService assets) {
		return new CustomSceneController(scenes, flow, evaluation, sessions, assets);
	}
}
