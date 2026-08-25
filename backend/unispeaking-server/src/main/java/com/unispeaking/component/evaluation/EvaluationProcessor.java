package com.unispeaking.component.evaluation;

import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.domain.dto.evaluation.IeltsPartEvaluation;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationHistoryItem;
import com.unispeaking.domain.dto.evaluation.PhonemeScore;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationResponse;
import com.unispeaking.domain.dto.evaluation.SpeechEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.SpeechEvaluationResult;
import com.unispeaking.domain.dto.evaluation.WordPronunciationScore;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.scene.IeltsTopic;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsStage;
import com.unispeaking.domain.vo.scene.RecommendedExpression;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.infrastructure.persistence.entity.evaluation.CustomTurnEvaluation;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsPartEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationWordDetail;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.IeltsEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository;
import com.unispeaking.infrastructure.config.ObjectStorageProperties;
import com.unispeaking.provider.ObjectStorageProvider;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import com.unispeaking.common.evaluation.validation.PcmWavValidator;
import com.unispeaking.common.evaluation.calculation.ConversationScoreCalculation;
import com.unispeaking.common.evaluation.calculation.ConversationScoreCalculator;
import com.unispeaking.common.evaluation.calculation.TurnScoreContribution;
import com.unispeaking.common.evaluation.calculation.TurnSpeechScoreCalculator;
import com.unispeaking.infrastructure.evaluation.client.EvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.IeltsEvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.PronunciationAssessmentClient;
import com.unispeaking.provider.AiInvocationContext;
import com.unispeaking.provider.AiInvocationContexts;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.evaluation.model.ConversationLanguageAssessment;
import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.TurnLanguageFeedback;
import com.unispeaking.common.evaluation.model.IeltsTextAssessment;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import com.unispeaking.common.evaluation.policy.TooShortEvaluationPolicy;
import com.unispeaking.common.evaluation.validation.EnglishWordCounter;
import com.unispeaking.common.prompt.evaluation.DialogueTurnEvaluationHistory;
import com.unispeaking.common.prompt.evaluation.DialogueTurnEvaluationPromptInput;
import com.unispeaking.common.evaluation.policy.UnavailableTurnEvaluationPolicy;
import com.unispeaking.common.evaluation.policy.SentenceReadingPassPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 最新评分接口的默认实现。
 */
