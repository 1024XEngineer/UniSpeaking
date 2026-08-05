package com.unispeaking.domain.vo.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InterviewProgress {

	private final InterviewQuestionPlan plan;
	private final List<InterviewQuestionPrompt> actualQuestions = new ArrayList<>();
	private int currentMainQuestionNo = 1;
	private int followUpCount;
	private int nextQuestionNo = 1;
	private boolean currentMainQuestionRecorded;
	private InterviewEndReason endReason;

	public InterviewProgress(InterviewQuestionPlan plan) {
		this.plan = Objects.requireNonNull(plan, "plan");
	}

	public synchronized InterviewQuestionPrompt recordMainQuestion(String questionText) {
		ensureOpen();
		if (currentMainQuestionRecorded) {
			throw new IllegalStateException("current main question is already recorded");
		}
		InterviewPlannedQuestion planned = plan.mainQuestion(currentMainQuestionNo);
		InterviewQuestionPrompt prompt = record(new InterviewQuestionPrompt(
				nextQuestionNo,
				InterviewQuestionType.MAIN,
				questionText == null ? planned.questionText() : questionText));
		nextQuestionNo++;
		currentMainQuestionRecorded = true;
		return prompt;
	}

	public synchronized InterviewQuestionPrompt recordFollowUp(String questionText) {
		ensureOpen();
		if (!currentMainQuestionRecorded) {
			throw new IllegalStateException("main question must be recorded first");
		}
		if (followUpCount >= plan.maxFollowUps()) {
			throw new IllegalStateException("follow-up limit has been reached");
		}
		InterviewQuestionPrompt prompt = record(new InterviewQuestionPrompt(
				nextQuestionNo,
				InterviewQuestionType.FOLLOW_UP,
				questionText));
		nextQuestionNo++;
		followUpCount++;
		return prompt;
	}

	public synchronized void moveToNextMainQuestion() {
		ensureOpen();
		if (!currentMainQuestionRecorded) {
			throw new IllegalStateException("current main question has not been recorded");
		}
		if (currentMainQuestionNo == plan.mainQuestions().size()) {
			endReason = InterviewEndReason.PLAN_COMPLETED;
			return;
		}
		currentMainQuestionNo++;
		followUpCount = 0;
		currentMainQuestionRecorded = false;
	}

	public synchronized void end(InterviewEndReason reason) {
		Objects.requireNonNull(reason, "reason");
		if (reason == InterviewEndReason.PLAN_COMPLETED
				&& (currentMainQuestionNo != plan.mainQuestions().size()
						|| !currentMainQuestionRecorded)) {
			throw new IllegalStateException("question plan is not complete");
		}
		if (endReason != null && endReason != reason) {
			throw new IllegalStateException("interview already has another end reason");
		}
		endReason = reason;
	}

	public synchronized boolean isComplete() {
		return endReason == InterviewEndReason.PLAN_COMPLETED;
	}

	public synchronized InterviewQuestionPlan plan() {
		return plan;
	}

	public synchronized int currentMainQuestionNo() {
		return currentMainQuestionNo;
	}

	public synchronized int followUpCount() {
		return followUpCount;
	}

	public synchronized int nextQuestionNo() {
		return nextQuestionNo;
	}

	public synchronized boolean currentMainQuestionRecorded() {
		return currentMainQuestionRecorded;
	}

	public synchronized int currentQuestionNo() {
		return endReason == null && currentMainQuestionRecorded && !actualQuestions.isEmpty()
				? actualQuestions.getLast().questionNo()
				: -1;
	}

	public synchronized InterviewEndReason endReason() {
		return endReason;
	}

	public synchronized List<InterviewQuestionPrompt> actualQuestions() {
		return List.copyOf(actualQuestions);
	}

	private InterviewQuestionPrompt record(InterviewQuestionPrompt prompt) {
		actualQuestions.add(prompt);
		return prompt;
	}

	private void ensureOpen() {
		if (endReason != null) {
			throw new IllegalStateException("interview progress is terminal");
		}
	}
}
