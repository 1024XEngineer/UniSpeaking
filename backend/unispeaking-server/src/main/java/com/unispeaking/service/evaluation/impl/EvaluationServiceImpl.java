package com.unispeaking.service.evaluation.impl;

import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import com.unispeaking.domain.dto.evaluation.PhonemeScore;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationResponse;
import com.unispeaking.domain.dto.evaluation.WordPronunciationScore;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.infrastructure.persistence.evaluation.asset.AssetSentenceEvaluationTarget;
import com.unispeaking.infrastructure.persistence.evaluation.repository.AssetSentenceEvaluationRepository;
import com.unispeaking.infrastructure.persistence.evaluation.repository.PracticeResultUtteranceRepository;
import com.unispeaking.infrastructure.persistence.evaluation.repository.PracticeSessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.evaluation.session.PracticeSessionEvaluationContext;
import com.unispeaking.infrastructure.persistence.evaluation.transaction.ConversationReportPersistenceCoordinator;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtterance;
import com.unispeaking.service.evaluation.EvaluationService;
import com.unispeaking.service.evaluation.internal.audio.PcmWavValidator;
import com.unispeaking.service.evaluation.internal.calculation.ConversationScoreCalculation;
import com.unispeaking.service.evaluation.internal.calculation.ConversationScoreCalculator;
import com.unispeaking.service.evaluation.internal.calculation.TurnScoreContribution;
import com.unispeaking.service.evaluation.internal.calculation.TurnSpeechScoreCalculator;
import com.unispeaking.service.evaluation.internal.client.EvaluationLlmClient;
import com.unispeaking.service.evaluation.internal.client.PronunciationAssessmentClient;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import com.unispeaking.service.evaluation.internal.handler.ConversationEvaluationQueryHandler;
import com.unispeaking.service.evaluation.internal.model.ConversationLanguageAssessment;
import com.unispeaking.service.evaluation.internal.model.EndingTone;
import com.unispeaking.service.evaluation.internal.model.PronunciationAssessmentResult;
import com.unispeaking.service.evaluation.internal.model.PronunciationPhonemeResult;
import com.unispeaking.service.evaluation.internal.model.PronunciationWordResult;
import com.unispeaking.service.evaluation.internal.model.TurnLanguageFeedback;
import com.unispeaking.service.evaluation.internal.model.WordReadStatus;
import com.unispeaking.service.evaluation.internal.result.TooShortEvaluationPolicy;
import com.unispeaking.service.evaluation.internal.text.EnglishWordCounter;
import com.unispeaking.service.prompt.evaluation.DialogueTurnEvaluationHistory;
import com.unispeaking.service.prompt.evaluation.DialogueTurnEvaluationPromptInput;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 最新评分接口的默认实现。
 */
@Service
@Profile("!test")
public class EvaluationServiceImpl implements EvaluationService {

	private static final BigDecimal SENTENCE_PASS_SCORE = new BigDecimal("60");

	private final PronunciationAssessmentClient pronunciationClient;
	private final EvaluationLlmClient llmClient;
	private final AssetSentenceEvaluationRepository sentenceRepository;
	private final PracticeSessionEvaluationRepository sessionRepository;
	private final PracticeResultUtteranceRepository utteranceRepository;
	private final ConversationReportPersistenceCoordinator reportPersistence;
	private final ConversationEvaluationQueryHandler queryHandler;

	public EvaluationServiceImpl(
			PronunciationAssessmentClient pronunciationClient,
			EvaluationLlmClient llmClient,
			AssetSentenceEvaluationRepository sentenceRepository,
			PracticeSessionEvaluationRepository sessionRepository,
			PracticeResultUtteranceRepository utteranceRepository,
			ConversationReportPersistenceCoordinator reportPersistence,
			ConversationEvaluationQueryHandler queryHandler) {
		this.pronunciationClient = Objects.requireNonNull(
				pronunciationClient,
				"pronunciationClient must not be null");
		this.llmClient = Objects.requireNonNull(llmClient, "llmClient must not be null");
		this.sentenceRepository = Objects.requireNonNull(
				sentenceRepository,
				"sentenceRepository must not be null");
		this.sessionRepository = Objects.requireNonNull(
				sessionRepository,
				"sessionRepository must not be null");
		this.utteranceRepository = Objects.requireNonNull(
				utteranceRepository,
				"utteranceRepository must not be null");
		this.reportPersistence = Objects.requireNonNull(
				reportPersistence,
				"reportPersistence must not be null");
		this.queryHandler = Objects.requireNonNull(
				queryHandler,
				"queryHandler must not be null");
	}

