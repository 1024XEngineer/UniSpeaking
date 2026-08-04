package com.unispeaking.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.domain.dto.scene.IeltsCategoryResponse;
import com.unispeaking.domain.dto.scene.IeltsQuestionResponse;
import com.unispeaking.domain.dto.scene.IeltsTopicSearchResponse;
import com.unispeaking.domain.dto.scene.IeltsTopicSummaryResponse;
import com.unispeaking.domain.dto.scene.IeltsTrainingResponse;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsTopicType;
import com.unispeaking.service.scene.IELTSSceneService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IELTSSceneControllerTest {

	@Test
	void topicEndpointReturnsPagedTopicData() throws Exception {
		IELTSSceneService service = mock(IELTSSceneService.class);
		IeltsTopicSearchResponse response = new IeltsTopicSearchResponse(
				List.of(new IeltsCategoryResponse("REQUIRED", "必考题")),
				List.of(new IeltsTopicSummaryResponse(
						"topic-home",
						"Home and Accommodation",
						IeltsTopicType.PART_1_POOL,
						"REQUIRED",
						"必考题",
						"XDF",
						17)),
				2,
				10,
				17,
				2);
		when(service.searchTopics(
				IeltsPart.PART_1,
				"REQUIRED",
				"home",
				2,
				10)).thenReturn(response);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new IELTSSceneController(service)).build();

		mvc.perform(get("/api/ielts/topics")
						.param("part", "PART_1")
						.param("category", "REQUIRED")
						.param("keyword", "home")
						.param("page", "2")
						.param("pageSize", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.topics[0].title")
						.value("Home and Accommodation"))
				.andExpect(jsonPath("$.data.page").value(2))
				.andExpect(jsonPath("$.data.pageSize").value(10))
				.andExpect(jsonPath("$.data.total").value(17))
				.andExpect(jsonPath("$.data.totalPages").value(2));

		verify(service).searchTopics(
				IeltsPart.PART_1,
				"REQUIRED",
				"home",
				2,
				10);
	}

	@Test
	void trainingEndpointReturnsSelectedTopicQuestions() throws Exception {
		IELTSSceneService service = mock(IELTSSceneService.class);
		IeltsTrainingResponse response = new IeltsTrainingResponse(
				"topic-home",
				"Home and Accommodation",
				IeltsPart.PART_1,
				List.of(new IeltsQuestionResponse(
						"question-1",
						IeltsPart.PART_1,
						1,
						"What kind of home do you live in?",
						List.of(),
						List.of())));
		when(service.prepareTraining(IeltsPart.PART_1, "topic-home"))
				.thenReturn(response);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new IELTSSceneController(service)).build();

		mvc.perform(get("/api/ielts/training")
						.param("part", "PART_1")
						.param("topicId", "topic-home"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.topicId").value("topic-home"))
				.andExpect(jsonPath("$.data.questions[0].questionText")
						.value("What kind of home do you live in?"));
	}
}
