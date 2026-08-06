package com.unispeaking.domain.po.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.vo.scene.InterviewSubmissionStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InterviewSubmissionTest {

	private static final Instant NOW = Instant.parse("2026-08-05T04:00:00Z");

	@Test
	void followsProcessingAndRetryableFailureLifecycle() {
		InterviewSubmission submission = new InterviewSubmission(
				"submission_1", 1, "digest_1", NOW);

		submission.markProcessing(NOW.plusSeconds(1));
		submission.markFailed(
				true,
				"ASR_TIMEOUT",
				"语音识别暂时超时",
				NOW.plusSeconds(2));
		submission.retry(NOW.plusSeconds(3));
		submission.markProcessing(NOW.plusSeconds(4));
		submission.markCompleted(NOW.plusSeconds(5));

		assertEquals(InterviewSubmissionStatus.COMPLETED, submission.status());
		assertEquals(NOW.plusSeconds(5), submission.updatedAt());
		assertEquals(null, submission.errorCode());
	}

	@Test
	void terminalFailureCannotBeRetriedOrCompleted() {
		InterviewSubmission submission = new InterviewSubmission(
				"submission_1", 1, "digest_1", NOW);
		submission.markProcessing(NOW.plusSeconds(1));
		submission.markFailed(
				false,
				"AUDIO_INVALID",
				"音频格式无效",
				NOW.plusSeconds(2));

		assertEquals(InterviewSubmissionStatus.FAILED_TERMINAL, submission.status());
		assertThrows(
				IllegalStateException.class,
				() -> submission.retry(NOW.plusSeconds(3)));
		assertThrows(
				IllegalStateException.class,
				() -> submission.markCompleted(NOW.plusSeconds(3)));
	}

	@Test
	void invalidFailureDoesNotMutateStateAndTimeCannotMoveBackwards() {
		InterviewSubmission submission = new InterviewSubmission(
				"submission_1", 1, "digest_1", NOW);
		submission.markProcessing(NOW.plusSeconds(1));

		assertThrows(
				IllegalArgumentException.class,
				() -> submission.markFailed(
						true, " ", "message", NOW.plusSeconds(2)));
		assertEquals(InterviewSubmissionStatus.PROCESSING, submission.status());
		assertThrows(
				IllegalArgumentException.class,
				() -> submission.markCompleted(NOW));
		assertEquals(InterviewSubmissionStatus.PROCESSING, submission.status());
	}

	@Test
	void timeoutCanFailAcceptedOrProcessingSubmissionButNotCompletedSubmission() {
		InterviewSubmission accepted = new InterviewSubmission(
				"submission_accepted", 1, "digest_1", NOW);
		InterviewSubmission processing = new InterviewSubmission(
				"submission_processing", 1, "digest_2", NOW);
		processing.markProcessing(NOW.plusSeconds(1));
		InterviewSubmission completed = new InterviewSubmission(
				"submission_completed", 1, "digest_3", NOW);
		completed.markProcessing(NOW.plusSeconds(1));
		completed.markCompleted(NOW.plusSeconds(2));

		assertTrue(accepted.markTimedOut(
				true, "TIMEOUT", "timeout", NOW.plusSeconds(3)));
		assertTrue(processing.markTimedOut(
				false, "TIMEOUT", "timeout", NOW.plusSeconds(3)));
		assertFalse(completed.markTimedOut(
				true, "TIMEOUT", "timeout", NOW.plusSeconds(3)));
		assertEquals(InterviewSubmissionStatus.FAILED_RETRYABLE, accepted.status());
		assertEquals(InterviewSubmissionStatus.FAILED_TERMINAL, processing.status());
		assertEquals(InterviewSubmissionStatus.COMPLETED, completed.status());
	}
}
