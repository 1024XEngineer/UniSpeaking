package com.unispeaking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.domain.dto.asset.InterviewAssetItem;
import com.unispeaking.domain.dto.scene.InterviewMaterial;
import com.unispeaking.domain.dto.scene.InterviewMaterialDraft;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.service.scene.impl.InterviewSceneServiceImpl;
import com.unispeaking.service.session.InterviewSessionService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InterviewSceneControllerTest {

	@Test
	void prepareMaterialsBindsMultipartAndReturnsDraft() throws Exception {
		InterviewSceneServiceImpl service = mock(InterviewSceneServiceImpl.class);
		InterviewMaterial material = new InterviewMaterial(
				"Java 工程师",
				List.of("负责后端服务开发"),
				List.of("熟悉 Java 21"),
				List.of("Spring"),
				"",
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				"Java 工程师岗位职责与任职要求");
		when(service.prepareMaterials(any()))
				.thenReturn(new InterviewMaterialDraft(material));
		MockMvc mvc = MockMvcBuilders
				.standaloneSetup(new InterviewSceneController(
						service,
						mock(InterviewSessionService.class),
						mock(RecordingStore.class)))
				.build();

		mvc.perform(multipart("/api/interview-scenes/prepare-materials")
						.param("jobDescriptionText", "负责后端服务开发，熟悉 Java 21")
						.param("resumeText", "三年后端经验")
						.file(new MockMultipartFile(
								"unused",
								"unused.txt",
								"text/plain",
								new byte[] {})))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.material.jobTitle").value("Java 工程师"))
				.andExpect(jsonPath("$.data.material.responsibilities[0]")
						.value("负责后端服务开发"));
	}

	@Test
	void prepareMaterialsAcceptsResumePdf() throws Exception {
		InterviewSceneServiceImpl service = mock(InterviewSceneServiceImpl.class);
		when(service.prepareMaterials(any()))
				.thenReturn(new InterviewMaterialDraft(new InterviewMaterial(
						"Java 工程师",
						List.of("职责"),
						List.of("要求"),
						List.of(),
						"",
						List.of(),
						List.of(),
						List.of(),
						List.of(),
						List.of(),
						"最终文本")));
		MockMvc mvc = MockMvcBuilders
				.standaloneSetup(new InterviewSceneController(
						service,
						mock(InterviewSessionService.class),
						mock(RecordingStore.class)))
				.build();

		mvc.perform(multipart("/api/interview-scenes/prepare-materials")
						.param("jobDescriptionText", "JD 文本")
						.file(new MockMultipartFile(
								"resumeFile",
								"resume.pdf",
								"application/pdf",
								"%PDF-1.4 fake pdf".getBytes())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.material.finalText").value("最终文本"));
	}

	@Test
	void listAssetsReturnsOwnedInterviewAssetItems() throws Exception {
		InterviewSceneServiceImpl service = mock(InterviewSceneServiceImpl.class);
		OffsetDateTime now = OffsetDateTime.parse("2026-08-09T00:00:00Z");
		when(service.listOwnedScenes()).thenReturn(List.of(new InterviewAssetItem(
				"interview_1",
				"后端开发工程师",
				"HARD",
				"session-1",
				"COMPLETED",
				new BigDecimal("85.0"),
				now,
				2,
				now)));
		MockMvc mvc = MockMvcBuilders
				.standaloneSetup(new InterviewSceneController(
						service,
						mock(InterviewSessionService.class),
						mock(RecordingStore.class)))
				.build();

		mvc.perform(get("/api/interview-scenes/assets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data[0].sceneId").value("interview_1"))
				.andExpect(jsonPath("$.data[0].jobTitle").value("后端开发工程师"))
				.andExpect(jsonPath("$.data[0].difficulty").value("HARD"))
				.andExpect(jsonPath("$.data[0].latestReportStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.data[0].latestOverallScore").value(85.0))
				.andExpect(jsonPath("$.data[0].practiceCount").value(2));
	}

	@Test
	void ocrAvailabilityDelegatesToService() throws Exception {
		InterviewSceneServiceImpl service = mock(InterviewSceneServiceImpl.class);
		when(service.isOcrAvailable()).thenReturn(false);
		MockMvc mvc = MockMvcBuilders
				.standaloneSetup(new InterviewSceneController(
						service,
						mock(InterviewSessionService.class),
						mock(RecordingStore.class)))
				.build();

		mvc.perform(get("/api/interview-scenes/ocr/availability"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.available").value(false));
	}

	@Test
	void startSessionRoutesSceneIdAndDialogueRequest() throws Exception {
		InterviewSessionService sessions = mock(InterviewSessionService.class);
		when(sessions.startSession(any(), any()))
				.thenReturn(new StartSceneSessionResponse(
						"interview_1",
						"模拟面试",
						com.unispeaking.domain.vo.scene.SceneType.INTERVIEW_SCENE,
						List.of(),
						List.of(),
						List.of(),
						com.unispeaking.domain.vo.scene.SceneFlowStage.DIALOGUE,
						true,
						"session-1",
						"provider-session",
						"answer-sdp",
						null,
						"Katerina",
						com.unispeaking.domain.vo.session.SessionStatus.WAITING_CLIENT,
						"2026-08-09T00:00:00Z",
						"system-prompt"));
		MockMvc mvc = MockMvcBuilders
				.standaloneSetup(new InterviewSceneController(
						mock(InterviewSceneServiceImpl.class),
						sessions,
						mock(RecordingStore.class)))
				.build();

		mvc.perform(post("/api/interview-scenes/interview_1/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"offerSdp":"sdp","provider":"QWEN","model":"qwen3.5-plus","voice":"Katerina","translationEnabled":true}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.sceneId").value("interview_1"))
				.andExpect(jsonPath("$.data.sessionId").value("session-1"))
				.andExpect(jsonPath("$.data.scoringEnabled").value(true));
	}

	@Test
	void submitTurnBindsMultipartWithAudio() throws Exception {
		InterviewSessionService sessions = mock(InterviewSessionService.class);
		when(sessions.submitTurn(any(), any(), anyInt(), any(), any()))
				.thenReturn(new com.unispeaking.domain.dto.session.InterviewTurnResult(
						new com.unispeaking.domain.dto.session.InterviewTurnStateResponse(
								false, 1, "自我介绍"),
						null));
		MockMvc mvc = MockMvcBuilders
				.standaloneSetup(new InterviewSceneController(
						mock(InterviewSceneServiceImpl.class),
						sessions,
						mock(RecordingStore.class)))
				.build();
		byte[] wav = new byte[] {
				(byte) 0x52, (byte) 0x49, (byte) 0x46, (byte) 0x46,
				0x00, 0x00, 0x00, 0x00, (byte) 0x57, (byte) 0x41, (byte) 0x56, (byte) 0x45};

		mvc.perform(multipart(
						"/api/interview-scenes/interview_1/sessions/session-1/turns/1")
						.param("transcript", "recorded transcript")
						.file(new MockMultipartFile(
								"audio", "interview-turn-1.wav", "audio/wav", wav)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.state.currentTopic").value("自我介绍"))
				.andExpect(jsonPath("$.data.state.completedTopicCount").value(1));
	}
}
