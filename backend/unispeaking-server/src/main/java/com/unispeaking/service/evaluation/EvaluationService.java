package com.unispeaking.service.evaluation;

import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationHistoryItem;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationResponse;
import com.unispeaking.domain.dto.evaluation.SpeechEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.SpeechEvaluationResult;
import com.unispeaking.domain.dto.session.Message;
import java.math.BigDecimal;
import java.util.List;

public interface EvaluationService {
	SpeechEvaluationResult evaluateSpeech(SpeechEvaluationCommand command);

	SentenceEvaluationResponse evaluateSentenceReading(
			String sentenceId,
			byte[] audio);

	DialogueTurnEvaluationResult evaluateDialogueTurn(
			DialogueTurnEvaluationCommand command);

	DialogueTurnEvaluationResult evaluateIeltsTurn(
			String ieltsId,
			DialogueTurnEvaluationCommand command);

	IeltsEvaluationResult generateIeltsEvaluation(
			String ieltsId,
			String sessionId);

	BigDecimal getLatestIeltsEstimatedScore();

	List<IeltsEvaluationHistoryItem> getIeltsEvaluationHistory();

	DialogueReportResult generateDialogueReport(
			String sessionId,
			List<Message> dialogue);

	DialogueEvaluationResult getDialogueEvaluation(String sessionId);
}
