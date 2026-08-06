package com.unispeaking.domain.po.session;

import com.unispeaking.domain.vo.scene.InterviewSubmissionStatus;
import java.time.Instant;
import java.util.Objects;

public final class InterviewSubmission {

	private final String submissionId;
	private final int questionNo;
	private final String payloadDigest;
	private final Instant acceptedAt;
	private InterviewSubmissionStatus status = InterviewSubmissionStatus.ACCEPTED;
	private Instant updatedAt;
	private String errorCode;
	private String errorMessage;

	public InterviewSubmission(
			String submissionId,
			int questionNo,
			String payloadDigest,
			Instant acceptedAt) {
		if (submissionId == null || submissionId.isBlank()) {
			throw new IllegalArgumentException("submissionId must not be blank");
		}
		if (questionNo < 1) {
			throw new IllegalArgumentException("questionNo must be positive");
		}
		if (payloadDigest == null || payloadDigest.isBlank()) {
			throw new IllegalArgumentException("payloadDigest must not be blank");
		}
		this.submissionId = submissionId.trim();
		this.questionNo = questionNo;
		this.payloadDigest = payloadDigest.trim();
		this.acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt");
		this.updatedAt = acceptedAt;
	}

	public synchronized void markProcessing(Instant at) {
		requireStatus(InterviewSubmissionStatus.ACCEPTED,
				"submission must be accepted before processing");
		validateTime(at);
		status = InterviewSubmissionStatus.PROCESSING;
		updatedAt = at;
	}

	public synchronized void markCompleted(Instant at) {
		requireStatus(InterviewSubmissionStatus.PROCESSING,
				"submission must be processing before completion");
		validateTime(at);
		status = InterviewSubmissionStatus.COMPLETED;
		updatedAt = at;
		errorCode = null;
		errorMessage = null;
	}

	public synchronized void markFailed(
			boolean retryable,
			String errorCode,
			String errorMessage,
			Instant at) {
		requireStatus(InterviewSubmissionStatus.PROCESSING,
				"submission must be processing before failure");
		String requiredCode = requireText(errorCode, "errorCode");
		String requiredMessage = requireText(errorMessage, "errorMessage");
		validateTime(at);
		status = retryable
				? InterviewSubmissionStatus.FAILED_RETRYABLE
				: InterviewSubmissionStatus.FAILED_TERMINAL;
		this.errorCode = requiredCode;
		this.errorMessage = requiredMessage;
		updatedAt = at;
	}

	public synchronized boolean markTimedOut(
			boolean retryable,
			String errorCode,
			String errorMessage,
			Instant at) {
		if (!status.isInFlight()) {
			return false;
		}
		String requiredCode = requireText(errorCode, "errorCode");
		String requiredMessage = requireText(errorMessage, "errorMessage");
		validateTime(at);
		status = retryable
				? InterviewSubmissionStatus.FAILED_RETRYABLE
				: InterviewSubmissionStatus.FAILED_TERMINAL;
		this.errorCode = requiredCode;
		this.errorMessage = requiredMessage;
		updatedAt = at;
		return true;
	}

	public synchronized void retry(Instant at) {
		requireStatus(InterviewSubmissionStatus.FAILED_RETRYABLE,
				"only retryable failures can be retried");
		validateTime(at);
		status = InterviewSubmissionStatus.ACCEPTED;
		updatedAt = at;
		errorCode = null;
		errorMessage = null;
	}

	public String submissionId() { return submissionId; }
	public int questionNo() { return questionNo; }
	public String payloadDigest() { return payloadDigest; }
	public Instant acceptedAt() { return acceptedAt; }
	public synchronized InterviewSubmissionStatus status() { return status; }
	public synchronized Instant updatedAt() { return updatedAt; }
	public synchronized String errorCode() { return errorCode; }
	public synchronized String errorMessage() { return errorMessage; }

	private void requireStatus(InterviewSubmissionStatus expected, String message) {
		if (status != expected) {
			throw new IllegalStateException(message);
		}
	}

	private void validateTime(Instant at) {
		Objects.requireNonNull(at, "at");
		if (at.isBefore(updatedAt)) {
			throw new IllegalArgumentException("submission time must not move backwards");
		}
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value.trim();
	}
}
