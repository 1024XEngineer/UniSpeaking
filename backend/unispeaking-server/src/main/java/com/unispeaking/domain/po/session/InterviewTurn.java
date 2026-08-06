package com.unispeaking.domain.po.session;

import com.unispeaking.domain.dto.evaluation.SpeechEvaluationResult;
import com.unispeaking.domain.vo.scene.InterviewQuestionPrompt;
import java.util.Objects;

public final class InterviewTurn {

	private final InterviewQuestionPrompt question;
	private String aiAudioBase64;
	private String answerObjectKey;
	private String transcript;
	private int validWordCount;
	private SpeechEvaluationResult speechEvaluation;

	public InterviewTurn(InterviewQuestionPrompt question) {
		this.question = Objects.requireNonNull(question, "question");
	}

	public synchronized void setAiAudioBase64(String value) {
		aiAudioBase64 = value == null || value.isBlank() ? null : value;
	}

	public synchronized void setAnswerObjectKey(String value) {
		answerObjectKey = value == null || value.isBlank() ? null : value;
	}

	public synchronized void setTranscript(String value) {
		transcript = value == null || value.isBlank() ? null : value.trim();
	}

	public synchronized void setValidWordCount(int value) {
		if (value < 0) {
			throw new IllegalArgumentException("validWordCount must not be negative");
		}
		validWordCount = value;
	}

	public synchronized void setSpeechEvaluation(SpeechEvaluationResult value) {
		speechEvaluation = value;
	}

	public InterviewQuestionPrompt question() { return question; }
	public synchronized String aiAudioBase64() { return aiAudioBase64; }
	public synchronized String answerObjectKey() { return answerObjectKey; }
	public synchronized String transcript() { return transcript; }
	public synchronized int validWordCount() { return validWordCount; }
	public synchronized SpeechEvaluationResult speechEvaluation() { return speechEvaluation; }
}
