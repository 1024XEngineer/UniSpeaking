package com.unispeaking.service.evaluation.internal.mapper;

import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import com.unispeaking.domain.dto.evaluation.PhonemeScore;
import com.unispeaking.domain.dto.evaluation.WordPronunciationScore;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtterance;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 将持久化的单轮记录转换为最新会话评分查询结果。
 */
@Component
public final class ConversationEvaluationResultMapper {

	public DialogueEvaluationResult map(
			UUID expectedSessionId,
			List<PracticeResultUtterance> utterances) {
		try {
			UUID sessionId = Objects.requireNonNull(
					expectedSessionId,
					"expectedSessionId must not be null");
			List<PracticeResultUtterance> rows = List.copyOf(
					Objects.requireNonNull(
							utterances,
							"utterances must not be null"));
			List<Message> dialogue = new ArrayList<>();
			List<DialogueTurnEvaluationResult> evaluations = new ArrayList<>();
			for (PracticeResultUtterance utterance : rows) {
				PracticeResultUtterance row = Objects.requireNonNull(
						utterance,
						"utterance must not be null");
				if (!sessionId.equals(row.sessionId())) {
					throw new IllegalArgumentException("session mismatch");
				}
				if (row.aiText() != null) {
					dialogue.add(new Message(0, row.aiText(), null));
				}
				dialogue.add(new Message(1, row.transcript(), null));
				evaluations.add(mapTurn(row));
			}
			return new DialogueEvaluationResult(dialogue, evaluations);
		}
		catch (RuntimeException exception) {
			throw new EvaluationException(EvaluationErrorCode.PERSISTENCE_FAILED);
		}
	}

	private DialogueTurnEvaluationResult mapTurn(
			PracticeResultUtterance utterance) {
		return new DialogueTurnEvaluationResult(
				utterance.utteranceNo(),
				utterance.transcript(),
				utterance.overallScore(),
				utterance.rhythmScore(),
				utterance.toneScore(),
				utterance.integrityScore(),
				utterance.pronunciationScore(),
				utterance.fluencyScore(),
				utterance.feedbackSummary(),
				utterance.suggestedExpression(),
				utterance.words().stream().map(this::mapWord).toList());
	}

	private WordPronunciationScore mapWord(
			PracticeResultUtterance.Word word) {
		return new WordPronunciationScore(
				word.text(),
				word.pronunciationScore(),
				word.phonemes().stream().map(this::mapPhoneme).toList());
	}

	private PhonemeScore mapPhoneme(
			PracticeResultUtterance.Phoneme phoneme) {
		return new PhonemeScore(
				phoneme.expectedPhoneme(),
				phoneme.actualPhoneme(),
				phoneme.pronunciationScore());
	}
}
