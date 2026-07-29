package com.unispeaking.service.evaluation.internal.handler;

import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.infrastructure.persistence.evaluation.repository.PracticeResultUtteranceRepository;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import com.unispeaking.service.evaluation.internal.mapper.ConversationEvaluationResultMapper;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 查询已经保存的对话单轮评分。
 */
@Component
@Profile("!test")
public final class ConversationEvaluationQueryHandler {

	private final PracticeResultUtteranceRepository utteranceRepository;
	private final ConversationEvaluationResultMapper resultMapper;

	public ConversationEvaluationQueryHandler(
			PracticeResultUtteranceRepository utteranceRepository,
			ConversationEvaluationResultMapper resultMapper) {
		this.utteranceRepository = Objects.requireNonNull(
				utteranceRepository,
				"utteranceRepository must not be null");
		this.resultMapper = Objects.requireNonNull(
				resultMapper,
				"resultMapper must not be null");
	}

	public DialogueEvaluationResult handle(UUID sessionId) {
		if (sessionId == null) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		var utterances = utteranceRepository.findAll(sessionId);
		if (utterances.isEmpty()) {
			throw new EvaluationException(EvaluationErrorCode.RESULT_NOT_FOUND);
		}
		return resultMapper.map(sessionId, utterances);
	}
}
