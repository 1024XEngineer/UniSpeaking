package com.unispeaking.service.evaluation.impl;

import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import com.unispeaking.domain.dto.evaluation.PhonemeScore;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationResponse;
import com.unispeaking.domain.dto.evaluation.WordPronunciationScore;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.infrastructure.persistence.entity.evaluation.CustomTurnEvaluation;
import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationWordDetail;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.service.evaluation.EvaluationService;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.common.evaluation.validation.PcmWavValidator;
import com.unispeaking.common.evaluation.calculation.ConversationScoreCalculation;
import com.unispeaking.common.evaluation.calculation.ConversationScoreCalculator;
import com.unispeaking.common.evaluation.calculation.TurnScoreContribution;
import com.unispeaking.common.evaluation.calculation.TurnSpeechScoreCalculator;
import com.unispeaking.infrastructure.evaluation.client.EvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.PronunciationAssessmentClient;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.evaluation.model.ConversationLanguageAssessment;
import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.TurnLanguageFeedback;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import com.unispeaking.common.evaluation.policy.TooShortEvaluationPolicy;
import com.unispeaking.common.evaluation.validation.EnglishWordCounter;
import com.unispeaking.common.prompt.evaluation.DialogueTurnEvaluationHistory;
import com.unispeaking.common.prompt.evaluation.DialogueTurnEvaluationPromptInput;
import com.unispeaking.common.evaluation.policy.UnavailableTurnEvaluationPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 最新评分接口的默认实现。
 */