@Component
@Profile("!test")
public class EvaluationProcessor {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			EvaluationProcessor.class);
	private static final int IELTS_EVALUATION_LOCK_STRIPES = 64;
	private static final SentenceReadingPassPolicy SENTENCE_READING_PASS_POLICY =
			new SentenceReadingPassPolicy();

	private final PronunciationAssessmentClient pronunciationClient;
	private final EvaluationLlmClient llmClient;
	private final ActiveSessionRegistry activeSessionRegistry;
	private final SceneRepository sceneRepository;
	private final SessionMessageRepository sessionMessageRepository;
	private final TurnEvaluationRepository turnEvaluationRepository;
	private final SessionEvaluationRepository sessionEvaluationRepository;
	private final SceneSentenceReadingRepository sceneSentenceReadingRepository;
	private final IeltsPracticeRepository ieltsPracticeRepository;
	private final IeltsRepository ieltsRepository;
	private final IeltsSceneFlowService sceneFlowService;
	private final PracticeSessionRepository practiceSessionRepository;
	private final IeltsEvaluationRepository ieltsEvaluationRepository;
	private final IeltsEvaluationLlmClient ieltsLlmClient;
	private final AuthService authService;
	private final ObjectStorageProvider objectStorage;
	private final ObjectStorageProperties objectStorageProperties;
	private final RecordingStore ieltsRecordingStore;
	private final ConcurrentHashMap<String, EvaluationLock> ieltsEvaluationLocks =
			new ConcurrentHashMap<>();
	private Executor ieltsPartEvaluationExecutor = Runnable::run;
	private Executor turnEvaluationExecutor = Runnable::run;

	public EvaluationProcessor(
			PronunciationAssessmentClient pronunciationClient,
			EvaluationLlmClient llmClient,
			ActiveSessionRegistry activeSessionRegistry,
			SceneRepository sceneRepository,
			SessionMessageRepository sessionMessageRepository,
			TurnEvaluationRepository turnEvaluationRepository,
			SessionEvaluationRepository sessionEvaluationRepository,
			SceneSentenceReadingRepository sceneSentenceReadingRepository,
			IeltsPracticeRepository ieltsPracticeRepository,
			IeltsRepository ieltsRepository,
			IeltsSceneFlowService sceneFlowService,
			PracticeSessionRepository practiceSessionRepository,
			IeltsEvaluationRepository ieltsEvaluationRepository,
			IeltsEvaluationLlmClient ieltsLlmClient,
			AuthService authService,
			ObjectStorageProvider objectStorage,
			ObjectStorageProperties objectStorageProperties,
			@Qualifier("ieltsRecordingStore") RecordingStore ieltsRecordingStore) {
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
		this.ieltsPracticeRepository = Objects.requireNonNull(
				ieltsPracticeRepository,
				"ieltsPracticeRepository must not be null");
		this.ieltsRepository = Objects.requireNonNull(
				ieltsRepository,
				"ieltsRepository must not be null");
		this.sceneFlowService = Objects.requireNonNull(
				sceneFlowService,
				"sceneFlowService must not be null");
		this.practiceSessionRepository = Objects.requireNonNull(
				practiceSessionRepository,
				"practiceSessionRepository must not be null");
		this.ieltsEvaluationRepository = Objects.requireNonNull(
				ieltsEvaluationRepository,
				"ieltsEvaluationRepository must not be null");
		this.ieltsLlmClient = Objects.requireNonNull(
				ieltsLlmClient,
				"ieltsLlmClient must not be null");
		this.authService = Objects.requireNonNull(
				authService,
				"authService must not be null");
		this.objectStorage = Objects.requireNonNull(
				objectStorage,
				"objectStorage must not be null");
		this.objectStorageProperties = Objects.requireNonNull(
				objectStorageProperties,
				"objectStorageProperties must not be null");
		this.ieltsRecordingStore = Objects.requireNonNull(
				ieltsRecordingStore,
				"ieltsRecordingStore must not be null");
	}

	@Autowired
	public void configureIeltsPartEvaluationExecutor(
			@Qualifier("ieltsPartEvaluationExecutor") Executor executor) {
		this.ieltsPartEvaluationExecutor = Objects.requireNonNull(executor);
	}

	@Autowired
	public void configureTurnEvaluationExecutor(
			@Qualifier("turnEvaluationExecutor") Executor executor) {
		this.turnEvaluationExecutor = Objects.requireNonNull(executor);
	}

	public SpeechEvaluationResult evaluateSpeech(
			SpeechEvaluationCommand command) {
		if (command == null
				|| command.referenceText() == null
				|| command.referenceText().isBlank()) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		String referenceText = command.referenceText().trim();
		byte[] audio = command.audio();
		PcmWavValidator.validate(audio);
		PronunciationAssessmentResult assessment =
				pronunciationClient.evaluate(referenceText, audio);
		var calculation = TurnSpeechScoreCalculator.calculate(assessment);
		return new SpeechEvaluationResult(
				calculation.accuracyScore(),
				calculation.fluencyScore(),
				calculation.effectiveDurationUnits(),
				calculation.validPhonemeCount());
	}

	public IeltsEvaluationResult generateIeltsEvaluation(
			String ieltsId,
			String sessionId) {
		return generateIeltsEvaluationForUser(
				ieltsId,
				sessionId,
				authService.requireUserId(null));
	}

	public IeltsEvaluationResult generateIeltsEvaluationForUser(
			String ieltsId,
			String sessionId,
			String userId) {
		IeltsPracticeRecord practice = requireOwnedIeltsPractice(ieltsId, userId);
		List<PracticeSessionRecord> sessions = completedIeltsSessions(ieltsId);
		int sessionIndex = sessionIndex(sessions, sessionId);
		if (sessionIndex < 0
				|| !sessions.get(sessionIndex).userId().toString().equals(userId)) {
			throw new EvaluationException(EvaluationErrorCode.SESSION_NOT_FOUND);
		}
		boolean finalTask = practice.mode() == IeltsMode.MOCK_TEST
				&& sessionIndex >= 2;
		String lockKey = finalTask ? "FINAL:" + ieltsId : "PART:" + sessionId;
		return withEvaluationLock(
				lockKey,
				() -> generateIeltsEvaluationLocked(practice, sessionId));
	}

	private IeltsEvaluationResult generateIeltsEvaluationLocked(
			IeltsPracticeRecord practice,
			String sessionId) {
		String ieltsId = practice.ieltsId();
		List<PracticeSessionRecord> sessions = completedIeltsSessions(ieltsId);
		int sessionIndex = sessionIndex(sessions, sessionId);
		if (sessionIndex < 0) {
			throw new EvaluationException(EvaluationErrorCode.SESSION_NOT_FOUND);
		}
		boolean finalMockEvaluation = practice.mode() == IeltsMode.MOCK_TEST
				&& sessionIndex >= 2;
		if (finalMockEvaluation) {
			var cachedFinal = ieltsEvaluationRepository.findFinal(ieltsId);
			if (cachedFinal.isPresent()
					&& "COMPLETED".equals(cachedFinal.get().getEvaluationStatus())) {
				return toFinalEvaluationResult(
						cachedFinal.get(),
						ieltsEvaluationRepository.findParts(ieltsId).stream()
								.map(this::toHistoryPartEvaluation)
								.toList());
			}
			List<CompletableFuture<IeltsPartEvaluation>> partFutures = new ArrayList<>();
			for (int index = 0; index < Math.min(3, sessions.size()); index++) {
				PracticeSessionRecord partSession = sessions.get(index);
				IeltsPart part = partByIndex(index);
				partFutures.add(submitPartEvaluation(
						() -> resolvePartEvaluation(practice, partSession, part)));
			}
			List<IeltsPartEvaluation> partEvaluations = partFutures.stream()
					.map(this::awaitTask)
					.toList();
			IeltsEvaluationResult finalResult = evaluateCompleteIeltsTest(
					sessions,
					partEvaluations);
			ieltsEvaluationRepository.saveFinal(ieltsId, finalResult);
			ieltsPracticeRepository.incrementCompletedCount(practice.userId());
			return finalResult;
		}
		var cachedPart = ieltsEvaluationRepository.findPart(sessionId);
		if (cachedPart.isPresent()
				&& "COMPLETED".equals(cachedPart.get().getEvaluationStatus())) {
			return toPartEvaluationResult(cachedPart.get());
		}
		IeltsEvaluationResult result = evaluateIeltsPart(
						practice,
						sessionId,
						practice.selectedPart() != null
								? practice.selectedPart()
								: partByIndex(sessionIndex));
			ieltsEvaluationRepository.savePart(ieltsId, sessionId, result);
			if (practice.mode() == IeltsMode.PART_PRACTICE) {
				ieltsPracticeRepository.incrementCompletedCount(practice.userId());
			}
			return result;
	}

	public BigDecimal getLatestIeltsEstimatedScore() {
		return getIeltsEvaluationHistory().stream()
				.filter(item -> item.mode() == IeltsMode.MOCK_TEST)
				.filter(item -> "FINAL".equals(item.assessmentType()))
				.findFirst()
				.map(IeltsEvaluationHistoryItem::overallBandScore)
				.orElse(null);
	}

	public List<IeltsEvaluationHistoryItem> getIeltsEvaluationHistory() {
		String userId = authService.requireUserId(null);
		Map<String, List<PracticeSessionRecord>> sessionsByPractice =
				practiceSessionRepository.findCompletedByUserAndSceneType(
						UUID.fromString(userId),
						SceneType.IELTS_SCENE)
						.stream()
						.collect(java.util.stream.Collectors.groupingBy(
								PracticeSessionRecord::sceneId,
								LinkedHashMap::new,
								java.util.stream.Collectors.toList()));
		Map<String, IeltsPracticeRecord> practicesById = new LinkedHashMap<>();
		java.util.Set<String> topicIds = new java.util.LinkedHashSet<>();
		for (String practiceId : sessionsByPractice.keySet()) {
			ieltsPracticeRepository.findPractice(practiceId).ifPresent(practice -> {
				practicesById.put(practiceId, practice);
				if (practice.part1TopicId() != null) topicIds.add(practice.part1TopicId());
				if (practice.part2TopicId() != null) topicIds.add(practice.part2TopicId());
				if (practice.part3TopicId() != null) topicIds.add(practice.part3TopicId());
			});
		}
		Map<String, String> topicTitleById = ieltsRepository
				.findTopicsByIds(topicIds)
				.stream()
				.collect(java.util.stream.Collectors.toMap(
						IeltsTopic::id,
						IeltsTopic::title));
		List<IeltsEvaluationHistoryItem> history = new ArrayList<>();
		for (Map.Entry<String, List<PracticeSessionRecord>> entry
				: sessionsByPractice.entrySet()) {
			IeltsPracticeRecord practice = practicesById.get(entry.getKey());
			if (practice == null) continue;
			List<PracticeSessionRecord> sessions = entry.getValue();
			PracticeSessionRecord resultSession;
			String assessmentType;
			IeltsPart part;
			if (practice.mode() == IeltsMode.MOCK_TEST) {
				if (sessions.size() < 3) continue;
				resultSession = sessions.get(2);
				assessmentType = "FINAL";
				part = null;
			}
			else {
				resultSession = sessions.getLast();
				assessmentType = "DIAGNOSTIC";
				part = practice.selectedPart();
			}
			BigDecimal overall;
			BigDecimal fluency;
			BigDecimal lexical;
			BigDecimal grammar;
			BigDecimal pronunciation;
			String fluencyReason;
			String lexicalReason;
			String grammarReason;
			String pronunciationReason;
			String summary;
			String[] strengths;
			String[] improvements;
			String[] recommendedExpressions;
			List<IeltsPartEvaluation> partEvaluations;
			if (practice.mode() == IeltsMode.MOCK_TEST) {
				IeltsEvaluationEntity evaluation = ieltsEvaluationRepository
						.findFinal(practice.ieltsId())
						.orElse(null);
				if (evaluation == null) continue;
				overall = evaluation.getOverallBandScore();
				fluency = evaluation.getFluencyCoherenceScore();
				lexical = evaluation.getLexicalResourceScore();
				grammar = evaluation.getGrammaticalRangeAccuracyScore();
				pronunciation = evaluation.getPronunciationScore();
				fluencyReason = evaluation.getFluencyCoherenceReason();
				lexicalReason = evaluation.getLexicalResourceReason();
				grammarReason = evaluation.getGrammaticalRangeAccuracyReason();
				pronunciationReason = evaluation.getPronunciationReason();
				summary = evaluation.getSummary();
				strengths = evaluation.getStrengths();
				improvements = evaluation.getImprovements();
				recommendedExpressions = evaluation.getRecommendedExpressions();
				partEvaluations = ieltsEvaluationRepository.findParts(practice.ieltsId())
						.stream()
						.map(this::toHistoryPartEvaluation)
						.toList();
			}
			else {
				IeltsPartEvaluationEntity evaluation = ieltsEvaluationRepository
						.findPart(resultSession.sessionId())
						.orElse(null);
				if (evaluation == null) continue;
				overall = null;
				fluency = evaluation.getFluencyCoherenceScore();
				lexical = evaluation.getLexicalResourceScore();
				grammar = evaluation.getGrammaticalRangeAccuracyScore();
				pronunciation = evaluation.getPronunciationScore();
				fluencyReason = evaluation.getFluencyCoherenceReason();
				lexicalReason = evaluation.getLexicalResourceReason();
				grammarReason = evaluation.getGrammaticalRangeAccuracyReason();
				pronunciationReason = evaluation.getPronunciationReason();
				summary = evaluation.getSummary();
				strengths = evaluation.getStrengths();
				improvements = evaluation.getImprovements();
				recommendedExpressions = evaluation.getRecommendedExpressions();
				partEvaluations = List.of(toHistoryPartEvaluation(evaluation));
			}
			history.add(new IeltsEvaluationHistoryItem(
					resultSession.sessionId(),
					practice.ieltsId(),
					practice.mode(),
					part,
					assessmentType,
					overall,
					fluency,
					lexical,
					grammar,
					pronunciation,
					summary,
					strengths == null
							? List.of()
							: List.of(strengths),
					improvements == null
							? List.of()
							: List.of(improvements),
					recommendedExpressions == null
							? List.of()
							: List.of(recommendedExpressions),
					partEvaluations,
					practice.topicSelectionMethod(),
					topicTitles(practice, topicTitleById),
					recordingUrls(practice.mode() == IeltsMode.MOCK_TEST
							? sessions
							: List.of(resultSession)),
					practice.mode() == IeltsMode.MOCK_TEST
							? sessions.getFirst().startedAt()
							: resultSession.startedAt(),
					resultSession.endedAt(),
					fluencyReason,
					lexicalReason,
					grammarReason,
					pronunciationReason));
		}
		return history.stream()
				.sorted(Comparator.comparing(
						IeltsEvaluationHistoryItem::endedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
	}

	private Map<IeltsPart, String> topicTitles(
			IeltsPracticeRecord practice,
			Map<String, String> titleById) {
		Map<IeltsPart, String> titles = new LinkedHashMap<>();
		putTopicTitle(titles, IeltsPart.PART_1, practice.part1TopicId(), titleById);
		putTopicTitle(titles, IeltsPart.PART_2, practice.part2TopicId(), titleById);
		putTopicTitle(titles, IeltsPart.PART_3, practice.part3TopicId(), titleById);
		return titles;
	}

	private void putTopicTitle(
			Map<IeltsPart, String> target,
			IeltsPart part,
			String topicId,
			Map<String, String> titleById) {
		if (topicId == null || topicId.isBlank()) return;
		target.put(part, titleById.getOrDefault(topicId, topicId));
	}

	private IeltsPartEvaluation toHistoryPartEvaluation(
			IeltsPartEvaluationEntity evaluation) {
		return new IeltsPartEvaluation(
				IeltsPart.valueOf(evaluation.getPart()),
				evaluation.getFluencyCoherenceScore(),
				evaluation.getLexicalResourceScore(),
				evaluation.getGrammaticalRangeAccuracyScore(),
				evaluation.getPronunciationScore(),
				evaluation.getSummary(),
				evaluation.getStrengths() == null
						? List.of()
						: List.of(evaluation.getStrengths()),
				evaluation.getImprovements() == null
						? List.of()
						: List.of(evaluation.getImprovements()),
				evaluation.getRecommendedExpressions() == null
						? List.of()
						: List.of(evaluation.getRecommendedExpressions()),
				evaluation.getFluencyCoherenceReason(),
				evaluation.getLexicalResourceReason(),
				evaluation.getGrammaticalRangeAccuracyReason(),
				evaluation.getPronunciationReason());
	}

	private IeltsEvaluationResult toPartEvaluationResult(
			IeltsPartEvaluationEntity evaluation) {
		IeltsPartEvaluation partEvaluation = toHistoryPartEvaluation(evaluation);
		return new IeltsEvaluationResult(
				partEvaluation.part(),
				"DIAGNOSTIC",
				null,
				partEvaluation.fluencyCoherenceScore(),
				partEvaluation.lexicalResourceScore(),
				partEvaluation.grammaticalRangeAccuracyScore(),
				partEvaluation.pronunciationScore(),
				partEvaluation.summary(),
				partEvaluation.strengths(),
				partEvaluation.improvements(),
				List.of(),
				partEvaluation.recommendedExpressions(),
				partEvaluation.fluencyCoherenceReason(),
				partEvaluation.lexicalResourceReason(),
				partEvaluation.grammaticalRangeAccuracyReason(),
				partEvaluation.pronunciationReason());
	}

	private IeltsEvaluationResult toFinalEvaluationResult(
			IeltsEvaluationEntity evaluation,
			List<IeltsPartEvaluation> partEvaluations) {
		return new IeltsEvaluationResult(
				null,
				"FINAL",
				evaluation.getOverallBandScore(),
				evaluation.getFluencyCoherenceScore(),
				evaluation.getLexicalResourceScore(),
				evaluation.getGrammaticalRangeAccuracyScore(),
				evaluation.getPronunciationScore(),
				evaluation.getSummary(),
				evaluation.getStrengths() == null
						? List.of()
						: List.of(evaluation.getStrengths()),
				evaluation.getImprovements() == null
						? List.of()
						: List.of(evaluation.getImprovements()),
				partEvaluations,
				evaluation.getRecommendedExpressions() == null
						? List.of()
						: List.of(evaluation.getRecommendedExpressions()),
				evaluation.getFluencyCoherenceReason(),
				evaluation.getLexicalResourceReason(),
				evaluation.getGrammaticalRangeAccuracyReason(),
				evaluation.getPronunciationReason());
	}

	private IeltsEvaluationResult evaluateIeltsPart(
			IeltsPracticeRecord practice,
			String sessionId,
			IeltsPart part) {
		List<Message> messages = sessionMessageRepository.findMessages(sessionId);
		String transcript = formatTranscript(messages, true);
		List<CustomTurnEvaluation> turns =
				turnEvaluationRepository.findAll(sessionId);
		List<CustomTurnEvaluation> scorableTurns = turns.stream()
				.filter(turn -> !isUnscorable(turn))
				.toList();
		String cueCard = part == IeltsPart.PART_2
				? formatCueCard(practice)
				: null;
		IeltsTextAssessment text = AiInvocationContexts.call(
				AiInvocationContext.create(practice.userId().toString(), sessionId, "ielts_part_evaluation"),
				() -> ieltsLlmClient.assessPart(part, transcript, cueCard, formatSpeechMetrics(scorableTurns)));
		BigDecimal pronunciation = scorableTurns.isEmpty()
				? null
				: pronunciationBand(scorableTurns);
		List<String> recommendedExpressions = recommendedExpressions(turns);
		return new IeltsEvaluationResult(
				part,
				"DIAGNOSTIC",
				null,
				text.fluencyCoherenceBand(),
				text.lexicalResourceBand(),
				text.grammaticalRangeAccuracyBand(),
				pronunciation,
				text.summary()
						+ (pronunciation == null
								? " 本 Part 的有效发音诊断暂不可用。"
								: "")
						+ " 本结果仅包含四项能力诊断，不生成单 Part 总分。",
				text.strengths(),
				text.improvements(),
				List.of(),
				recommendedExpressions,
				text.fluencyCoherenceReason(),
				text.lexicalResourceReason(),
				text.grammaticalRangeAccuracyReason(),
				pronunciationReason(pronunciation, scorableTurns));
	}

	private IeltsEvaluationResult evaluateCompleteIeltsTest(
			List<PracticeSessionRecord> sessions,
			List<IeltsPartEvaluation> partEvaluations) {
		List<CustomTurnEvaluation> allTurns = new ArrayList<>();
		for (int index = 0; index < Math.min(3, sessions.size()); index++) {
			var session = sessions.get(index);
			allTurns.addAll(turnEvaluationRepository.findAll(session.sessionId()));
		}
		BigDecimal fluency = averagePartScore(
				partEvaluations,
				IeltsPartEvaluation::fluencyCoherenceScore);
		BigDecimal lexical = averagePartScore(
				partEvaluations,
				IeltsPartEvaluation::lexicalResourceScore);
		BigDecimal grammar = averagePartScore(
				partEvaluations,
				IeltsPartEvaluation::grammaticalRangeAccuracyScore);
		BigDecimal pronunciation = averagePartScore(
				partEvaluations,
				IeltsPartEvaluation::pronunciationScore);
		BigDecimal overall = averageAvailableBands(java.util.stream.Stream.of(
					fluency,
					lexical,
					grammar,
					pronunciation)
				.filter(Objects::nonNull)
				.toList());
		return new IeltsEvaluationResult(
				null,
				"FINAL",
				overall,
				fluency,
				lexical,
				grammar,
				pronunciation,
				"完整模考总评由 Part 1、Part 2 和 Part 3 的四项能力评分汇总生成。",
				partEvaluations.stream()
						.flatMap(part -> part.strengths().stream())
						.distinct()
						.toList(),
				partEvaluations.stream()
						.flatMap(part -> part.improvements().stream())
						.distinct()
						.toList(),
				partEvaluations,
				recommendedExpressions(allTurns),
				aggregatedPartReason("流利与连贯", fluency),
				aggregatedPartReason("词汇资源", lexical),
				aggregatedPartReason("语法范围与准确性", grammar),
				aggregatedPartReason("发音", pronunciation));
	}

	private BigDecimal averagePartScore(
			List<IeltsPartEvaluation> evaluations,
			Function<IeltsPartEvaluation, BigDecimal> extractor) {
		List<BigDecimal> values = evaluations.stream()
				.map(extractor)
				.filter(Objects::nonNull)
				.toList();
		return averageAvailableBands(values);
	}

	private BigDecimal averageAvailableBands(List<BigDecimal> values) {
		if (values.isEmpty()) return null;
		BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		return roundToHalf(total.divide(
				BigDecimal.valueOf(values.size()),
				4,
				RoundingMode.HALF_UP));
	}

	private String aggregatedPartReason(String dimension, BigDecimal score) {
		if (score == null) {
			return "三个 Part 均缺少有效的" + dimension + "评分。";
		}
		return dimension + "分数由三个 Part 已完成的后台评分取平均并按 0.5 分取整，结果为 "
				+ score.toPlainString() + "。";
	}

	private IeltsPartEvaluation resolvePartEvaluation(
			IeltsPracticeRecord practice,
			PracticeSessionRecord session,
			IeltsPart part) {
		return withEvaluationLock("PART:" + session.sessionId(), () -> {
			var cached = ieltsEvaluationRepository.findPart(session.sessionId());
			if (cached.isPresent()
					&& "COMPLETED".equals(cached.get().getEvaluationStatus())) {
				return toHistoryPartEvaluation(cached.get());
			}
			return evaluatePartSafely(practice, session, part);
		});
	}

	private IeltsPartEvaluation toPartEvaluation(
			IeltsEvaluationResult result) {
		return new IeltsPartEvaluation(
				result.part(),
				result.fluencyCoherenceScore(),
				result.lexicalResourceScore(),
				result.grammaticalRangeAccuracyScore(),
				result.pronunciationScore(),
				result.summary(),
				result.strengths(),
				result.improvements(),
				result.recommendedExpressions(),
				result.fluencyCoherenceReason(),
				result.lexicalResourceReason(),
				result.grammaticalRangeAccuracyReason(),
				result.pronunciationReason());
	}

	private IeltsPartEvaluation evaluatePartSafely(
			IeltsPracticeRecord practice,
			PracticeSessionRecord session,
			IeltsPart part) {
		try {
			IeltsEvaluationResult result = evaluateIeltsPart(
					practice,
					session.sessionId(),
					part);
			ieltsEvaluationRepository.savePart(
					practice.ieltsId(),
					session.sessionId(),
					result);
			return toPartEvaluation(result);
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
					"IELTS per-Part evaluation unavailable during final aggregation "
							+ "sessionId={} part={} error={}",
					session.sessionId(),
					part,
					exception.getMessage());
			List<CustomTurnEvaluation> turns = turnEvaluationRepository
					.findAll(session.sessionId());
			List<CustomTurnEvaluation> scorableTurns = turns.stream()
					.filter(turn -> !isUnscorable(turn))
					.toList();
			if (!scorableTurns.isEmpty()) {
				BigDecimal fluency = roundToHalf(average(
						scorableTurns,
						CustomTurnEvaluation::fluencyScore)
						.multiply(new BigDecimal("0.09")));
				BigDecimal pronunciation = pronunciationBand(scorableTurns);
				return new IeltsPartEvaluation(
						part,
						fluency,
						null,
						null,
						pronunciation,
						"文本模型评分暂不可用，当前显示基于有效语音轮次的临时诊断分。",
						List.of(),
						List.of("评分服务恢复后重新生成完整文本诊断。"),
						recommendedExpressions(turns),
						"文本评分服务暂不可用；当前分数仅依据有效语音轮次的流利度数据折算。",
						null,
						null,
						pronunciationReason(pronunciation, scorableTurns));
			}
			return new IeltsPartEvaluation(
					part,
					null,
					null,
					null,
					null,
					"该 Part 的后台评分暂不可用。",
					List.of(),
					List.of(),
						recommendedExpressions(turns),
						null,
						null,
						null,
						null);
		}
	}

	private List<String> recommendedExpressions(
			List<CustomTurnEvaluation> turns) {
		return turns.stream()
				.map(CustomTurnEvaluation::suggestedExpression)
				.filter(value -> value != null && !value.isBlank())
				.map(String::trim)
				.distinct()
				.toList();
	}

	private String formatTranscript(
			List<Message> messages,
			boolean requireCandidateAnswer) {
		String transcript = messages.stream()
				.map(message -> (message.owner() == 0
						? "EXAMINER: "
						: "CANDIDATE: ") + message.content())
				.collect(java.util.stream.Collectors.joining("\n"));
		if (requireCandidateAnswer
				&& messages.stream().noneMatch(message -> message.owner() == 1)) {
			throw new EvaluationException(
					EvaluationErrorCode.NO_SCORABLE_UTTERANCES);
		}
		return transcript;
	}

	private String formatCueCard(IeltsPracticeRecord practice) {
		var question = practice.content().part2().stream()
				.findFirst()
				.orElseThrow(() -> new EvaluationException(
						EvaluationErrorCode.RESULT_INCOMPLETE));
		String cuePoints = question.cuePoints().isEmpty()
				? ""
				: "\nYou should say:\n- "
						+ String.join("\n- ", question.cuePoints());
		return question.question() + cuePoints;
	}

	private String formatSpeechMetrics(List<CustomTurnEvaluation> turns) {
		if (turns.isEmpty()) return null;
		return "scorable_turns=" + turns.size()
				+ "; average_fluency_score_0_100="
				+ average(turns, CustomTurnEvaluation::fluencyScore)
				+ "; exact_pause_repetition_self_correction_counts=UNAVAILABLE";
	}

	private BigDecimal pronunciationBand(List<CustomTurnEvaluation> turns) {
		if (turns.isEmpty()) {
			throw new EvaluationException(
					EvaluationErrorCode.NO_SCORABLE_UTTERANCES);
		}
		BigDecimal percentage = average(
				turns,
				CustomTurnEvaluation::pronunciationScore);
		return roundToHalf(percentage.multiply(new BigDecimal("0.09")));
	}

	private String pronunciationReason(
			BigDecimal band,
			List<CustomTurnEvaluation> turns) {
		if (band == null || turns.isEmpty()) {
			return "本次没有足够的有效原始语音，无法形成发音评分理由。";
		}
		BigDecimal averageScore = average(
				turns,
				CustomTurnEvaluation::pronunciationScore);
		List<String> lowerScoringWords = turns.stream()
				.flatMap(turn -> turn.words().stream())
				.filter(word -> word.text() != null && !word.text().isBlank())
				.filter(word -> word.pronunciationScore() != null)
				.sorted(Comparator.comparing(
						PronunciationWordDetail::pronunciationScore))
				.map(PronunciationWordDetail::text)
				.distinct()
				.limit(3)
				.toList();
		String wordEvidence = lowerScoringWords.isEmpty()
				? ""
				: "；其中较需要关注的词包括 “"
						+ String.join("”、 “", lowerScoringWords) + "”";
		return "基于本次 " + turns.size() + " 轮有效原始语音，音频模型的平均发音得分为 "
				+ averageScore.setScale(1, RoundingMode.HALF_UP).toPlainString()
				+ "/100" + wordEvidence + "，按 9 分制折算为 "
				+ band.toPlainString() + "。";
	}

	private BigDecimal overallBand(
			IeltsTextAssessment text,
			BigDecimal pronunciation) {
		return roundToHalf(text.fluencyCoherenceBand()
				.add(text.lexicalResourceBand())
				.add(text.grammaticalRangeAccuracyBand())
				.add(pronunciation)
				.divide(BigDecimal.valueOf(4), 4, RoundingMode.HALF_UP));
	}

	private BigDecimal roundToHalf(BigDecimal value) {
		return value.multiply(BigDecimal.valueOf(2))
				.setScale(0, RoundingMode.HALF_UP)
				.divide(BigDecimal.valueOf(2), 1, RoundingMode.UNNECESSARY)
				.max(BigDecimal.ZERO.setScale(1))
				.min(BigDecimal.valueOf(9).setScale(1));
	}

	private IeltsPart partByIndex(int index) {
		return switch (index) {
			case 0 -> IeltsPart.PART_1;
			case 1 -> IeltsPart.PART_2;
			case 2 -> IeltsPart.PART_3;
			default -> throw new EvaluationException(
					EvaluationErrorCode.INVALID_REQUEST);
		};
	}

	public DialogueTurnEvaluationResult evaluateIeltsTurn(
			String ieltsId,
			DialogueTurnEvaluationCommand command) {
		if (command == null || command.turnNo() < 1) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		AbstractSceneSession session = requireOwnedIeltsSession(
				ieltsId,
				command.sessionId());
		try {
			return evaluateIeltsSceneTurn(session, command);
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
					session,
					evaluation,
					List.of()));
			LOGGER.warn(
					"IELTS turn scoring unavailable sceneId={} sessionId={} "
							+ "turnNo={} code={}",
					ieltsId,
					command.sessionId(),
					command.turnNo(),
					exception.errorCode().code());
			return evaluation;
		}
		finally {
			storeIeltsRecording(command);
		}
	}

	private void storeIeltsRecording(DialogueTurnEvaluationCommand command) {
		byte[] audio = command.audio();
		if (audio == null || audio.length == 0) return;
		try {
			PcmWavValidator.validate(audio);
			String audioUrl = ieltsRecordingStore.store(
					command.sessionId(),
					command.turnNo(),
					audio);
			try {
				sessionMessageRepository.attachLearnerAudioUrl(
						command.sessionId(),
						command.turnNo(),
						audioUrl);
			}
			catch (RuntimeException exception) {
				ieltsRecordingStore.delete(
						command.sessionId(),
						command.turnNo());
				throw exception;
			}
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
					"IELTS recording persistence unavailable sessionId={} turnNo={}",
					command.sessionId(),
					command.turnNo());
		}
	}

	private List<String> recordingUrls(List<PracticeSessionRecord> sessions) {
		return sessions.stream()
				.flatMap(session -> recordingUrls(session).stream())
				.toList();
	}

	private List<String> recordingUrls(PracticeSessionRecord session) {
		List<String> storedUrls = sessionMessageRepository.findAudioUrls(
				session.sessionId());
		if (!storedUrls.isEmpty()) return storedUrls;
		if (!objectStorage.available()) return List.of();
		return sessionMessageRepository.findAudioObjectKeys(session.sessionId())
				.stream()
				.map(key -> {
					try {
						return objectStorage.signGetUrl(
								key,
								objectStorageProperties.getSignedUrlTtl())
								.toString();
					}
					catch (RuntimeException exception) {
						return null;
					}
				})
				.filter(Objects::nonNull)
				.toList();
	}

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
		SceneSentenceReadingRepository.AttemptSummary attemptSummary =
				sceneSentenceReadingRepository.summarizeAttempts(
						sceneId,
						sentenceId);
		BigDecimal bestScore = attemptSummary == null
				|| attemptSummary.bestScore() == null
				? assessment.overallScore()
				: assessment.overallScore().max(attemptSummary.bestScore());
		boolean passed = SENTENCE_READING_PASS_POLICY.passes(assessment)
				|| (attemptSummary != null
						&& SENTENCE_READING_PASS_POLICY.passesRepeatedBest(
								attemptSummary.attemptCount(),
								bestScore));
		return new SentenceEvaluationResponse(
				passed ? bestScore : assessment.overallScore(),
				passed,
				mapWords(assessment.words()));
	}

	public DialogueTurnEvaluationResult evaluateDialogueTurn(
			DialogueTurnEvaluationCommand command) {
		if (command == null || command.turnNo() < 1) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		if (command.transcript().isBlank()) {
			throw new EvaluationException(EvaluationErrorCode.TRANSCRIPT_REQUIRED);
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
			RealtimeFlowLog.info(
					"evaluation.turn.unavailable sceneId={} sessionId={} "
							+ "turnNo={} errorCode={}",
					runtimeSession.getSceneId(),
					runtimeSession.getId(),
					command.turnNo(),
					exception.errorCode().code());
			return evaluation;
		}
	}

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
			ConversationLanguageAssessment language = AiInvocationContexts.call(
					AiInvocationContext.create(session.getUserId(), sessionId, "dialogue_report"),
					() -> llmClient.assessDialogue(dialogue));
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

	private List<PracticeSessionRecord> completedIeltsSessions(String ieltsId) {
		return practiceSessionRepository.findBySceneId(ieltsId).stream()
				.filter(item -> item.sceneType() == SceneType.IELTS_SCENE)
				.filter(item -> item.status() == SessionStatus.COMPLETED)
				.toList();
	}

	private int sessionIndex(
			List<PracticeSessionRecord> sessions,
			String sessionId) {
		for (int index = 0; index < sessions.size(); index++) {
			if (sessions.get(index).sessionId().equals(sessionId)) return index;
		}
		return -1;
	}

	private <T> CompletableFuture<T> submitPartEvaluation(Supplier<T> task) {
		try {
			return CompletableFuture.supplyAsync(task, ieltsPartEvaluationExecutor);
		}
		catch (RejectedExecutionException exception) {
			LOGGER.warn("IELTS part evaluation executor saturated; using task thread");
			return CompletableFuture.completedFuture(task.get());
		}
	}

	private <T> CompletableFuture<T> submitTurnTask(Supplier<T> task) {
		try {
			return CompletableFuture.supplyAsync(task, turnEvaluationExecutor);
		}
		catch (RejectedExecutionException exception) {
			return CompletableFuture.failedFuture(new EvaluationException(
					EvaluationErrorCode.PROVIDER_CALL_FAILED,
					null,
					exception));
		}
	}

	private <T> T awaitTask(CompletableFuture<T> future) {
		try {
			return future.join();
		}
		catch (CompletionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw new EvaluationException(
					EvaluationErrorCode.PROVIDER_CALL_FAILED,
					null,
					cause);
		}
	}

	private <T> T withEvaluationLock(String key, Supplier<T> task) {
		EvaluationLock entry = ieltsEvaluationLocks.compute(key, (ignored, current) -> {
			EvaluationLock resolved = current == null ? new EvaluationLock() : current;
			resolved.references++;
			return resolved;
		});
		entry.lock.lock();
		try {
			return task.get();
		}
		finally {
			entry.lock.unlock();
			ieltsEvaluationLocks.computeIfPresent(key, (ignored, current) -> {
				if (current != entry) return current;
				entry.references--;
				return entry.references == 0 ? null : entry;
			});
		}
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
		if (command.audio() == null || command.audio().length == 0) {
			DialogueTurnEvaluationResult result =
					UnavailableTurnEvaluationPolicy.createResult(
							command.turnNo(), command.transcript());
			turnEvaluationRepository.upsert(toCustomTurn(
					session, result, List.of()));
			LOGGER.info(
					"custom turn pronunciation unavailable; preserving transcript "
							+ "sessionId={} turnNo={} reason=audio_missing",
					session.getId(), command.turnNo());
			return result;
		}

		PcmWavValidator.validate(command.audio());
		DialogueTurnEvaluationPromptInput prompt = buildCustomTurnPrompt(session, command);
		CompletableFuture<PronunciationAssessmentResult> pronunciationFuture =
				submitTurnTask(() -> AiInvocationContexts.call(
						AiInvocationContext.create(
								session.getUserId(),
								session.getId(),
								"dialogue_turn_pronunciation"),
						() -> pronunciationClient.evaluate(
								command.transcript(),
								command.audio())));
		CompletableFuture<TurnLanguageFeedback> feedbackFuture =
				submitTurnTask(() -> AiInvocationContexts.call(
						AiInvocationContext.create(
								session.getUserId(),
								session.getId(),
								"dialogue_turn_feedback"),
						() -> llmClient.assessTurn(prompt)));
		PronunciationAssessmentResult assessment = awaitTask(pronunciationFuture);
		TurnSpeechScoreCalculator.calculate(assessment);
		TurnLanguageFeedback feedback = awaitTask(feedbackFuture);
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

	private DialogueTurnEvaluationResult evaluateIeltsSceneTurn(
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
		if (command.audio() == null || command.audio().length == 0) {
			DialogueTurnEvaluationResult result =
					UnavailableTurnEvaluationPolicy.createResult(
							command.turnNo(), command.transcript());
			turnEvaluationRepository.upsert(toCustomTurn(
					session, result, List.of()));
			LOGGER.info(
					"IELTS turn pronunciation unavailable; preserving transcript "
							+ "sessionId={} turnNo={} reason=audio_missing",
					session.getId(), command.turnNo());
			return result;
		}

		PcmWavValidator.validate(command.audio());
		DialogueTurnEvaluationPromptInput prompt = buildIeltsTurnPrompt(session, command);
		CompletableFuture<PronunciationAssessmentResult> pronunciationFuture =
				submitTurnTask(() -> AiInvocationContexts.call(
						AiInvocationContext.create(
								session.getUserId(),
								session.getId(),
								"ielts_turn_pronunciation"),
						() -> pronunciationClient.evaluate(
								command.transcript(),
								command.audio())));
		CompletableFuture<TurnLanguageFeedback> feedbackFuture =
				submitTurnTask(() -> AiInvocationContexts.call(
						AiInvocationContext.create(
								session.getUserId(),
								session.getId(),
								"ielts_turn_feedback"),
						() -> llmClient.assessTurn(prompt)));
		PronunciationAssessmentResult assessment = awaitTask(pronunciationFuture);
		TurnSpeechScoreCalculator.calculate(assessment);
		TurnLanguageFeedback feedback;
		try {
			feedback = awaitTask(feedbackFuture);
		}
		catch (EvaluationException exception) {
			if (!isProviderFeedbackFailure(exception)) throw exception;
			LOGGER.warn(
					"IELTS language feedback unavailable; preserving pronunciation "
							+ "sessionId={} turnNo={} code={}",
					session.getId(),
					command.turnNo(),
					exception.errorCode().code());
			feedback = new TurnLanguageFeedback(
					"本轮发音评分已完成，语言反馈暂不可用。",
					"");
		}
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

	private DialogueTurnEvaluationPromptInput buildIeltsTurnPrompt(
			AbstractSceneSession session,
			DialogueTurnEvaluationCommand command) {
		IeltsPracticeRecord practice = requireOwnedIeltsPractice(
				session.getSceneId());
		IeltsPart part = activeIeltsPart(practice, session);
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
		List<RecommendedExpression> recommendedExpressions = practice.content()
				.questionsFor(part)
				.stream()
				.flatMap(question -> question.recommendedExpressions().stream())
				.toList();
		return new DialogueTurnEvaluationPromptInput(
				"IELTS_" + part.name(),
				"IELTS Speaking " + part.name().replace('_', ' '),
				"IELTS Speaking examiner",
				"IELTS candidate",
				"Give concise corrective feedback while preserving the "
						+ "candidate's intended meaning.",
				history,
				findAiText(messages, command.turnNo()),
				command.transcript(),
				recommendedExpressions);
	}

	private AbstractSceneSession requireOwnedIeltsSession(
			String ieltsId,
			String sessionId) {
		String userId = authService.requireUserId(null);
		IeltsPracticeRecord practice = requireOwnedIeltsPractice(ieltsId);
		AbstractSceneSession session = activeSessionRegistry.findById(sessionId)
				.filter(value -> value.getSceneType() == SceneType.IELTS_SCENE)
				.orElseThrow(() -> new EvaluationException(
						EvaluationErrorCode.SESSION_NOT_FOUND));
		if (!userId.equals(practice.userId().toString())
				|| !userId.equals(session.getUserId())
				|| !ieltsId.equals(session.getSceneId())) {
			throw new EvaluationException(EvaluationErrorCode.SESSION_NOT_FOUND);
		}
		return session;
	}

	private IeltsPracticeRecord requireOwnedIeltsPractice(String ieltsId) {
		return requireOwnedIeltsPractice(
				ieltsId,
				authService.requireUserId(null));
	}

	private IeltsPracticeRecord requireOwnedIeltsPractice(
			String ieltsId,
			String userId) {
		IeltsPracticeRecord practice = ieltsPracticeRepository
				.findPractice(ieltsId)
				.orElseThrow(() -> new EvaluationException(
						EvaluationErrorCode.SESSION_NOT_FOUND));
		if (!userId.equals(practice.userId().toString())) {
			throw new EvaluationException(EvaluationErrorCode.SESSION_NOT_FOUND);
		}
		return practice;
	}

	private static final class EvaluationLock {
		private final ReentrantLock lock = new ReentrantLock();
		private int references;
	}

	private IeltsPart activeIeltsPart(
			IeltsPracticeRecord practice,
			AbstractSceneSession session) {
		if (practice.selectedPart() != null) {
			return practice.selectedPart();
		}
		if (session.getIeltsPart() != null) {
			return session.getIeltsPart();
		}
		// Compatibility fallback for active sessions created before IELTS Part
		// became immutable session metadata.
		return switch (sceneFlowService.current(practice.ieltsId())) {
			case PART1 -> IeltsPart.PART_1;
			case PART2 -> IeltsPart.PART_2;
			case PART3 -> IeltsPart.PART_3;
			case COMPLETED -> throw new EvaluationException(
					EvaluationErrorCode.SESSION_NOT_FOUND);
		};
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
			case AUDIO_REQUIRED,
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

	private boolean isProviderFeedbackFailure(EvaluationException exception) {
		return switch (exception.errorCode()) {
			case PROVIDER_NOT_CONFIGURED,
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
