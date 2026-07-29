package com.unispeaking.service.evaluation;

import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationResponse;
import com.unispeaking.domain.dto.session.Message;
import java.util.List;

public interface EvaluationService {
	SentenceEvaluationResponse evaluateSentenceReading(
			String sentenceId,
			byte[] audio);

	DialogueTurnEvaluationResult evaluateDialogueTurn(
			DialogueTurnEvaluationCommand command);

	DialogueReportResult generateDialogueReport(
			String sessionId,
			List<Message> dialogue);

	DialogueEvaluationResult getDialogueEvaluation(String sessionId);
}
