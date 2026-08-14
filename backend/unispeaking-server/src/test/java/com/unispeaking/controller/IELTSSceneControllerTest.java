package com.unispeaking.controller;

import com.unispeaking.component.recording.RecordingStore;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.domain.dto.scene.IeltsGenerationRequest;
import com.unispeaking.domain.dto.scene.IeltsGenerationResponse;
import com.unispeaking.domain.dto.scene.IeltsSettingsResponse;
import com.unispeaking.domain.dto.scene.IeltsCategoryResponse;
import com.unispeaking.domain.dto.scene.IeltsQuestionResponse;
import com.unispeaking.domain.dto.scene.IeltsTopicSearchResponse;
import com.unispeaking.domain.dto.scene.IeltsTopicSummaryResponse;
import com.unispeaking.domain.dto.scene.IeltsTrainingResponse;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.session.StartIeltsDialogueRequest;
import com.unispeaking.domain.dto.session.StartIeltsSessionResponse;
import com.unispeaking.domain.dto.session.StartIeltsSessionCommand;
import com.unispeaking.domain.dto.session.IeltsPart2StateResponse;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsContentQuestion;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsPart2Event;
import com.unispeaking.domain.vo.scene.IeltsTopicType;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.service.evaluation.IeltsEvaluationService;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import com.unispeaking.service.scene.IeltsSceneService;
import com.unispeaking.service.session.IeltsSessionService;
import java.util.List;
import java.time.Instant;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IELTSSceneControllerTest {

	@Test
	void recordingEndpointIsExposedByIeltsController() throws Exception {
		RecordingStore recordingStore = mock(RecordingStore.class);
		when(recordingStore.loadOwned("session_1", "turn-1.wav"))
				.thenReturn(new ByteArrayResource(new byte[] {1, 2, 3}));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new IELTSSceneController(
						mock(IeltsSceneService.class),
						mock(IeltsSceneFlowService.class),
						mock(IeltsEvaluationService.class),
						mock(IeltsSessionService.class),
						recordingStore)).build();

		mvc.perform(get("/api/ielts/recordings/session_1/turn-1.wav"))
				.andExpect(status().isOk())
				.andExpect(content().contentType("audio/wav"))
				.andExpect(header().string("Cache-Control", "no-store, private"))
				.andExpect(content().bytes(new byte[] {1, 2, 3}));

		verify(recordingStore).loadOwned("session_1", "turn-1.wav");
	}

	@Test
	void partTwoStateEndpointAcceptsApplicationTimerEvents() throws Exception {
		IeltsSceneService sceneService = mock(IeltsSceneService.class);
		IeltsSceneFlowService flowService = mock(IeltsSceneFlowService.class);
		IeltsSessionService sessionService = mock(IeltsSessionService.class);
		when(flowService.advancePart2State(
				"ielts_2",
				"session_2",
				IeltsPart2Event.PREPARATION_COMPLETE)).thenReturn(
					new IeltsPart2StateResponse(
							"ielts_2",
							"session_2",
							"LONG_TURN",
							false,
							"Please begin speaking now."));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new IELTSSceneController(
						sceneService,
						flowService,
						mock(IeltsEvaluationService.class),
						sessionService,
						mock(RecordingStore.class))).build();

		mvc.perform(post("/api/ielts/ielts_2/sessions/session_2/part2/state")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"event":"PREPARATION_COMPLETE"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.phase").value("LONG_TURN"))
				.andExpect(jsonPath("$.data.completed").value(false));

		verify(flowService).advancePart2State(
				"ielts_2",
				"session_2",
				IeltsPart2Event.PREPARATION_COMPLETE);
	}

	@Test
	void settingsUsesPersistedTargetCountAndLatestMockScore()
			throws Exception {
		IeltsSceneService sceneService = mock(IeltsSceneService.class);
		IeltsSceneFlowService flowService = mock(IeltsSceneFlowService.class);
		IeltsEvaluationService evaluationService = mock(IeltsEvaluationService.class);
		when(sceneService.getSettings()).thenReturn(new IeltsSettingsResponse(
				new BigDecimal("7.0"),
				2,
				"daniel",
				"Harvey",
				null));
		when(evaluationService.getLatestEstimatedScore())
				.thenReturn(new BigDecimal("6.5"));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new IELTSSceneController(
						sceneService,
						flowService,
						evaluationService,
						mock(IeltsSessionService.class),
						mock(RecordingStore.class))).build();

		mvc.perform(get("/api/ielts/settings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.targetScore").value(7.0))
				.andExpect(jsonPath("$.data.todayCompletedCount").value(2))
				.andExpect(jsonPath("$.data.latestEstimatedScore").value(6.5));
	}

	@Test
	void topicAndTrainingEndpointsAreExposedForEveryPart() throws Exception {
		IeltsSceneService sceneService = mock(IeltsSceneService.class);
		IeltsSceneFlowService flowService = mock(IeltsSceneFlowService.class);
		when(sceneService.searchTopics(
				IeltsPart.PART_1,
				"REQUIRED",
				"home",
				1,
				10)).thenReturn(new IeltsTopicSearchResponse(
				List.of(new IeltsCategoryResponse("REQUIRED", "必考题")),
				List.of(new IeltsTopicSummaryResponse(
						"topic-home",
						"Home",
						IeltsTopicType.PART_1_POOL,
						"REQUIRED",
						"必考题",
						"import",
						8)),
				1,
				10,
				1,
				1));
		when(sceneService.prepareTraining(
				IeltsPart.PART_1,
				"topic-home")).thenReturn(new IeltsTrainingResponse(
				"topic-home",
				"Home",
				IeltsPart.PART_1,
				List.of(new IeltsQuestionResponse(
						"question-1",
						IeltsPart.PART_1,
						1,
						"What kind of home do you live in?",
						List.of(),
						List.of()))));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new IELTSSceneController(
						sceneService,
						flowService,
						mock(IeltsEvaluationService.class),
						mock(IeltsSessionService.class),
						mock(RecordingStore.class))).build();

		mvc.perform(get("/api/ielts/topics")
						.param("part", "PART_1")
						.param("category", "REQUIRED")
						.param("keyword", "home"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.topics[0].id")
						.value("topic-home"));

		mvc.perform(get("/api/ielts/training")
						.param("part", "PART_1")
						.param("topicId", "topic-home"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.questions[0].questionText")
						.value("What kind of home do you live in?"));
	}

	@Test
	void generateDelegatesOnlyToIeltsSceneService() throws Exception {
		IeltsSceneService sceneService = mock(IeltsSceneService.class);
		IeltsSceneFlowService flowService = mock(IeltsSceneFlowService.class);
		IeltsGenerationRequest request = new IeltsGenerationRequest(
				IeltsMode.PART_PRACTICE,
				IeltsPart.PART_1,
				"topic-weekends");
		IeltsContent content = new IeltsContent(
				List.of(new IeltsContentQuestion(
						"What do you do at weekends?",
						List.of(),
						List.of())),
				List.of(),
				List.of());
		when(sceneService.generate(request)).thenReturn(new IeltsGenerationResponse(
				"ielts_123",
				IeltsMode.PART_PRACTICE,
				IeltsPart.PART_1,
				"topic-weekends",
				"Weekends",
				content,
				"Harvey",
				"IELTS prompt"));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new IELTSSceneController(
						sceneService,
						flowService,
						mock(IeltsEvaluationService.class),
						mock(IeltsSessionService.class),
						mock(RecordingStore.class))).build();

		mvc.perform(post("/api/ielts/generate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "mode": "PART_PRACTICE",
								  "part": "PART_1",
								  "topicId": "topic-weekends"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.ieltsId").value("ielts_123"))
				.andExpect(jsonPath("$.data.content.part1[0].question")
						.value("What do you do at weekends?"));

		verify(sceneService).generate(request);
	}

	@Test
	void flowEndpointUsesSceneFlowServiceDirectly() throws Exception {
		IeltsSceneService sceneService = mock(IeltsSceneService.class);
		IeltsSceneFlowService flowService = mock(IeltsSceneFlowService.class);
		when(flowService.response("ielts_123")).thenReturn(
				new SceneFlowResponse(
						"ielts_123",
						SceneFlowStage.DIALOGUE,
						false));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new IELTSSceneController(
						sceneService,
						flowService,
						mock(IeltsEvaluationService.class),
						mock(IeltsSessionService.class),
						mock(RecordingStore.class))).build();

		mvc.perform(post("/api/ielts/flows")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sceneId\":\"ielts_123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.stage").value("DIALOGUE"));

		verify(flowService).start("ielts_123");
	}

	@Test
	void startSessionReturnsIeltsContentWithoutCustomLearningFields()
			throws Exception {
		IeltsSceneService sceneService = mock(IeltsSceneService.class);
		IeltsSceneFlowService flowService = mock(IeltsSceneFlowService.class);
		IeltsSessionService sessionService = mock(IeltsSessionService.class);
		IeltsContent content = new IeltsContent(
				List.of(new IeltsContentQuestion(
						"What do you do at weekends?",
						List.of(),
						List.of())),
				List.of(),
				List.of());
		StartIeltsDialogueRequest request = new StartIeltsDialogueRequest(
				"offer-sdp",
				ProviderType.QWEN,
				"qwen3.5-omni-flash-realtime",
				"Harvey",
				true);
		when(sessionService.startSession(
				new StartIeltsSessionCommand("ielts_123", request))).thenReturn(
				new StartIeltsSessionResponse(
						"ielts_123",
						"Weekends",
						SceneType.IELTS_SCENE,
						content,
						IeltsPart.PART_1,
						true,
						"ielts_session_123",
						"provider-session",
						"answer-sdp",
						Instant.parse("2026-08-04T08:49:49Z"),
						"Harvey",
						SessionStatus.WAITING_CLIENT,
						"2026-08-04T08:44:49Z",
						"IELTS prompt"));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new IELTSSceneController(
						sceneService,
						flowService,
						mock(IeltsEvaluationService.class),
						sessionService,
						mock(RecordingStore.class))).build();

		mvc.perform(post("/api/ielts/ielts_123/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "offerSdp": "offer-sdp",
								  "provider": "QWEN",
								  "model": "qwen3.5-omni-flash-realtime",
								  "voiceId": "Harvey",
								  "translationEnabled": true
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.part1[0].question")
						.value("What do you do at weekends?"))
				.andExpect(jsonPath("$.data.wordList").doesNotExist())
				.andExpect(jsonPath("$.data.phraseList").doesNotExist())
				.andExpect(jsonPath("$.data.sentenceList").doesNotExist())
				.andExpect(jsonPath("$.data.currentStage").value("PART_1"))
				.andExpect(jsonPath("$.data.answerSdp").value("answer-sdp"));

		verify(sessionService).startSession(
				new StartIeltsSessionCommand("ielts_123", request));
	}
}
