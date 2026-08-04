package com.unispeaking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.common.exception.GlobalExceptionHandler;
import com.unispeaking.domain.dto.feedback.CreateFeedbackResponse;
import com.unispeaking.domain.dto.feedback.FeedbackListResponse;
import com.unispeaking.domain.dto.feedback.FeedbackResponse;
import com.unispeaking.domain.vo.feedback.FeedbackStatus;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.feedback.FeedbackService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FeedbackControllerTest {

	private static final UUID USER_ID =
			UUID.fromString("11111111-1111-4111-8111-111111111111");

	private AuthService authService;
	private FeedbackService feedbackService;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		authService = mock(AuthService.class);
		feedbackService = mock(FeedbackService.class);
		mvc = MockMvcBuilders.standaloneSetup(
					new FeedbackController(authService, feedbackService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void createsAnonymousFeedbackAndReturnsLookupCode() throws Exception {
		when(feedbackService.create(eq(null), any()))
				.thenReturn(new CreateFeedbackResponse(feedback(), "lookup-secret"));

		mvc.perform(post("/api/feedbacks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "categoryId":"audio",
								  "title":"麦克风无法使用",
								  "description":"允许权限后仍没有声音",
								  "environment":"Chrome 138"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.lookupCode").value("lookup-secret"))
				.andExpect(jsonPath("$.data.feedback.status").value("SUBMITTED"));

		verify(authService).currentUserIdOrNull();
		verify(feedbackService).create(eq(null), any());
	}

	@Test
	void rejectsInvalidFeedbackBeforeServiceCall() throws Exception {
		mvc.perform(post("/api/feedbacks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"categoryId\":\"audio\",\"title\":\"\",\"description\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void looksUpFeedbackUsingDedicatedHeader() throws Exception {
		when(feedbackService.lookup("FB-20260804-ABCDEF123456", "lookup-secret"))
				.thenReturn(feedback());

		mvc.perform(get("/api/feedbacks/lookup/FB-20260804-ABCDEF123456")
						.header("X-Feedback-Lookup-Code", "lookup-secret"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedbackNo")
						.value("FB-20260804-ABCDEF123456"));
	}

	@Test
	void listsFeedbackForAuthenticatedUser() throws Exception {
		when(authService.requireUserId(null)).thenReturn(USER_ID.toString());
		when(feedbackService.findMine(USER_ID))
				.thenReturn(new FeedbackListResponse(List.of(feedback())));

		mvc.perform(get("/api/feedbacks/mine"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedbacks.length()").value(1));

		verify(feedbackService).findMine(USER_ID);
	}

	private FeedbackResponse feedback() {
		Instant now = Instant.parse("2026-08-04T08:00:00Z");
		return new FeedbackResponse(
				"FB-20260804-ABCDEF123456",
				"audio",
				"麦克风无法使用",
				"允许权限后仍没有声音",
				"Chrome 138",
				FeedbackStatus.SUBMITTED,
				null,
				null,
				now,
				now);
	}
}
