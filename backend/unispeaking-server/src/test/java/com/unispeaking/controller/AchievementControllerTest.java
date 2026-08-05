package com.unispeaking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.GlobalExceptionHandler;
import com.unispeaking.domain.dto.achievement.AchievementAcknowledgeRequest;
import com.unispeaking.domain.dto.achievement.AchievementAcknowledgeResponse;
import com.unispeaking.domain.dto.achievement.AchievementOverviewResponse;
import com.unispeaking.domain.dto.achievement.AchievementSyncResponse;
import com.unispeaking.service.achievement.AchievementService;
import com.unispeaking.service.auth.AuthService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AchievementControllerTest {

	private static final UUID USER_ID =
			UUID.fromString("11111111-1111-4111-8111-111111111111");

	private AuthService authService;
	private AchievementService achievementService;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		authService = mock(AuthService.class);
		achievementService = mock(AchievementService.class);
		when(authService.requireUserId(null)).thenReturn(USER_ID.toString());
		mvc = MockMvcBuilders.standaloneSetup(
					new AchievementController(authService, achievementService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void returnsAchievementOverviewForAuthenticatedUser() throws Exception {
		when(achievementService.getOverview(USER_ID))
				.thenReturn(new AchievementOverviewResponse(List.of()));

		mvc.perform(get("/api/achievements"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("OK"))
				.andExpect(jsonPath("$.data.series").isArray());

		verify(authService).requireUserId(null);
		verify(achievementService).getOverview(USER_ID);
	}

	@Test
	void synchronizesAchievementUnlocksWithoutARequestBody() throws Exception {
		AchievementOverviewResponse overview =
				new AchievementOverviewResponse(List.of());
		when(achievementService.synchronize(USER_ID)).thenReturn(
				new AchievementSyncResponse(
						true,
						overview,
						List.of(),
						List.of()));

		mvc.perform(post("/api/achievement-unlocks"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.initialized").value(true))
				.andExpect(jsonPath("$.data.newlyUnlocked").isArray())
				.andExpect(jsonPath("$.data.pendingNotifications").isArray());

		verify(achievementService).synchronize(USER_ID);
	}

	@Test
	void acknowledgesDisplayedAchievement() throws Exception {
		Instant acknowledgedAt = Instant.parse("2026-08-04T02:30:05Z");
		when(achievementService.acknowledge(
				eq(USER_ID),
				eq("conversation-4"),
				any(AchievementAcknowledgeRequest.class)))
				.thenReturn(new AchievementAcknowledgeResponse(
						"conversation-4",
						acknowledgedAt));

		mvc.perform(patch("/api/achievement-unlocks/conversation-4")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"acknowledged\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.achievementId")
						.value("conversation-4"))
				.andExpect(jsonPath("$.data.acknowledgedAt")
						.value("2026-08-04T02:30:05Z"));
	}

	@Test
	void returnsBadRequestForInvalidAcknowledgement() throws Exception {
		when(achievementService.acknowledge(
				eq(USER_ID),
				eq("conversation-4"),
				any(AchievementAcknowledgeRequest.class)))
				.thenThrow(new BusinessException(
						"ACHIEVEMENT_ACKNOWLEDGEMENT_INVALID",
						"acknowledged 必须为 true"));

		mvc.perform(patch("/api/achievement-unlocks/conversation-4")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"acknowledged\":false}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code")
						.value("ACHIEVEMENT_ACKNOWLEDGEMENT_INVALID"));
	}
}
