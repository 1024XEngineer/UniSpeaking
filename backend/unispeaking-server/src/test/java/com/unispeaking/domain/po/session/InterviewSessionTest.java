package com.unispeaking.domain.po.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.InterviewEndReason;
import com.unispeaking.domain.vo.scene.InterviewPlannedQuestion;
import com.unispeaking.domain.vo.scene.InterviewQuestionPlan;
import com.unispeaking.domain.vo.scene.InterviewSubmissionStatus;
import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InterviewSessionTest {

	private static final Instant NOW = Instant.parse("2026-08-05T04:00:00Z");

	@Test
	void ownsInterviewRuntimeAndProtectsSnapshots() {
		InterviewSession session = new InterviewSession(
				"session_1", "user_1", "interview_1", plan());
		session.recordMainQuestion(null);
		Instant touchTime = session.getCreatedAt().plusSeconds(10);
		session.touch(touchTime);

		InterviewSubmission submission = new InterviewSubmission(
				"submission_1", 1, "digest_1", NOW);
		session.registerSubmission(submission);

		assertEquals("interview_1", session.getSceneId());
		assertEquals(SessionStatus.CREATED, session.getStatus());
		assertEquals(touchTime, session.lastSeen());
		assertEquals(1, session.turns().size());
		assertEquals(1, session.submissions().size());
		assertTrue(session.hasInFlightSubmissions());
		submission.markProcessing(NOW.plusSeconds(1));
		submission.markCompleted(NOW.plusSeconds(2));
		assertFalse(session.hasInFlightSubmissions());
	}

	@Test
	void endRequestStopsNewSubmissionsUntilInsufficientDataIsConfirmedOrCleared() {
		InterviewSession session = new InterviewSession(
				"session_1", "user_1", "interview_1", plan());
		session.requestEnd();
		assertTrue(session.endRequested());
		assertFalse(session.acceptingSubmissions());

		session.requireConfirmation();
		assertTrue(session.confirmationRequired());
		assertFalse(session.endRequested());
		assertTrue(session.acceptingSubmissions());

		session.requestEnd();
		session.confirmInsufficientData();
		assertFalse(session.acceptingSubmissions());
		assertTrue(session.insufficientDataConfirmed());
		assertThrows(IllegalStateException.class, session::clearEndRequest);
}

	@Test
	void terminalStateTransitionsRemainSynchronizedAndFlowCleanupIsIndependent() {
		InterviewSession session = new InterviewSession(
				"session_1", "user_1", "interview_1", plan());
		session.activate();
		session.complete(NOW);
		session.markFlowCleanupPending();

		assertEquals(SessionStatus.COMPLETED, session.getStatus());
		assertTrue(session.flowCleanupPending());
		session.clearFlowCleanupPending();
		assertFalse(session.flowCleanupPending());
		session.end(InterviewEndReason.USER_ENDED);
	}

	@Test
	void rejectsSubmissionAfterEndAndOnlyAcceptsCurrentRecordedQuestion() {
		InterviewSession session = new InterviewSession(
				"session_1", "user_1", "interview_1", plan());
		session.recordMainQuestion(null);
		InterviewSubmission submission = new InterviewSubmission(
				"submission_1", 1, "digest_1", NOW);
		session.registerSubmission(submission);
		assertThrows(
				IllegalStateException.class,
				() -> session.registerSubmission(new InterviewSubmission(
						"submission_1", 1, "digest_2", NOW)));

		InterviewSession secondSession = new InterviewSession(
				"session_2", "user_1", "interview_2", plan());
		secondSession.recordMainQuestion(null);
		assertThrows(
				IllegalArgumentException.class,
				() -> secondSession.registerSubmission(new InterviewSubmission(
						"submission_3", 99, "digest_3", NOW)));
		secondSession.recordFollowUp("Follow up");
		assertThrows(
				IllegalArgumentException.class,
				() -> secondSession.registerSubmission(new InterviewSubmission(
						"submission_old", 1, "digest_old", NOW)));
		secondSession.requestEnd();
		assertThrows(
				IllegalStateException.class,
				() -> secondSession.registerSubmission(new InterviewSubmission(
						"submission_4", 1, "digest_4", NOW)));
	}

	@Test
	void endClosesSubmissionsAndTouchNeverMovesLastSeenBackwards() {
		InterviewSession session = new InterviewSession(
				"session_1", "user_1", "interview_1", plan());
		session.recordMainQuestion(null);
		Instant touchTime = session.getCreatedAt().plusSeconds(10);
		session.touch(touchTime);
		session.touch(NOW);
		session.end(InterviewEndReason.USER_ENDED);

		assertEquals(touchTime, session.lastSeen());
		assertThrows(
				IllegalStateException.class,
				() -> session.registerSubmission(new InterviewSubmission(
						"submission_1", 1, "digest_1", NOW.plusSeconds(11))));
	}

	@Test
	void allowsOnlyOneConcurrentSubmissionForTheCurrentQuestion() throws Exception {
		InterviewSession session = new InterviewSession(
				"session_1", "user_1", "interview_1", plan());
		session.recordMainQuestion(null);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger accepted = new AtomicInteger();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (int index = 1; index <= 2; index++) {
				int submissionNo = index;
				executor.submit(() -> {
					ready.countDown();
					try {
						start.await();
						session.registerSubmission(new InterviewSubmission(
								"submission_" + submissionNo,
								1,
								"digest_" + submissionNo,
								Instant.now()));
						accepted.incrementAndGet();
					}
					catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
					}
					catch (RuntimeException ignored) {
						// The second reservation is expected to lose the session lock.
					}
				});
			}
			ready.await();
			start.countDown();
		}
		finally {
			executor.shutdown();
		}
		while (!executor.isTerminated()) {
			Thread.yield();
		}
		assertEquals(1, accepted.get());
		assertEquals(1, session.submissions().size());
	}

	@Test
	void naturalPlanCompletionClosesTheSubmissionGate() {
		InterviewSession session = new InterviewSession(
				"session_1", "user_1", "interview_1", plan());
		for (int mainNo = 1; mainNo <= 5; mainNo++) {
			session.recordMainQuestion("Main " + mainNo);
			if (mainNo < 5) {
				session.moveToNextMainQuestion();
			}
		}
		session.moveToNextMainQuestion();

		assertFalse(session.acceptingSubmissions());
		assertThrows(
				IllegalStateException.class,
				() -> session.registerSubmission(new InterviewSubmission(
						"submission_1", 5, "digest_1", NOW.plusSeconds(1))));
	}

	@Test
	void retryableFailureBlocksANewSubmissionIdButTerminalFailureAllowsIt() {
		InterviewSession retryableSession = new InterviewSession(
				"session_1", "user_1", "interview_1", plan());
		retryableSession.recordMainQuestion(null);
		InterviewSubmission retryable = new InterviewSubmission(
				"submission_1", 1, "digest_1", NOW);
		retryableSession.registerSubmission(retryable);
		retryable.markProcessing(NOW.plusSeconds(1));
		retryable.markFailed(true, "ASR_TIMEOUT", "超时", NOW.plusSeconds(2));
		assertThrows(
				IllegalStateException.class,
				() -> retryableSession.registerSubmission(new InterviewSubmission(
						"submission_2", 1, "digest_2", NOW.plusSeconds(3))));

		InterviewSession terminalSession = new InterviewSession(
				"session_2", "user_1", "interview_2", plan());
		terminalSession.recordMainQuestion(null);
		InterviewSubmission terminal = new InterviewSubmission(
				"submission_1", 1, "digest_1", NOW);
		terminalSession.registerSubmission(terminal);
		terminal.markProcessing(NOW.plusSeconds(1));
		terminal.markFailed(false, "AUDIO_INVALID", "无效", NOW.plusSeconds(2));
		terminalSession.registerSubmission(new InterviewSubmission(
				"submission_2", 1, "digest_2", NOW.plusSeconds(3)));
		assertEquals(2, terminalSession.submissions().size());
	}

	private static InterviewQuestionPlan plan() {
		return new InterviewQuestionPlan(
				InterviewDifficulty.STANDARD,
				java.util.stream.IntStream.rangeClosed(1, 5)
						.mapToObj(no -> new InterviewPlannedQuestion(no, "Main " + no, 1))
						.toList());
	}
}
