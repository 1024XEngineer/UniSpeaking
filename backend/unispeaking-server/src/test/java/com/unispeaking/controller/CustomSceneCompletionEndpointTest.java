package com.unispeaking.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueResponse;
import com.unispeaking.domain.dto.session.EndCustomSessionCommand;
import com.unispeaking.service.asset.LearningAssetService;
import com.unispeaking.service.evaluation.impl.CustomEvaluationServiceImpl;
import com.unispeaking.service.scene.impl.CustomSceneFlowServiceImpl;
import com.unispeaking.service.scene.impl.CustomSceneServiceImpl;
import com.unispeaking.service.session.impl.CustomSessionServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomSceneCompletionEndpointTest {

	@Test
	void activeHangupReturnsPersistedFiveDimensionReport() throws Exception {
		CustomSceneServiceImpl customSceneService = mock(CustomSceneServiceImpl.class);
		CustomSessionServiceImpl customSessionService = mock(CustomSessionServiceImpl.class);
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
				mock(CustomSceneFlowServiceImpl.class),
				mock(CustomEvaluationServiceImpl.class),
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
}
