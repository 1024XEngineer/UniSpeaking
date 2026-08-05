package com.unispeaking.domain.po.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