@Service
@Profile("!test")
public class EvaluationServiceImpl implements EvaluationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			EvaluationServiceImpl.class);
	private static final BigDecimal SENTENCE_PASS_SCORE = new BigDecimal("80");

	private final PronunciationAssessmentClient pronunciationClient;
	private final EvaluationLlmClient llmClient;
	private final ActiveSessionRegistry activeSessionRegistry;
	private final SceneRepository sceneRepository;
	private final SessionMessageRepository sessionMessageRepository;
	private final TurnEvaluationRepository turnEvaluationRepository;
	private final SessionEvaluationRepository sessionEvaluationRepository;
	private final SceneSentenceReadingRepository sceneSentenceReadingRepository;
	private final AuthService authService;

	public EvaluationServiceImpl(
			PronunciationAssessmentClient pronunciationClient,
			EvaluationLlmClient llmClient,
			ActiveSessionRegistry activeSessionRegistry,
			SceneRepository sceneRepository,
			SessionMessageRepository sessionMessageRepository,
			TurnEvaluationRepository turnEvaluationRepository,
			SessionEvaluationRepository sessionEvaluationRepository,
			SceneSentenceReadingRepository sceneSentenceReadingRepository,
			AuthService authService) {
		this.pronunciationClient = Objects.requireNonNull(
				pronunciationClient,
				"pronunciationClient must not be null");
		this.llmClient = Objects.requireNonNull(llmClient, "llmClient must not be null");
		this.activeSessionRegistry = Objects.requireNonNull(
				activeSessionRegistry,
				"activeSessionRegistry must not be null");
		this.sceneRepository = Objects.requireNonNull(
				sceneRepository,
				"sceneRepository must not be null");
		this.sessionMessageRepository = Objects.requireNonNull(
				sessionMessageRepository,
				"sessionMessageRepository must not be null");
		this.turnEvaluationRepository = Objects.requireNonNull(
				turnEvaluationRepository,
				"turnEvaluationRepository must not be null");
		this.sessionEvaluationRepository = Objects.requireNonNull(
				sessionEvaluationRepository,
				"sessionEvaluationRepository must not be null");
		this.sceneSentenceReadingRepository = Objects.requireNonNull(
				sceneSentenceReadingRepository,
				"sceneSentenceReadingRepository must not be null");
		this.authService = Objects.requireNonNull(
				authService,
				"authService must not be null");
	}

	@Override
	public SentenceEvaluationResponse evaluateSentenceReading(
			String sentenceId,
			byte[] audio) {
		String sceneId = sceneSentenceReadingRepository
				.findSceneIdBySentenceId(sentenceId)
				.orElseThrow(() -> new EvaluationException(
						EvaluationErrorCode.SENTENCE_NOT_FOUND));
		CustomSceneDefinition scene = requireOwnedScene(sceneId);
		LearningContentItem sentence = scene.sentenceList().stream()
				.filter(item -> item.contentId().equals(sentenceId))
				.findFirst()
				.orElseThrow(() -> new EvaluationException(
						EvaluationErrorCode.SENTENCE_NOT_FOUND));
		PcmWavValidator.validate(audio);
		PronunciationAssessmentResult assessment =
				pronunciationClient.evaluate(sentence.englishText(), audio);
		sceneSentenceReadingRepository.saveAttempt(
				sceneId,
				sentence,
				assessment);
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
		AbstractSceneSession runtimeSession =
				findCustomRuntimeSession(command.sessionId());
		if (runtimeSession == null) {
			throw new EvaluationException(EvaluationErrorCode.SESSION_NOT_FOUND);
		}
		runtimeSession = requireOwnedCustomSession(
				runtimeSession.getSceneId(),
				command.sessionId());
		try {
			return evaluateCustomSceneTurn(runtimeSession, command);
		}
		catch (EvaluationException exception) {
			if (!isRecoverableTurnFailure(exception)) {
				throw exception;
			}
			DialogueTurnEvaluationResult evaluation =
					UnavailableTurnEvaluationPolicy.createResult(
							command.turnNo(),
							command.transcript());
			turnEvaluationRepository.upsert(toCustomTurn(
					runtimeSession,
					evaluation,
					List.of()));
			LOGGER.warn(
					"custom turn scoring unavailable sceneId={} sessionId={} "
							+ "turnNo={} code={}",
					runtimeSession.getSceneId(),
					runtimeSession.getId(),
					command.turnNo(),
					exception.errorCode().code());
			return evaluation;
		}
	}

	@Override
	public DialogueReportResult generateDialogueReport(
			String sessionId,
			List<Message> dialogue) {
		AbstractSceneSession customSession = findCustomRuntimeSession(sessionId);
		if (customSession == null) {
			throw new EvaluationException(EvaluationErrorCode.SESSION_NOT_FOUND);
		}
		return generateCustomDialogueReport(
				customSession,
				validateDialogue(dialogue));
	}

	private DialogueReportResult generateCustomDialogueReport(
			AbstractSceneSession session,
			List<Message> dialogue) {
		String sessionId = session.getId();
		var existingReport = sessionEvaluationRepository.find(sessionId);
		if (existingReport.isPresent()) {
			RealtimeFlowLog.info(
					"evaluation.report.cached sessionId={} finalScore={}",
					sessionId,
					existingReport.get().finalScore());
			return existingReport.get();
		}
		List<CustomTurnEvaluation> savedTurns = ensureTurnRecords(
				session,
				dialogue,
				turnEvaluationRepository.findAll(sessionId));
		List<CustomTurnEvaluation> scorableTurns = savedTurns.stream()
				.filter(turn -> !isUnscorable(turn))
				.sorted(Comparator.comparingInt(CustomTurnEvaluation::turnNo))
				.toList();
		if (scorableTurns.isEmpty()) {
			DialogueReportResult report = unavailableDialogueReport();
			sessionEvaluationRepository.save(
					session.getSceneId(),
					session.getId(),
					report);
			return report;
		}

		requireCompleteLearnerTurns(dialogue, savedTurns);
		DialogueReportResult report;
		try {
			ConversationLanguageAssessment language =
					llmClient.assessDialogue(dialogue);
			ConversationScoreCalculation scores =
					ConversationScoreCalculator.calculate(
							scorableTurns.stream()
									.map(this::toContribution)
									.toList(),
							language);
			report = new DialogueReportResult(
					scores.accuracyScore(),
					scores.fluencyScore(),
					scores.grammarScore(),
					scores.vocabularyScore(),
					scores.naturalnessScore(),
					scores.finalScore(),
					language.summary(),
					language.strengths(),
					language.improvements());
		}
		catch (EvaluationException exception) {
			if (!isRecoverableReportFailure(exception)) {
				throw exception;
			}
			LOGGER.warn(
					"conversation report provider unavailable; using persisted turn "
							+ "scores sessionId={} errorCode={}",
					sessionId,
					exception.errorCode());
			report = fallbackDialogueReport(scorableTurns);
			RealtimeFlowLog.info(
					"evaluation.report.fallback sessionId={} errorCode={} finalScore={}",
					sessionId,
					exception.errorCode().code(),
					report.finalScore());
		}
		sessionEvaluationRepository.save(
				session.getSceneId(),
				session.getId(),
				report);
		RealtimeFlowLog.info(
				"evaluation.report.saved sessionId={} turns={} finalScore={}",
				sessionId,
				scorableTurns.size(),
				report.finalScore());
		return report;
	}

	@Override
	public DialogueEvaluationResult getDialogueEvaluation(String sessionId) {
		String customSceneId =
				sessionMessageRepository.findSceneId(sessionId).orElse(null);
		if (customSceneId != null) {
			requireOwnedScene(customSceneId);
			List<Message> dialogue =
					sessionMessageRepository.findMessages(sessionId);
			List<DialogueTurnEvaluationResult> turns =
					turnEvaluationRepository.findAll(sessionId).stream()
							.map(this::toDialogueTurnResult)
							.toList();
			if (dialogue.isEmpty() && turns.isEmpty()) {
				throw new EvaluationException(
						EvaluationErrorCode.RESULT_NOT_FOUND);
			}
			return new DialogueEvaluationResult(dialogue, turns);
		}
		throw new EvaluationException(EvaluationErrorCode.RESULT_NOT_FOUND);
	}

	private DialogueTurnEvaluationResult evaluateCustomSceneTurn(
			AbstractSceneSession session,
			DialogueTurnEvaluationCommand command) {
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
			turnEvaluationRepository.upsert(toCustomTurn(
					session,
					result,
					List.of()));
			return result;
		}

		PcmWavValidator.validate(command.audio());
		PronunciationAssessmentResult assessment =
				pronunciationClient.evaluate(command.transcript(), command.audio());
		TurnSpeechScoreCalculator.calculate(assessment);
		TurnLanguageFeedback feedback = llmClient.assessTurn(
				buildCustomTurnPrompt(session, command));
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
		turnEvaluationRepository.upsert(toCustomTurn(
				session,
				result,
				toPersistedWords(assessment)));
		return result;
	}

	private DialogueTurnEvaluationPromptInput buildCustomTurnPrompt(
			AbstractSceneSession session,
			DialogueTurnEvaluationCommand command) {
		CustomSceneDefinition scene = sceneRepository
				.findCustomDefinitionById(session.getSceneId())
				.orElseThrow(() -> new EvaluationException(
						EvaluationErrorCode.SESSION_NOT_FOUND));
		List<Message> messages =
				sessionMessageRepository.findMessages(session.getId());
		List<DialogueTurnEvaluationHistory> history =
				turnEvaluationRepository.findBefore(
								session.getId(),
								command.turnNo())
						.stream()
						.map(turn -> new DialogueTurnEvaluationHistory(
								turn.turnNo(),
								findAiText(messages, turn.turnNo()),
								turn.transcript()))
						.toList();
		return new DialogueTurnEvaluationPromptInput(
				SceneType.CUSTOM_SCENE.name(),
				scene.background(),
				scene.aiRole(),
				scene.userRole(),
				scene.learningGoal(),
				history,
				findAiText(messages, command.turnNo()),
				command.transcript());
	}

	private TurnScoreContribution toContribution(
			CustomTurnEvaluation evaluation) {
		return TurnSpeechScoreCalculator.calculate(
				toAssessment(evaluation)).toContribution();
	}

	private PronunciationAssessmentResult toAssessment(
			CustomTurnEvaluation evaluation) {
		List<PronunciationWordResult> words = evaluation.words().stream()
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
				evaluation.overallScore(),
				evaluation.rhythmScore(),
				evaluation.toneScore(),
				evaluation.integrityScore(),
				evaluation.pronunciationScore(),
				evaluation.fluencyScore(),
				EndingTone.UNKNOWN,
				words);
	}

	private List<PronunciationWordDetail> toPersistedWords(
			PronunciationAssessmentResult assessment) {
		return assessment.words().stream()
				.filter(word -> word.phonemes().stream().anyMatch(
						phoneme -> phoneme.startPosition() >= 0
								&& phoneme.endPosition()
										> phoneme.startPosition()))
				.map(word -> new PronunciationWordDetail(
						word.index(),
						word.word(),
						word.pronunciationScore(),
						word.phonemes().stream()
								.filter(phoneme ->
										phoneme.startPosition() >= 0
												&& phoneme.endPosition()
												> phoneme.startPosition())
								.map(phoneme -> new PronunciationWordDetail.Phoneme(
										phoneme.index(),
										phoneme.expectedPhoneme(),
										phoneme.actualPhoneme(),
										phoneme.pronunciationScore(),
										phoneme.startPosition(),
										phoneme.endPosition()))
								.toList()))
				.toList();
	}

	private CustomTurnEvaluation toCustomTurn(
			AbstractSceneSession session,
			DialogueTurnEvaluationResult result,
			List<PronunciationWordDetail> words) {
		return new CustomTurnEvaluation(
				session.getSceneId(),
				session.getId(),
				result.turnNo(),
				result.transcript(),
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

	private DialogueTurnEvaluationResult toDialogueTurnResult(
			CustomTurnEvaluation evaluation) {
		return new DialogueTurnEvaluationResult(
				evaluation.turnNo(),
				evaluation.transcript(),
				evaluation.overallScore(),
				evaluation.rhythmScore(),
				evaluation.toneScore(),
				evaluation.integrityScore(),
				evaluation.pronunciationScore(),
				evaluation.fluencyScore(),
				evaluation.feedbackSummary(),
				evaluation.suggestedExpression(),
				evaluation.words().stream()
						.map(word -> new WordPronunciationScore(
								word.text(),
								word.pronunciationScore(),
								word.phonemes().stream()
										.map(phoneme -> new PhonemeScore(
												phoneme.expectedPhoneme(),
												phoneme.actualPhoneme(),
												phoneme.pronunciationScore()))
										.toList()))
						.toList());
	}

	private AbstractSceneSession findCustomRuntimeSession(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			return null;
		}
		return activeSessionRegistry.findById(sessionId)
				.filter(session -> session.getSceneType() == SceneType.CUSTOM_SCENE)
				.orElse(null);
	}

	private CustomSceneDefinition requireOwnedScene(String sceneId) {
		String userId = authService.requireUserId(null);
		CustomSceneDefinition scene = sceneRepository
				.findCustomDefinitionById(sceneId)
				.orElseThrow(() -> new EvaluationException(
						EvaluationErrorCode.SENTENCE_NOT_FOUND));
		if (!userId.equals(scene.userId())) {
			throw new EvaluationException(
					EvaluationErrorCode.SENTENCE_NOT_FOUND);
		}
		return scene;
	}

	private AbstractSceneSession requireOwnedCustomSession(
			String sceneId,
			String sessionId) {
		String userId = authService.requireUserId(null);
		CustomSceneDefinition scene = sceneRepository
				.findCustomDefinitionById(sceneId)
				.orElseThrow(() -> new EvaluationException(
						EvaluationErrorCode.SESSION_NOT_FOUND));
		AbstractSceneSession session = requireCustomRuntimeSession(sessionId);
		if (!userId.equals(scene.userId())
				|| !userId.equals(session.getUserId())
				|| !sceneId.equals(session.getSceneId())) {
			throw new EvaluationException(
					EvaluationErrorCode.SESSION_NOT_FOUND);
		}
		return session;
	}

	private AbstractSceneSession requireCustomRuntimeSession(String sessionId) {
		AbstractSceneSession session = findCustomRuntimeSession(sessionId);
		if (session == null
				|| session.getSceneId() == null
				|| session.getSceneId().isBlank()) {
			throw new EvaluationException(EvaluationErrorCode.SESSION_NOT_FOUND);
		}
		return session;
	}

	private boolean isTooShort(CustomTurnEvaluation turn) {
		return TooShortEvaluationPolicy.isTooShort(
				turn.overallScore(),
				turn.rhythmScore(),
				turn.toneScore(),
				turn.integrityScore(),
				turn.pronunciationScore(),
				turn.fluencyScore(),
				turn.feedbackSummary());
	}

	private boolean isUnscorable(CustomTurnEvaluation turn) {
		return isTooShort(turn)
				|| UnavailableTurnEvaluationPolicy.isUnavailable(
						new UnavailableTurnEvaluationPolicy.CustomScores(
								turn.overallScore(),
								turn.rhythmScore(),
								turn.toneScore(),
								turn.integrityScore(),
								turn.pronunciationScore(),
								turn.fluencyScore(),
								turn.feedbackSummary()));
	}

	private List<CustomTurnEvaluation> ensureTurnRecords(
			AbstractSceneSession session,
			List<Message> dialogue,
			List<CustomTurnEvaluation> savedTurns) {
		Map<Integer, CustomTurnEvaluation> byTurn = new HashMap<>();
		for (CustomTurnEvaluation turn : savedTurns) {
			byTurn.put(turn.turnNo(), turn);
		}
		int turnNo = 0;
		for (Message message : dialogue) {
			if (message.owner() != 1) {
				continue;
			}
			turnNo++;
			if (byTurn.containsKey(turnNo)) {
				continue;
			}
			DialogueTurnEvaluationResult unavailable =
					UnavailableTurnEvaluationPolicy.createResult(
							turnNo,
							message.content());
			CustomTurnEvaluation record = toCustomTurn(
					session,
					unavailable,
					List.of());
			turnEvaluationRepository.upsert(record);
			byTurn.put(turnNo, record);
			LOGGER.warn(
					"backfilled missing custom turn evaluation sessionId={} turnNo={}",
					session.getId(),
					turnNo);
		}
		return byTurn.values().stream()
				.sorted(Comparator.comparingInt(CustomTurnEvaluation::turnNo))
				.toList();
	}

	private boolean isRecoverableTurnFailure(EvaluationException exception) {
		return switch (exception.errorCode()) {
			case TRANSCRIPT_REQUIRED,
					AUDIO_REQUIRED,
					AUDIO_UNSUPPORTED,
					AUDIO_INVALID,
					PROVIDER_NOT_CONFIGURED,
					PROVIDER_CALL_FAILED,
					PROVIDER_REJECTED,
					PROVIDER_RESPONSE_INVALID,
					PROVIDER_RESPONSE_INCOMPLETE,
					PROMPT_TEMPLATE_INVALID -> true;
			default -> false;
		};
	}

	private DialogueReportResult unavailableDialogueReport() {
		return new DialogueReportResult(
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				"本次对话已保存，但有效英文语音不足，暂时无法生成完整五维评分。",
				List.of(),
				List.of("请使用完整英文句子完成至少一轮回答后再试。"));
	}

	private boolean isRecoverableReportFailure(EvaluationException exception) {
		return switch (exception.errorCode()) {
			case PROVIDER_NOT_CONFIGURED,
					PROVIDER_CALL_FAILED,
					PROVIDER_REJECTED,
					PROVIDER_RESPONSE_INVALID,
					PROVIDER_RESPONSE_INCOMPLETE,
					PROMPT_TEMPLATE_INVALID,
					RESULT_INCOMPLETE -> true;
			default -> false;
		};
	}

	/**
	 * A provider-independent report used only when the final language report
	 * cannot be produced. Every value is derived from persisted turn_evaluation
	 * rows, so actively ending a session never strands the client.
	 */
	private DialogueReportResult fallbackDialogueReport(
			List<CustomTurnEvaluation> turns) {
		BigDecimal accuracy = average(
				turns,
				turn -> firstScore(
						turn.pronunciationScore(),
						turn.overallScore()));
		BigDecimal fluency = average(
				turns,
				turn -> firstScore(turn.fluencyScore(), turn.overallScore()));
		BigDecimal grammar = average(
				turns,
				turn -> firstScore(turn.integrityScore(), turn.overallScore()));
		BigDecimal vocabulary = average(
				turns,
				CustomTurnEvaluation::overallScore);
		BigDecimal naturalness = average(
				turns,
				turn -> meanAvailable(
						turn.rhythmScore(),
						turn.toneScore(),
						turn.fluencyScore(),
						turn.overallScore()));
		BigDecimal finalScore = accuracy.multiply(new BigDecimal("0.25"))
				.add(fluency.multiply(new BigDecimal("0.20")))
				.add(grammar.multiply(new BigDecimal("0.20")))
				.add(vocabulary.multiply(new BigDecimal("0.15")))
				.add(naturalness.multiply(new BigDecimal("0.20")))
				.setScale(1, RoundingMode.HALF_UP);
		return new DialogueReportResult(
				accuracy,
				fluency,
				grammar,
				vocabulary,
				naturalness,
				finalScore,
				"本次会话已结束。五维结果根据已保存的逐轮评分生成，语言模型文字报告暂不可用。",
				List.of("已完成的有效轮次均已保存。"),
				List.of("可稍后重新打开学习资产查看更新后的报告。"));
	}

	private BigDecimal average(
			List<CustomTurnEvaluation> turns,
			Function<CustomTurnEvaluation, BigDecimal> extractor) {
		List<BigDecimal> scores = turns.stream()
				.map(extractor)
				.filter(Objects::nonNull)
				.toList();
		return meanAvailable(scores.toArray(BigDecimal[]::new));
	}

	private BigDecimal meanAvailable(BigDecimal... scores) {
		BigDecimal total = BigDecimal.ZERO;
		int count = 0;
		for (BigDecimal score : scores) {
			if (score == null) {
				continue;
			}
			total = total.add(score.max(BigDecimal.ZERO)
					.min(new BigDecimal("100")));
			count++;
		}
		return count == 0
				? BigDecimal.ZERO.setScale(1)
				: total.divide(
						BigDecimal.valueOf(count),
						1,
						RoundingMode.HALF_UP);
	}

	private BigDecimal firstScore(
			BigDecimal preferred,
			BigDecimal fallback) {
		return preferred == null ? fallback : preferred;
	}

	private String findAiText(List<Message> messages, int learnerTurnNo) {
		int learnerTurn = 0;
		String latestAiText = null;
		for (Message message : messages) {
			if (message.owner() == 0) {
				latestAiText = message.content();
				continue;
			}
			learnerTurn++;
			if (learnerTurn == learnerTurnNo) {
				return latestAiText;
			}
		}
		return null;
	}

	private void requireCompleteLearnerTurns(
			List<Message> dialogue,
			List<CustomTurnEvaluation> savedTurns) {
		long learnerTurns = dialogue.stream()
				.filter(message -> message.owner() == 1)
				.count();
		Map<Integer, CustomTurnEvaluation> turnsByNumber = new HashMap<>();
		for (CustomTurnEvaluation turn : savedTurns) {
			turnsByNumber.put(turn.turnNo(), turn);
		}
		if (turnsByNumber.size() < learnerTurns) {
			throw new EvaluationException(EvaluationErrorCode.RESULT_INCOMPLETE);
		}
		for (int turnNo = 1; turnNo <= learnerTurns; turnNo++) {
			if (!turnsByNumber.containsKey(turnNo)) {
				throw new EvaluationException(
						EvaluationErrorCode.RESULT_INCOMPLETE);
			}
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

}