	@Override
	public SentenceEvaluationResponse evaluateSentenceReading(
			String sentenceId,
			byte[] audio) {
		UUID id = parseUuid(sentenceId);
		PcmWavValidator.validate(audio);
		AssetSentenceEvaluationTarget target = sentenceRepository
				.findEvaluationTarget(id)
				.orElseThrow(() -> new EvaluationException(
						EvaluationErrorCode.SENTENCE_NOT_FOUND));
		PronunciationAssessmentResult assessment =
				pronunciationClient.evaluate(target.referenceText(), audio);
		sentenceRepository.replaceReadingDetails(id, assessment);
		return new SentenceEvaluationResponse(
				assessment.overallScore(),
				assessment.overallScore().compareTo(SENTENCE_PASS_SCORE) >= 0,
				mapWords(assessment.words()));
	}

	@Override
	public DialogueTurnEvaluationResult evaluateDialogueTurn(
			DialogueTurnEvaluationCommand command) {
		if (command == null || command.turnNo() < 1) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		UUID sessionId = parseUuid(command.sessionId());
		PracticeSessionEvaluationContext context = requireSession(sessionId);
		EnglishWordCounter.Analysis text =
				EnglishWordCounter.analyze(command.transcript());
		if (text.classification() == EnglishWordCounter.Classification.EMPTY) {
			throw new EvaluationException(EvaluationErrorCode.TRANSCRIPT_REQUIRED);
		}
		if (text.classification() == EnglishWordCounter.Classification.TOO_SHORT) {
			DialogueTurnEvaluationResult result =
					TooShortEvaluationPolicy.createResult(
							command.turnNo(),
							command.transcript());
			utteranceRepository.upsert(toTooShortRow(sessionId, result));
			return result;
		}

		PcmWavValidator.validate(command.audio());
		PronunciationAssessmentResult assessment =
				pronunciationClient.evaluate(command.transcript(), command.audio());
		TurnSpeechScoreCalculator.calculate(assessment);
		TurnLanguageFeedback feedback = llmClient.assessTurn(
				buildTurnPrompt(
						context,
						command.turnNo(),
						command.transcript()));
		DialogueTurnEvaluationResult result = new DialogueTurnEvaluationResult(
				command.turnNo(),
				command.transcript(),
				assessment.overallScore(),
				assessment.rhythmScore(),
				assessment.toneScore(),
				assessment.integrityScore(),
				assessment.pronunciationScore(),
				assessment.fluencyScore(),
				feedback.feedbackSummary(),
				feedback.suggestedExpression(),
				mapWords(assessment.words()));
		utteranceRepository.upsert(
				toPersistedTurn(sessionId, result, assessment));
		return result;
	}

