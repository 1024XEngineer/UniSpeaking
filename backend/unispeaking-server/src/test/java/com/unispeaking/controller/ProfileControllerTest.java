package com.unispeaking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.common.exception.GlobalExceptionHandler;
import com.unispeaking.domain.dto.profile.ProfileInsightsResponse;
import com.unispeaking.domain.dto.profile.UpdateWeeklyLearningGoalsRequest;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.profile.ProfileAccountService;
import com.unispeaking.service.profile.ProfileInsightsService;
import com.unispeaking.service.profile.ProfileOverviewService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileControllerTest {

	private static final String USER_ID =
			"11111111-1111-4111-8111-111111111111";

	private AuthService authService;
	private ProfileInsightsService insightsService;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		authService = mock(AuthService.class);
		insightsService = mock(ProfileInsightsService.class);
		when(authService.requireUserId(null)).thenReturn(USER_ID);
		mvc = MockMvcBuilders.standaloneSetup(new ProfileController(
							authService,
							mock(ProfileOverviewService.class),
							mock(ProfileAccountService.class),
							insightsService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void returnsWeeklyLearningGoalProgress() throws Exception {
		when(insightsService.getInsights(USER_ID)).thenReturn(response());

		mvc.perform(get("/api/profile/insights"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.weeklyGoals.durationTargetMinutes")
						.value(120))
				.andExpect(jsonPath("$.data.weeklyGoals.completedDurationSeconds")
						.value(4560))
				.andExpect(jsonPath("$.data.weeklyGoals.completedTrainingCount")
						.value(3));

		verify(authService).requireUserId(null);
		verify(insightsService).getInsights(USER_ID);
	}

	@Test
	void updatesBothWeeklyLearningGoals() throws Exception {
		when(insightsService.updateGoals(
					any(String.class),
					any(UpdateWeeklyLearningGoalsRequest.class)))
				.thenReturn(response());

		mvc.perform(put("/api/profile/insights/goals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "durationTargetMinutes": 120,
								  "trainingCountTarget": 5
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.weeklyGoals.trainingCountTarget")
						.value(5));

		verify(insightsService).updateGoals(
					any(String.class),
					any(UpdateWeeklyLearningGoalsRequest.class));
	}

	@Test
	void rejectsMissingAndOutOfRangeTargets() throws Exception {
		mvc.perform(put("/api/profile/insights/goals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "durationTargetMinutes": 1261,
								  "trainingCountTarget": 0
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mvc.perform(put("/api/profile/insights/goals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private ProfileInsightsResponse response() {
		return new ProfileInsightsResponse(new ProfileInsightsResponse.WeeklyGoals(
				OffsetDateTime.parse("2026-08-03T00:00:00+08:00"),
				OffsetDateTime.parse("2026-08-10T00:00:00+08:00"),
				120,
				4560,
				2640,
				63.3,
				false,
				5,
				3,
				2,
				60.0,
				false));
	}
}
