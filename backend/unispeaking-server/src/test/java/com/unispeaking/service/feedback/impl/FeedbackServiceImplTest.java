package com.unispeaking.service.feedback.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.feedback.CreateFeedbackRequest;
import com.unispeaking.domain.dto.feedback.UpdateFeedbackRequest;
import com.unispeaking.domain.po.feedback.UserFeedback;
import com.unispeaking.domain.vo.feedback.FeedbackStatus;
import com.unispeaking.infrastructure.persistence.repository.feedback.FeedbackRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FeedbackServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

	private FeedbackRepository feedbacks;
	private FeedbackServiceImpl service;

	@BeforeEach
	void setUp() {
		feedbacks = mock(FeedbackRepository.class);
		service = new FeedbackServiceImpl(
				feedbacks,
				Clock.fixed(NOW, ZoneOffset.UTC),
				new java.security.SecureRandom());
	}

	@Test
	void createsAnonymousFeedbackWithTrackableLookupCode() {
		when(feedbacks.create(any(UserFeedback.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		var response = service.create(null, request());

		assertEquals(FeedbackStatus.SUBMITTED, response.feedback().status());
		assertEquals(NOW, response.feedback().createdAt());
		assertNotNull(response.lookupCode());
		assertEquals(32, response.lookupCode().length());
		ArgumentCaptor<UserFeedback> created = ArgumentCaptor.forClass(UserFeedback.class);
		verify(feedbacks).create(created.capture());
		assertNull(created.getValue().userId());
		assertEquals(64, created.getValue().lookupCodeHash().length());
	}

	@Test
	void looksUpFeedbackOnlyWithMatchingCode() {
		when(feedbacks.create(any(UserFeedback.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		var created = service.create(null, request());
		ArgumentCaptor<UserFeedback> captured = ArgumentCaptor.forClass(UserFeedback.class);
		verify(feedbacks).create(captured.capture());
		when(feedbacks.findByFeedbackNo(created.feedback().feedbackNo()))
				.thenReturn(Optional.of(captured.getValue()));

		assertEquals(
				created.feedback().feedbackNo(),
				service.lookup(
						created.feedback().feedbackNo(),
						created.lookupCode()).feedbackNo());
		BusinessException denied = assertThrows(
				BusinessException.class,
				() -> service.lookup(created.feedback().feedbackNo(), "wrong-code"));
		assertEquals("FEEDBACK_LOOKUP_DENIED", denied.code());
	}

	@Test
	void listsOnlyFeedbackOwnedByAuthenticatedUser() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		when(feedbacks.findAllByUserId(userId)).thenReturn(List.of(feedback()));

		assertEquals(1, service.findMine(userId).feedbacks().size());
		assertEquals(
				"AUTHENTICATION_REQUIRED",
				assertThrows(BusinessException.class, () -> service.findMine(null)).code());
	}

	@Test
	void enforcesForwardStatusTransitionsAndResolutionReply() {
		UserFeedback submitted = feedback();
		when(feedbacks.findByFeedbackNo(submitted.feedbackNo()))
				.thenReturn(Optional.of(submitted));
		when(feedbacks.update(any(UserFeedback.class), any(UserFeedback.class)))
				.thenAnswer(invocation -> invocation.getArgument(1));

		var processing = service.update(
				submitted.feedbackNo(),
				new UpdateFeedbackRequest(FeedbackStatus.IN_PROGRESS, null));
		assertEquals(FeedbackStatus.IN_PROGRESS, processing.status());

		BusinessException missingReply = assertThrows(
				BusinessException.class,
				() -> service.update(
						submitted.feedbackNo(),
						new UpdateFeedbackRequest(FeedbackStatus.RESOLVED, null)));
		assertEquals("FEEDBACK_REPLY_REQUIRED", missingReply.code());

		var resolved = service.update(
				submitted.feedbackNo(),
				new UpdateFeedbackRequest(FeedbackStatus.RESOLVED, " 已恢复正常 "));
		assertEquals("已恢复正常", resolved.reply());
		assertEquals(NOW, resolved.repliedAt());

		BusinessException backwards = assertThrows(
				BusinessException.class,
				() -> service.update(
						submitted.feedbackNo(),
						new UpdateFeedbackRequest(FeedbackStatus.SUBMITTED, null)));
		assertEquals("FEEDBACK_STATUS_TRANSITION_INVALID", backwards.code());
	}

	@Test
	void rejectsUnsupportedCategoryBeforePersistence() {
		BusinessException invalid = assertThrows(
				BusinessException.class,
				() -> service.create(null, new CreateFeedbackRequest(
						"future-feature",
						"标题",
						"描述",
						null)));

		assertEquals("FEEDBACK_CATEGORY_INVALID", invalid.code());
	}

	private CreateFeedbackRequest request() {
		return new CreateFeedbackRequest(
				"audio",
				" 麦克风无法使用 ",
				" 已允许权限但没有声音 ",
				" Chrome 138 ");
	}

	private UserFeedback feedback() {
		return new UserFeedback(
				UUID.fromString("22222222-2222-4222-8222-222222222222"),
				"FB-20260804-ABCDEF123456",
				null,
				"a".repeat(64),
				"audio",
				"麦克风无法使用",
				"已允许权限但没有声音",
				"Chrome 138",
				FeedbackStatus.SUBMITTED,
				null,
				null,
				NOW,
				NOW);
	}
}
