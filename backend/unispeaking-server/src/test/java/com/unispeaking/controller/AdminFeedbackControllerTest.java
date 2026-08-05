package com.unispeaking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.common.exception.GlobalExceptionHandler;
import com.unispeaking.domain.dto.feedback.FeedbackListResponse;
import com.unispeaking.domain.dto.feedback.FeedbackResponse;
import com.unispeaking.domain.vo.feedback.FeedbackStatus;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.feedback.FeedbackService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminFeedbackControllerTest {

	private AuthService authService;
	private FeedbackService feedbackService;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		authService = mock(AuthService.class);
		feedbackService = mock(FeedbackService.class);
		mvc = MockMvcBuilders.standaloneSetup(
					new AdminFeedbackController(authService, feedbackService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void listsFeedbackAfterAdminAuthorization() throws Exception {
		when(feedbackService.findAll(FeedbackStatus.SUBMITTED))
				.thenReturn(new FeedbackListResponse(List.of(feedback())));

		mvc.perform(get("/api/admin/feedbacks?status=SUBMITTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedbacks.length()").value(1));

		verify(authService).requireAdminUserId();
		verify(feedbackService).findAll(FeedbackStatus.SUBMITTED);
	}

	@Test
	void updatesFeedbackAfterAdminAuthorization() throws Exception {
		when(feedbackService.update(any(), any())).thenReturn(feedback());

		mvc.perform(patch("/api/admin/feedbacks/FB-20260804-ABCDEF123456")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"RESOLVED\",\"reply\":\"已经处理\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedbackNo")
						.value("FB-20260804-ABCDEF123456"));

		verify(authService).requireAdminUserId();
	}

	private FeedbackResponse feedback() {
		Instant now = Instant.parse("2026-08-04T08:00:00Z");
		return new FeedbackResponse(
				"FB-20260804-ABCDEF123456",
				"audio",
				"麦克风无法使用",
				"允许权限后仍没有声音",
				null,
				FeedbackStatus.SUBMITTED,
				null,
				null,
				now,
				now);
	}
}
