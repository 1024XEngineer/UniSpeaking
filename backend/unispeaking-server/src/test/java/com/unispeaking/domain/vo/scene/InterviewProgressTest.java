package com.unispeaking.domain.vo.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewProgressTest {

	@Test
	void recordsActualQuestionSequenceAndResetsFollowUps() {
		InterviewProgress progress = new InterviewProgress(plan(InterviewDifficulty.STANDARD));

		InterviewQuestionPrompt main = progress.recordMainQuestion(null);
		InterviewQuestionPrompt followUp = progress.recordFollowUp("Why?");
		progress.moveToNextMainQuestion();
		InterviewQuestionPrompt next = progress.recordMainQuestion(null);

		assertEquals(1, main.questionNo());
		assertEquals(InterviewQuestionType.MAIN, main.questionType());
		assertEquals(2, followUp.questionNo());
		assertEquals(3, next.questionNo());
		assertEquals(0, progress.followUpCount());
		assertEquals(List.of(main, followUp, next), progress.actualQuestions());
	}

	@Test
	void enforcesLimitAndCompletesOnlyAfterTheFifthMainQuestion() {
		InterviewProgress progress = new InterviewProgress(plan(InterviewDifficulty.BASIC));
		for (int mainNo = 1; mainNo <= 5; mainNo++) {
			progress.recordMainQuestion("Main " + mainNo);
			if (mainNo < 5) {
				progress.moveToNextMainQuestion();
			}
		}

		progress.recordFollowUp("final follow-up");
		assertThrows(
				IllegalStateException.class,
				() -> progress.recordFollowUp("too late"));
		progress.moveToNextMainQuestion();
		assertEquals(InterviewEndReason.PLAN_COMPLETED, progress.endReason());
		assertThrows(
				IllegalStateException.class,
				() -> progress.recordMainQuestion("after completion"));
	}

	@Test
	void rejectsEndingPlanBeforeFifthQuestion() {
		InterviewProgress progress = new InterviewProgress(plan(InterviewDifficulty.STANDARD));
		assertThrows(
				IllegalStateException.class,
				() -> progress.end(InterviewEndReason.PLAN_COMPLETED));
		progress.end(InterviewEndReason.USER_ENDED);
		assertEquals(InterviewEndReason.USER_ENDED, progress.endReason());
	}

	@Test
	void rejectsSkippingOrRepeatingTheCurrentMainQuestion() {
		InterviewProgress progress = new InterviewProgress(plan(InterviewDifficulty.STANDARD));
		assertThrows(
				IllegalStateException.class,
				progress::moveToNextMainQuestion);
		progress.recordMainQuestion("Main 1");
		assertThrows(
				IllegalStateException.class,
				() -> progress.recordMainQuestion("Duplicate"));
	}

	@Test
	void cannotCompleteThePlanBeforeTheFifthQuestionIsActuallyPresented() {
		InterviewProgress progress = new InterviewProgress(plan(InterviewDifficulty.STANDARD));
		for (int mainNo = 1; mainNo < 5; mainNo++) {
			progress.recordMainQuestion("Main " + mainNo);
			progress.moveToNextMainQuestion();
		}
		assertThrows(
				IllegalStateException.class,
				() -> progress.end(InterviewEndReason.PLAN_COMPLETED));
		progress.recordMainQuestion("Main 5");
		progress.end(InterviewEndReason.PLAN_COMPLETED);
		assertEquals(InterviewEndReason.PLAN_COMPLETED, progress.endReason());
	}

	@Test
	void invalidQuestionTextDoesNotConsumeQuestionNumbersOrFollowUpQuota() {
		InterviewProgress progress = new InterviewProgress(plan(InterviewDifficulty.STANDARD));
		InterviewQuestionPrompt main = progress.recordMainQuestion("Main 1");
		assertEquals(1, main.questionNo());
		assertThrows(
				IllegalArgumentException.class,
				() -> progress.recordFollowUp(" "));
		assertEquals(0, progress.followUpCount());
		assertEquals(2, progress.nextQuestionNo());
	}

	private static InterviewQuestionPlan plan(InterviewDifficulty difficulty) {
		int limit = difficulty == InterviewDifficulty.CHALLENGE ? 2 : 1;
		return new InterviewQuestionPlan(
				difficulty,
				java.util.stream.IntStream.rangeClosed(1, 5)
						.mapToObj(no -> new InterviewPlannedQuestion(no, "Main " + no, limit))
						.toList());
	}
}
