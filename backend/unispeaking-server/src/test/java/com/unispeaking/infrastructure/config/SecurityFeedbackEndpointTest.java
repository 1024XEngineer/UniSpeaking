package com.unispeaking.infrastructure.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.service.evaluation.EvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFeedbackEndpointTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private EvaluationService evaluationService;

	@MockitoBean
	private SceneRepository sceneRepository;

	@Test
	void allowsAnonymousSubmissionAndLookupRoutes() throws Exception {
		mvc.perform(post("/api/feedbacks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mvc.perform(get("/api/feedbacks/lookup/FB-20260804-ABCDEF123456"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void keepsPersonalAndAdministrativeFeedbackRoutesAuthenticated() throws Exception {
		mvc.perform(get("/api/feedbacks/mine"))
				.andExpect(status().isUnauthorized());

		mvc.perform(get("/api/admin/feedbacks"))
				.andExpect(status().isUnauthorized());
	}
}