	@Override
	public DialogueReportResult generateDialogueReport(
			String sessionId,
			List<Message> dialogue) {
		UUID id = parseUuid(sessionId);
		requireSession(id);
		List<Message> immutableDialogue = validateDialogue(dialogue);
		List<PracticeResultUtterance> savedTurns =
				utteranceRepository.findAll(id);
		List<PracticeResultUtterance> scorableTurns = savedTurns.stream()
				.filter(turn -> !TooShortEvaluationPolicy.isTooShort(
						turn.overallScore(),
						turn.rhythmScore(),
						turn.toneScore(),
						turn.integrityScore(),
						turn.pronunciationScore(),
						turn.fluencyScore(),
						turn.feedbackSummary()))
				.sorted(Comparator.comparingInt(
						PracticeResultUtterance::utteranceNo))
				.toList();
		if (scorableTurns.isEmpty()) {
			throw new EvaluationException(
					EvaluationErrorCode.NO_SCORABLE_UTTERANCES);
		}

		ConversationLanguageAssessment language =
				llmClient.assessDialogue(immutableDialogue);
		List<TurnScoreContribution> contributions = scorableTurns.stream()
				.map(this::toContribution)
				.toList();
		ConversationScoreCalculation scores =
				ConversationScoreCalculator.calculate(contributions, language);
		DialogueReportResult report = new DialogueReportResult(
				scores.accuracyScore(),
				scores.fluencyScore(),
				scores.grammarScore(),
				scores.vocabularyScore(),
				scores.naturalnessScore(),
				scores.finalScore(),
				language.summary(),
				language.strengths(),
				language.improvements());

		persistDialogueContext(id, immutableDialogue, savedTurns);
		reportPersistence.save(id, report);
		return report;
	}

	@Override
	public DialogueEvaluationResult getDialogueEvaluation(String sessionId) {
		return queryHandler.handle(parseUuid(sessionId));
	}

	private PracticeSessionEvaluationContext requireSession(UUID sessionId) {
		return sessionRepository.findBySessionId(sessionId)
				.orElseThrow(() -> new EvaluationException(
						EvaluationErrorCode.SESSION_NOT_FOUND));
	}

	private DialogueTurnEvaluationPromptInput buildTurnPrompt(
			PracticeSessionEvaluationContext context,
			int turnNo,
			String transcript) {
		List<DialogueTurnEvaluationHistory> history =
				utteranceRepository.findBefore(context.sessionId(), turnNo)
						.stream()
						.map(turn -> new DialogueTurnEvaluationHistory(
								turn.utteranceNo(),
								turn.aiText(),
								turn.transcript()))
						.toList();
		return new DialogueTurnEvaluationPromptInput(
				context.practiceMode(),
				context.background(),
				context.aiRole(),
				context.userRole(),
				context.learningGoal(),
				history,
				null,
				transcript);
	}

	private TurnScoreContribution toContribution(
			PracticeResultUtterance utterance) {
		return TurnSpeechScoreCalculator.calculate(
				toAssessment(utterance)).toContribution();
	}

	private PronunciationAssessmentResult toAssessment(
			PracticeResultUtterance utterance) {
		List<PronunciationWordResult> words = utterance.words().stream()
				.map(word -> new PronunciationWordResult(
						word.index(),
						word.text(),
						WordReadStatus.NORMAL,
						word.pronunciationScore(),
						word.pronunciationScore(),
						null,
						word.phonemes().stream()
								.map(phoneme -> new PronunciationPhonemeResult(
										phoneme.index(),
										phoneme.expectedPhoneme(),
										phoneme.actualPhoneme(),
										phoneme.pronunciationScore(),
										phoneme.startPosition(),
										phoneme.endPosition()))
								.toList()))
				.toList();
		return new PronunciationAssessmentResult(
				utterance.overallScore(),
				utterance.rhythmScore(),
				utterance.toneScore(),
				utterance.integrityScore(),
				utterance.pronunciationScore(),
				utterance.fluencyScore(),
				EndingTone.UNKNOWN,
				words);
	}

	private PracticeResultUtterance toTooShortRow(
			UUID sessionId,
			DialogueTurnEvaluationResult result) {
		return new PracticeResultUtterance(
				sessionId,
				result.turnNo(),
				result.transcript(),
				null,
				result.overallScore(),
				result.rhythmScore(),
				result.toneScore(),
				result.integrityScore(),
				result.pronunciationScore(),
				result.fluencyScore(),
				result.feedbackSummary(),
				result.suggestedExpression(),
				List.of());
	}

