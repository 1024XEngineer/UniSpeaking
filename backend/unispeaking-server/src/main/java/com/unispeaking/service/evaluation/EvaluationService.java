package com.unispeaking.service.evaluation;

import com.unispeaking.domain.dto.evaluation.ConversationReportRequest;
import com.unispeaking.domain.dto.evaluation.ConversationReportResponse;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationRequest;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResponse;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationRequest;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationResponse;

public interface EvaluationService {
	SentenceEvaluationResponse evaluateSentence(SentenceEvaluationRequest request);
	DialogueTurnEvaluationResponse evaluateDialogueTurn(DialogueTurnEvaluationRequest request);
	ConversationReportResponse generateConversationReport(ConversationReportRequest request);
}
