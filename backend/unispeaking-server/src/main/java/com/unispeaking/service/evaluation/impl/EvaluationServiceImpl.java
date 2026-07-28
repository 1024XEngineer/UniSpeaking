package com.unispeaking.service.evaluation.impl;

import com.unispeaking.domain.dto.evaluation.ConversationReportRequest;
import com.unispeaking.domain.dto.evaluation.ConversationReportResponse;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationRequest;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResponse;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationRequest;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationResponse;
import com.unispeaking.domain.vo.evaluation.FiveDimensionScore;
import com.unispeaking.service.evaluation.EvaluationService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EvaluationServiceImpl implements EvaluationService {

	@Override
	public SentenceEvaluationResponse evaluateSentence(SentenceEvaluationRequest request) {
		return new SentenceEvaluationResponse(request.contentId(), 0);
	}

	@Override
	public DialogueTurnEvaluationResponse evaluateDialogueTurn(DialogueTurnEvaluationRequest request) {
		return new DialogueTurnEvaluationResponse(request.turnId(), 0, 0, 0, 0, 0, 0, 0, 0);
	}

	@Override
	public ConversationReportResponse generateConversationReport(ConversationReportRequest request) {
		return new ConversationReportResponse(
				"report_" + UUID.randomUUID(),
				request.localSessionId(),
				0,
				new FiveDimensionScore(0, 0, 0, 0, 0),
				"");
	}
}
