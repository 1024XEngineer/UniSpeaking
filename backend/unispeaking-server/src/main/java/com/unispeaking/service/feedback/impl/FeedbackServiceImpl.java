package com.unispeaking.service.feedback.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.feedback.CreateFeedbackRequest;
import com.unispeaking.domain.dto.feedback.CreateFeedbackResponse;
import com.unispeaking.domain.dto.feedback.FeedbackListResponse;
import com.unispeaking.domain.dto.feedback.FeedbackResponse;
import com.unispeaking.domain.dto.feedback.UpdateFeedbackRequest;
import com.unispeaking.domain.po.feedback.UserFeedback;
import com.unispeaking.domain.vo.feedback.FeedbackStatus;
import com.unispeaking.infrastructure.persistence.repository.feedback.FeedbackRepository;
import com.unispeaking.service.feedback.FeedbackService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackServiceImpl implements FeedbackService {

	private static final Set<String> SUPPORTED_CATEGORIES = Set.of(
			"quick-start",
			"account-login",
			"ai-training",
			"audio",
			"learning-records",
			"membership",
			"privacy-security",
			"feedback");
	private static final DateTimeFormatter FEEDBACK_DATE =
			DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

	private final FeedbackRepository feedbacks;
	private final Clock clock;
	private final SecureRandom secureRandom;

	@Autowired
	public FeedbackServiceImpl(FeedbackRepository feedbacks) {
		this(feedbacks, Clock.systemUTC(), new SecureRandom());
	}

	FeedbackServiceImpl(
			FeedbackRepository feedbacks,
			Clock clock,
			SecureRandom secureRandom) {
		this.feedbacks = feedbacks;
		this.clock = clock;
		this.secureRandom = secureRandom;
	}

	@Override
	@Transactional
	public CreateFeedbackResponse create(
			UUID userId,
			CreateFeedbackRequest request) {
		validateCreateRequest(request);
		Instant now = clock.instant();
		String lookupCode = generateLookupCode();
		UserFeedback feedback = new UserFeedback(
				UUID.randomUUID(),
				generateFeedbackNo(now),
				userId,
				hashLookupCode(lookupCode),
				request.categoryId().trim(),
				request.title().trim(),
				request.description().trim(),
				normalizeNullable(request.environment()),
				FeedbackStatus.SUBMITTED,
				null,
				null,
				now,
				now);
		UserFeedback created = feedbacks.create(feedback);
		return new CreateFeedbackResponse(
				FeedbackResponse.from(created),
				lookupCode);
	}

	@Override
	@Transactional(readOnly = true)
	public FeedbackResponse lookup(String feedbackNo, String lookupCode) {
		UserFeedback feedback = feedbacks.findByFeedbackNo(feedbackNo)
				.orElseThrow(this::lookupDenied);
		String normalizedCode = normalizeNullable(lookupCode);
		if (normalizedCode == null || !secureHashEquals(
				feedback.lookupCodeHash(),
				hashLookupCode(normalizedCode))) {
			throw lookupDenied();
		}
		return FeedbackResponse.from(feedback);
	}

	@Override
	@Transactional(readOnly = true)
	public FeedbackListResponse findMine(UUID userId) {
		if (userId == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "请先登录");
		}
		return new FeedbackListResponse(feedbacks.findAllByUserId(userId).stream()
				.map(FeedbackResponse::from)
				.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public FeedbackListResponse findAll(FeedbackStatus status) {
		return new FeedbackListResponse(feedbacks.findAll(status).stream()
				.map(FeedbackResponse::from)
				.toList());
	}

	@Override
	@Transactional
	public FeedbackResponse update(
			String feedbackNo,
			UpdateFeedbackRequest request) {
		if (request == null || request.status() == null) {
			throw new BusinessException("FEEDBACK_STATUS_REQUIRED", "请选择反馈状态");
		}
		UserFeedback existing = feedbacks.findByFeedbackNo(feedbackNo)
				.orElseThrow(this::feedbackNotFound);
		validateTransition(existing.status(), request.status());
		String reply = normalizeNullable(request.reply());
		if (reply == null && existing.reply() != null) {
			reply = existing.reply();
		}
		if (request.status() == FeedbackStatus.RESOLVED
				|| request.status() == FeedbackStatus.CLOSED) {
			if (reply == null) {
				throw new BusinessException(
						"FEEDBACK_REPLY_REQUIRED",
						"解决或关闭反馈时必须填写回复");
			}
		}
		else if (reply != null) {
			throw new BusinessException(
					"FEEDBACK_REPLY_STATUS_INVALID",
					"反馈进入解决或关闭状态后才能填写回复");
		}
		UserFeedback updated = existing.withResolution(
				request.status(),
				reply,
				clock.instant());
		return FeedbackResponse.from(feedbacks.update(existing, updated));
	}

	private void validateCreateRequest(CreateFeedbackRequest request) {
		if (request == null
				|| request.categoryId() == null
				|| !SUPPORTED_CATEGORIES.contains(request.categoryId().trim())) {
			throw new BusinessException("FEEDBACK_CATEGORY_INVALID", "请选择有效的问题分类");
		}
		validateText(request.title(), 80, "FEEDBACK_TITLE_INVALID", "请填写 80 个字符以内的问题标题");
		validateText(
				request.description(),
				2000,
				"FEEDBACK_DESCRIPTION_INVALID",
				"请填写 2000 个字符以内的问题描述");
		if (request.environment() != null && request.environment().trim().length() > 200) {
			throw new BusinessException("FEEDBACK_ENVIRONMENT_INVALID", "设备与浏览器信息不能超过 200 个字符");
		}
	}

	private void validateText(
			String value,
			int maxLength,
			String code,
			String message) {
		if (value == null || value.isBlank() || value.trim().length() > maxLength) {
			throw new BusinessException(code, message);
		}
	}

	private void validateTransition(
			FeedbackStatus current,
			FeedbackStatus next) {
		boolean allowed = switch (current) {
			case SUBMITTED -> next == FeedbackStatus.IN_PROGRESS
					|| next == FeedbackStatus.RESOLVED
					|| next == FeedbackStatus.CLOSED;
			case IN_PROGRESS -> next == FeedbackStatus.RESOLVED
					|| next == FeedbackStatus.CLOSED;
			case RESOLVED -> next == FeedbackStatus.CLOSED;
			case CLOSED -> false;
		};
		if (!allowed) {
			throw new BusinessException(
					"FEEDBACK_STATUS_TRANSITION_INVALID",
					"当前反馈状态不能变更为 " + next.name());
		}
	}

	private String generateFeedbackNo(Instant now) {
		String suffix = UUID.randomUUID().toString()
				.replace("-", "")
				.substring(0, 12)
				.toUpperCase(Locale.ROOT);
		return "FB-" + FEEDBACK_DATE.format(now) + "-" + suffix;
	}

	private String generateLookupCode() {
		byte[] bytes = new byte[24];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hashLookupCode(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private boolean secureHashEquals(String expected, String actual) {
		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.US_ASCII),
				actual.getBytes(StandardCharsets.US_ASCII));
	}

	private String normalizeNullable(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private BusinessException lookupDenied() {
		return new BusinessException(
				"FEEDBACK_LOOKUP_DENIED",
				"反馈编号或查询码不正确");
	}

	private BusinessException feedbackNotFound() {
		return new BusinessException("FEEDBACK_NOT_FOUND", "没有找到这条反馈");
	}
}