	private PracticeResultUtterance toPersistedTurn(
			UUID sessionId,
			DialogueTurnEvaluationResult result,
			PronunciationAssessmentResult assessment) {
		List<PracticeResultUtterance.Word> words = assessment.words().stream()
				.filter(word -> word.phonemes().stream().anyMatch(
						phoneme -> phoneme.startPosition() >= 0
								&& phoneme.endPosition()
										> phoneme.startPosition()))
				.map(word -> new PracticeResultUtterance.Word(
						word.index(),
						word.word(),
						word.pronunciationScore(),
						word.phonemes().stream()
								.filter(phoneme ->
										phoneme.startPosition() >= 0
												&& phoneme.endPosition()
														> phoneme.startPosition())
								.map(phoneme -> new PracticeResultUtterance.Phoneme(
										phoneme.index(),
										phoneme.expectedPhoneme(),
										phoneme.actualPhoneme(),
										phoneme.pronunciationScore(),
										phoneme.startPosition(),
										phoneme.endPosition()))
								.toList()))
				.toList();
		return new PracticeResultUtterance(
				sessionId,
				result.turnNo(),
				result.transcript(),
				null,
				result.overallScore(),
				result.rhythmScore(),
				result.toneScore(),
				result.integrityScore(),
				result.pronunciationScore(),
				result.fluencyScore(),
				result.feedbackSummary(),
				result.suggestedExpression(),
				words);
	}

	private void persistDialogueContext(
			UUID sessionId,
			List<Message> dialogue,
			List<PracticeResultUtterance> savedTurns) {
		Map<Integer, PracticeResultUtterance> byTurn = new HashMap<>();
		for (PracticeResultUtterance turn : savedTurns) {
			byTurn.put(turn.utteranceNo(), turn);
		}

		int learnerTurn = 0;
		String latestAiText = null;
		for (Message message : dialogue) {
			if (message.owner() == 0) {
				latestAiText = message.content();
				continue;
			}
			learnerTurn++;
			PracticeResultUtterance saved = byTurn.get(learnerTurn);
			if (saved == null) {
				throw new EvaluationException(
						EvaluationErrorCode.RESULT_INCOMPLETE);
			}
			utteranceRepository.upsert(new PracticeResultUtterance(
					sessionId,
					saved.utteranceNo(),
					saved.transcript(),
					latestAiText,
					saved.overallScore(),
					saved.rhythmScore(),
					saved.toneScore(),
					saved.integrityScore(),
					saved.pronunciationScore(),
					saved.fluencyScore(),
					saved.feedbackSummary(),
					saved.suggestedExpression(),
					saved.words()));
			latestAiText = null;
		}
		if (learnerTurn != savedTurns.size()) {
			throw new EvaluationException(EvaluationErrorCode.RESULT_INCOMPLETE);
		}
	}

	private List<Message> validateDialogue(List<Message> dialogue) {
		if (dialogue == null || dialogue.isEmpty()) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		List<Message> copy = List.copyOf(dialogue);
		boolean hasLearner = false;
		for (Message message : copy) {
			if (message == null
					|| message.owner() == null
					|| message.content() == null
					|| message.content().isBlank()
					|| (message.owner() != 0 && message.owner() != 1)) {
				throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
			}
			hasLearner |= message.owner() == 1;
		}
		if (!hasLearner) {
			throw new EvaluationException(
					EvaluationErrorCode.NO_SCORABLE_UTTERANCES);
		}
		return copy;
	}

	private List<WordPronunciationScore> mapWords(
			List<PronunciationWordResult> words) {
		List<WordPronunciationScore> mapped = new ArrayList<>();
		for (PronunciationWordResult word : words) {
			List<PhonemeScore> phonemes = word.phonemes().stream()
					.map(phoneme -> new PhonemeScore(
							phoneme.expectedPhoneme(),
							phoneme.actualPhoneme(),
							phoneme.pronunciationScore()))
					.toList();
			mapped.add(new WordPronunciationScore(
					word.word(),
					word.pronunciationScore(),
					phonemes));
		}
		return List.copyOf(mapped);
	}

	private UUID parseUuid(String value) {
		if (value == null || value.isBlank()) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		try {
			return UUID.fromString(value.trim());
		}
		catch (IllegalArgumentException exception) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
	}
}
