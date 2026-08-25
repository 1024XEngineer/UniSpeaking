package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.timeout;

import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.IeltsTextAssessment;
import com.unispeaking.common.evaluation.model.ConversationLanguageAssessment;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.TurnLanguageFeedback;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.component.evaluation.EvaluationProcessor;
import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.SpeechEvaluationCommand;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.scene.IeltsTopic;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsContentQuestion;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.RecommendedExpression;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.config.ObjectStorageProperties;
import com.unispeaking.infrastructure.evaluation.client.EvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.IeltsEvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.PronunciationAssessmentClient;
import com.unispeaking.infrastructure.persistence.entity.evaluation.CustomTurnEvaluation;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsPartEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationWordDetail;
import com.unispeaking.infrastructure.persistence.repository.evaluation.IeltsEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.provider.ObjectStorageProvider;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvaluationProcessorReadPathTest {

	private final PronunciationAssessmentClient pronunciationClient =
			mock(PronunciationAssessmentClient.class);
	private final EvaluationLlmClient llmClient = mock(EvaluationLlmClient.class);
	private final ActiveSessionRegistry activeSessions = mock(ActiveSessionRegistry.class);
	private final SceneRepository sceneRepository = mock(SceneRepository.class);
	private final SessionMessageRepository messageRepository =
			mock(SessionMessageRepository.class);
	private final TurnEvaluationRepository turnRepository =
			mock(TurnEvaluationRepository.class);
	private final SessionEvaluationRepository reportRepository =
			mock(SessionEvaluationRepository.class);
	private final SceneSentenceReadingRepository sentenceRepository =
			mock(SceneSentenceReadingRepository.class);
	private final IeltsPracticeRepository practiceRepository =
			mock(IeltsPracticeRepository.class);
	private final IeltsRepository ieltsRepository = mock(IeltsRepository.class);
	private final IeltsSceneFlowService sceneFlowService =
			mock(IeltsSceneFlowService.class);
	private final PracticeSessionRepository practiceSessionRepository =
			mock(PracticeSessionRepository.class);
	private final IeltsEvaluationRepository ieltsEvaluationRepository =
			mock(IeltsEvaluationRepository.class);
	private final IeltsEvaluationLlmClient ieltsLlmClient =
			mock(IeltsEvaluationLlmClient.class);
	private final ObjectStorageProvider objectStorage =
			mock(ObjectStorageProvider.class);
	private final ObjectStorageProperties objectStorageProperties =
			new ObjectStorageProperties();
	private final RecordingStore recordingStore = mock(RecordingStore.class);
	private final AuthService authService = mock(AuthService.class);
	private EvaluationProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new EvaluationProcessor(
				pronunciationClient, llmClient, activeSessions, sceneRepository,
				messageRepository, turnRepository, reportRepository,
				sentenceRepository, practiceRepository,
				ieltsRepository, sceneFlowService,
				practiceSessionRepository, ieltsEvaluationRepository,
				ieltsLlmClient, authService,
				objectStorage, objectStorageProperties,
				recordingStore);
	}

	@Test
	void rejectsIeltsEvaluationWhenRequestedSessionIsNotCompletedIeltsSession() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice("ielts-1")).thenReturn(Optional.of(
				partPractice("ielts-1", userId, IeltsPart.PART_1)));
		when(practiceSessionRepository.findBySceneId("ielts-1")).thenReturn(List.of(
				practiceSession("custom-session", userId, "ielts-1",
						SceneType.CUSTOM_SCENE, SessionStatus.COMPLETED),
				practiceSession("active-ielts", userId, "ielts-1",
						SceneType.IELTS_SCENE, SessionStatus.ACTIVE)));

		EvaluationException exception = assertThrows(EvaluationException.class,
				() -> processor.generateIeltsEvaluation("ielts-1", "active-ielts"));

		assertEquals(EvaluationErrorCode.SESSION_NOT_FOUND, exception.errorCode());
		verify(ieltsEvaluationRepository, never()).findPart(any());
	}

	@Test
	void returnsCompletedDiagnosticPartFromCacheWithoutReevaluating() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice("ielts-1")).thenReturn(Optional.of(
				partPractice("ielts-1", userId, IeltsPart.PART_2)));
		when(practiceSessionRepository.findBySceneId("ielts-1")).thenReturn(List.of(
				practiceSession("session-1", userId, "ielts-1",
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED)));
		IeltsPartEvaluationEntity cached = cachedPart(
				"ielts-1", "session-1", IeltsPart.PART_2);
		when(ieltsEvaluationRepository.findPart("session-1"))
				.thenReturn(Optional.of(cached));

		var result = processor.generateIeltsEvaluation("ielts-1", "session-1");

		assertEquals(IeltsPart.PART_2, result.part());
		assertEquals("DIAGNOSTIC", result.assessmentType());
		assertEquals(new BigDecimal("7.0"), result.fluencyCoherenceScore());
		assertEquals(List.of("能够展开"), result.strengths());
		assertEquals(List.of("增加细节"), result.improvements());
		assertEquals(List.of("A clearer answer."), result.recommendedExpressions());
		verify(ieltsLlmClient, never()).assessPart(any(), any(), any(), any());
		verify(ieltsEvaluationRepository, never()).savePart(any(), any(), any());
	}

	@Test
	void aggregatesFinalMockWithPerPartFallbackWhenTextProviderFails() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "ielts-mock-1";
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(
						ieltsId, userId, IeltsMode.MOCK_TEST, null, null, "RANDOM",
						"topic-1", "topic-2", "topic-3",
						new IeltsContent(List.of(), List.of(), List.of()))));
		List<PracticeSessionRecord> sessions = List.of(
				practiceSession("session-1", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("session-2", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("session-3", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED));
		when(practiceSessionRepository.findBySceneId(ieltsId)).thenReturn(sessions);
		when(ieltsEvaluationRepository.findFinal(ieltsId)).thenReturn(Optional.empty());
		when(ieltsEvaluationRepository.findPart(any())).thenReturn(Optional.empty());
		when(ieltsLlmClient.assessPart(any(), any(), any(), any()))
				.thenThrow(new EvaluationException(EvaluationErrorCode.PROVIDER_CALL_FAILED));
		for (PracticeSessionRecord session : sessions) {
			when(messageRepository.findMessages(session.sessionId())).thenReturn(List.of(
					new Message(0, "Tell me about this topic.", null),
					new Message(1, "I can explain this topic with several details.", null)));
			when(turnRepository.findAll(session.sessionId())).thenReturn(List.of(
					scorableTurn(ieltsId, session.sessionId())));
		}

		var result = processor.generateIeltsEvaluation(ieltsId, "session-3");

		assertEquals("FINAL", result.assessmentType());
		assertEquals(3, result.partEvaluations().size());
		assertEquals(new BigDecimal("7.0"),
				result.partEvaluations().getFirst().fluencyCoherenceScore());
		assertEquals(new BigDecimal("7.0"),
				result.partEvaluations().getFirst().pronunciationScore());
		assertTrue(result.partEvaluations().getFirst().summary()
				.contains("临时诊断分"));
		assertEquals(List.of("A more natural expression."),
				result.recommendedExpressions());
		verify(ieltsEvaluationRepository).saveFinal(eq(ieltsId), eq(result));
		verify(practiceRepository).incrementCompletedCount(userId);
	}

	@Test
	void keepsPartEvaluationUnavailableWhenFallbackHasNoScorableTurns() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "ielts-mock-empty";
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(
						ieltsId, userId, IeltsMode.MOCK_TEST, null, null, "RANDOM",
						null, null, null, new IeltsContent(List.of(), List.of(), List.of()))));
		List<PracticeSessionRecord> sessions = List.of(
				practiceSession("empty-1", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("empty-2", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("empty-3", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED));
		when(practiceSessionRepository.findBySceneId(ieltsId)).thenReturn(sessions);
		when(ieltsEvaluationRepository.findFinal(ieltsId)).thenReturn(Optional.empty());
		when(ieltsEvaluationRepository.findPart(any())).thenReturn(Optional.empty());
		when(ieltsLlmClient.assessPart(any(), any(), any(), any()))
				.thenThrow(new EvaluationException(EvaluationErrorCode.PROVIDER_CALL_FAILED));
		for (PracticeSessionRecord session : sessions) {
			when(messageRepository.findMessages(session.sessionId())).thenReturn(List.of(
					new Message(1, "I answered this question.", null)));
			when(turnRepository.findAll(session.sessionId())).thenReturn(List.of());
		}

		var result = processor.generateIeltsEvaluation(ieltsId, "empty-3");

		assertEquals(3, result.partEvaluations().size());
		assertTrue(result.partEvaluations().stream()
				.allMatch(part -> part.fluencyCoherenceScore() == null
						&& part.pronunciationScore() == null));
		assertTrue(result.fluencyCoherenceReason().contains("均缺少有效"));
	}

	@Test
	void resolvesIeltsPartFromSceneFlowWhenPracticeAndSessionHaveNoPartMetadata() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "ielts-flow";
		String sessionId = "ielts-flow-session";
		CustomSceneSession session = session(sessionId, ieltsId, userId.toString());
		session.setSceneType(SceneType.IELTS_SCENE);
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(
						ieltsId, userId, IeltsMode.MOCK_TEST, null, null, "RANDOM",
						null, null, null, new IeltsContent(List.of(), List.of(), List.of()))));
		when(activeSessions.findById(sessionId)).thenReturn(Optional.of(session));
		when(sceneFlowService.current(ieltsId))
				.thenReturn(com.unispeaking.domain.vo.scene.IeltsStage.PART2);
		when(messageRepository.findMessages(sessionId)).thenReturn(List.of());
		when(turnRepository.findBefore(sessionId, 1)).thenReturn(List.of());
		when(pronunciationClient.evaluate(any(), any()))
				.thenReturn(completeAssessment());
		when(llmClient.assessTurn(any())).thenReturn(new TurnLanguageFeedback(
				"反馈", "Try a more precise expression."));

		var result = processor.evaluateIeltsTurn(ieltsId,
				new DialogueTurnEvaluationCommand(
						sessionId, 1, canonicalWav(),
						"I would like to explain this topic in several detailed sentences."));

		assertEquals("反馈", result.feedbackSummary());
		verify(llmClient).assessTurn(any());
	}

	@Test
	void evaluatesOwnedSentenceAndPersistsAttempt() {
		byte[] audio = canonicalWav();
		LearningContentItem sentence = new LearningContentItem(
				"sentence-1", "The project improved our response time.", "项目提升了响应速度", null);
		when(sentenceRepository.findSceneIdBySentenceId("sentence-1"))
				.thenReturn(Optional.of("scene-1"));
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-1", sentence)));
		when(pronunciationClient.evaluate(sentence.englishText(), audio))
				.thenReturn(new PronunciationAssessmentResult(
						new BigDecimal("80"), null, null, null, null, null,
						EndingTone.UNKNOWN, List.of()));

		var response = processor.evaluateSentenceReading("sentence-1", audio);

		assertEquals(new BigDecimal("80"), response.overallScore());
		assertTrue(response.passed());
		assertTrue(response.words().isEmpty());
		verify(sentenceRepository).saveAttempt("scene-1", sentence,
				new PronunciationAssessmentResult(
						new BigDecimal("80"), null, null, null, null, null,
						EndingTone.UNKNOWN, List.of()));
	}

	@Test
	void rejectsSentenceOutsideCurrentUsersSceneBeforeCallingProvider() {
		when(sentenceRepository.findSceneIdBySentenceId("sentence-1"))
				.thenReturn(Optional.of("scene-1"));
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-2", null)));

		EvaluationException exception = assertThrows(EvaluationException.class,
				() -> processor.evaluateSentenceReading("sentence-1", canonicalWav()));

		assertEquals(EvaluationErrorCode.SENTENCE_NOT_FOUND, exception.errorCode());
		verify(pronunciationClient, never()).evaluate(any(), any());
	}

	@Test
	void returnsPersistedDialogueAndMapsTurnScoresForOwnedScene() {
		when(messageRepository.findSceneId("session-1")).thenReturn(Optional.of("scene-1"));
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-1", null)));
		when(messageRepository.findMessages("session-1"))
				.thenReturn(List.of(new Message(1, "I improved the service latency.", null)));
		when(turnRepository.findAll("session-1")).thenReturn(List.of(turn()));

		var result = processor.getDialogueEvaluation("session-1");

		assertEquals(1, result.dialogue().size());
		assertEquals(1, result.turnEvaluation().size());
		assertEquals("I improved the service latency.",
				result.turnEvaluation().getFirst().transcript());
		assertEquals(new BigDecimal("82"), result.turnEvaluation().getFirst().overallScore());
	}

	@Test
	void rejectsDialogueReadWhenNoPersistedSceneCanBeFound() {
		when(messageRepository.findSceneId("missing")).thenReturn(Optional.empty());

		EvaluationException exception = assertThrows(EvaluationException.class,
				() -> processor.getDialogueEvaluation("missing"));

		assertEquals(EvaluationErrorCode.RESULT_NOT_FOUND, exception.errorCode());
	}

	@Test
	void rejectsDialogueReadWhenOwnedSceneHasNeitherMessagesNorTurns() {
		when(messageRepository.findSceneId("session-1")).thenReturn(Optional.of("scene-1"));
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-1", null)));
		when(messageRepository.findMessages("session-1")).thenReturn(List.of());
		when(turnRepository.findAll("session-1")).thenReturn(List.of());

		EvaluationException exception = assertThrows(EvaluationException.class,
				() -> processor.getDialogueEvaluation("session-1"));

		assertEquals(EvaluationErrorCode.RESULT_NOT_FOUND, exception.errorCode());
	}

	@Test
	void persistsUnavailableResultWhenOwnedDialogueHasNoAudio() {
		CustomSceneSession session = session("session-1", "scene-1", "user-1");
		when(activeSessions.findById("session-1")).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-1", null)));

		var result = processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
				"session-1", 1, null,
				"I designed the cache to reduce repeated database reads."));

		assertEquals(1, result.turnNo());
		assertFalse(result.feedbackSummary().isBlank());
		verify(turnRepository).upsert(any(CustomTurnEvaluation.class));
		verify(pronunciationClient, never()).evaluate(any(), any());
		verify(llmClient, never()).assessTurn(any());
	}

	@Test
	void mapsInvalidDialogueAudioToDurableUnavailableResult() {
		CustomSceneSession session = session("session-1", "scene-1", "user-1");
		when(activeSessions.findById("session-1")).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-1", null)));

		var result = processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
				"session-1", 1, new byte[]{1, 2, 3},
				"I designed the cache to reduce repeated database reads."));

		assertEquals("本轮评分暂不可用，已保留对话内容", result.feedbackSummary());
		verify(turnRepository).upsert(any(CustomTurnEvaluation.class));
		verify(pronunciationClient, never()).evaluate(any(), any());
	}

	@Test
	void persistsTooShortDialogueWithoutCallingSpeechOrLanguageProviders() {
		CustomSceneSession session = session("session-1", "scene-1", "user-1");
		when(activeSessions.findById("session-1")).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-1", null)));

		var result = processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
				"session-1", 1, canonicalWav(), "Hello"));

		assertEquals(1, result.turnNo());
		assertFalse(result.feedbackSummary().isBlank());
		verify(turnRepository).upsert(any(CustomTurnEvaluation.class));
		verify(pronunciationClient, never()).evaluate(any(), any());
		verify(llmClient, never()).assessTurn(any());
	}

	@Test
	void rejectsDialogueTurnWhenRuntimeSessionIsMissing() {
		when(activeSessions.findById("missing")).thenReturn(Optional.empty());

		EvaluationException exception = assertThrows(EvaluationException.class,
				() -> processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
						"missing", 1, null,
						"I designed the cache to reduce repeated database reads.")));

		assertEquals(EvaluationErrorCode.SESSION_NOT_FOUND, exception.errorCode());
		verify(turnRepository, never()).upsert(any());
	}

	@Test
	void rejectsUnknownSentenceBeforeResolvingSceneOwnership() {
		when(sentenceRepository.findSceneIdBySentenceId("missing"))
				.thenReturn(Optional.empty());

		EvaluationException exception = assertThrows(EvaluationException.class,
				() -> processor.evaluateSentenceReading("missing", canonicalWav()));

		assertEquals(EvaluationErrorCode.SENTENCE_NOT_FOUND, exception.errorCode());
		verify(sceneRepository, never()).findCustomDefinitionById(any());
	}

	@Test
	void preservesIeltsTurnWhenRecordingAttachmentFailsAndCleansStoredAudio() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "ielts-1";
		CustomSceneSession session = session("session-ielts", ieltsId, userId.toString());
		session.setSceneType(SceneType.IELTS_SCENE);
		when(activeSessions.findById("session-ielts")).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(ieltsId, userId, IeltsMode.PART_PRACTICE,
						IeltsPart.PART_1, "topic-1",
						new IeltsContent(List.of(), List.of(), List.of()))));
		byte[] audio = canonicalWav();
		when(recordingStore.store("session-ielts", 1, audio))
				.thenReturn("https://audio.example/session-ielts.wav");
		org.mockito.Mockito.doThrow(new IllegalStateException("write failed"))
				.when(messageRepository).attachLearnerAudioUrl(
						"session-ielts", 1,
						"https://audio.example/session-ielts.wav");

		var result = processor.evaluateIeltsTurn(ieltsId,
				new DialogueTurnEvaluationCommand("session-ielts", 1, audio, "Hello"));

		assertEquals(1, result.turnNo());
		verify(turnRepository).upsert(any(CustomTurnEvaluation.class));
		verify(recordingStore).store("session-ielts", 1, audio);
		verify(recordingStore).delete("session-ielts", 1);
	}

	@Test
	void returnsFullDialogueTurnFeedbackAndPersistsDetailedPronunciation() {
		byte[] audio = canonicalWav();
		CustomSceneSession session = session("session-1", "scene-1", "user-1");
		when(activeSessions.findById("session-1")).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-1", null)));
		when(messageRepository.findMessages("session-1")).thenReturn(List.of(
				new Message(0, "Please describe your database optimization work.", null)));
		when(turnRepository.findBefore("session-1", 1)).thenReturn(List.of());
		when(pronunciationClient.evaluate(
				"I added an index and reduced repeated database reads.", audio))
				.thenReturn(completeAssessment());
		when(llmClient.assessTurn(any())).thenReturn(new TurnLanguageFeedback(
				"表达清晰，可以补充量化结果。", "I reduced database reads by adding an index."));

		var result = processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
				"session-1", 1, audio,
				"I added an index and reduced repeated database reads."));

		assertEquals(new BigDecimal("86"), result.overallScore());
		assertEquals("表达清晰，可以补充量化结果。", result.feedbackSummary());
		assertEquals(1, result.words().size());
		verify(turnRepository).upsert(any(CustomTurnEvaluation.class));
	}

	@Test
	void generatesDialogueReportFromStoredSpeechAndLanguageAssessment() {
		CustomSceneSession session = session("session-1", "scene-1", "user-1");
		when(activeSessions.findById("session-1")).thenReturn(Optional.of(session));
		when(reportRepository.find("session-1")).thenReturn(Optional.empty());
		List<Message> dialogue = List.of(
				new Message(0, "What did you improve?", null),
				new Message(1, "I added an index and reduced repeated database reads.", null));
		when(turnRepository.findAll("session-1")).thenReturn(List.of(turn()));
		when(llmClient.assessDialogue(dialogue)).thenReturn(
				new ConversationLanguageAssessment(new BigDecimal("88"),
						new BigDecimal("84"), new BigDecimal("86"), "报告已生成",
						List.of("结构清晰"), List.of("补充量化结果")));

		var report = processor.generateDialogueReport("session-1", dialogue);

		assertEquals("报告已生成", report.summary());
		assertEquals(new BigDecimal("88.0"), report.grammarScore());
		assertEquals(new BigDecimal("84.0"), report.vocabularyScore());
		assertTrue(report.finalScore().compareTo(BigDecimal.ZERO) > 0);
		verify(reportRepository).save(eq("scene-1"), eq("session-1"), eq(report));
	}

	@Test
	void mapsRecoverableIeltsTurnFailureToUnavailableResult() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "ielts-1";
		CustomSceneSession session = session("session-ielts", ieltsId, userId.toString());
		session.setSceneType(SceneType.IELTS_SCENE);
		when(activeSessions.findById("session-ielts")).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(ieltsId, userId, IeltsMode.PART_PRACTICE,
						IeltsPart.PART_1, "topic-1",
						new IeltsContent(List.of(), List.of(), List.of()))));

		var result = processor.evaluateIeltsTurn(ieltsId,
				new DialogueTurnEvaluationCommand("session-ielts", 1,
						new byte[]{1, 2, 3},
						"I added an index and reduced repeated database reads."));

		assertEquals("本轮评分暂不可用，已保留对话内容", result.feedbackSummary());
		verify(turnRepository).upsert(any(CustomTurnEvaluation.class));
		verify(pronunciationClient, never()).evaluate(any(), any());
	}

	@Test
	void evaluatesSpeechAndTrimsReferenceBeforeCallingProvider() {
		when(pronunciationClient.evaluate(eq("hello world"), any()))
				.thenReturn(completeAssessment());

		var result = processor.evaluateSpeech(
				new SpeechEvaluationCommand("  hello world  ", canonicalWav()));

		assertTrue(result.accuracyScore().compareTo(BigDecimal.ZERO) > 0);
		assertTrue(result.fluencyScore().compareTo(BigDecimal.ZERO) > 0);
		verify(pronunciationClient).evaluate(eq("hello world"), any());
	}

	@Test
	void rejectsSpeechRequestsBeforeProviderForMissingReferenceOrInvalidAudio() {
		EvaluationException missing = assertThrows(EvaluationException.class,
				() -> processor.evaluateSpeech(new SpeechEvaluationCommand(" ", canonicalWav())));
		assertEquals(EvaluationErrorCode.INVALID_REQUEST, missing.errorCode());

		EvaluationException invalidAudio = assertThrows(EvaluationException.class,
				() -> processor.evaluateSpeech(new SpeechEvaluationCommand("hello", new byte[]{1, 2})));
		assertEquals(EvaluationErrorCode.AUDIO_UNSUPPORTED, invalidAudio.errorCode());
		verify(pronunciationClient, never()).evaluate(any(), any());
	}

	@Test
	void returnsCachedDialogueReportWithoutCallingProvidersOrSavingAgain() {
		CustomSceneSession session = session("session-1", "scene-1", "user-1");
		when(activeSessions.findById("session-1")).thenReturn(Optional.of(session));
		var cached = new com.unispeaking.domain.dto.evaluation.DialogueReportResult(
				new BigDecimal("80"), new BigDecimal("81"), new BigDecimal("82"),
				new BigDecimal("83"), new BigDecimal("84"), new BigDecimal("83"),
				"cached", List.of(), List.of());
		when(reportRepository.find("session-1")).thenReturn(Optional.of(cached));

		var result = processor.generateDialogueReport("session-1", List.of(
				new Message(1, "already evaluated", null)));

		assertEquals(cached, result);
		verify(llmClient, never()).assessDialogue(any());
		verify(reportRepository, never()).save(any(), any(), any());
	}

	@Test
	void persistsUnavailableReportWhenNoScorableTurnsExist() {
		CustomSceneSession session = session("session-1", "scene-1", "user-1");
		when(activeSessions.findById("session-1")).thenReturn(Optional.of(session));
		when(reportRepository.find("session-1")).thenReturn(Optional.empty());
		when(turnRepository.findAll("session-1")).thenReturn(List.of());
		List<Message> dialogue = List.of(new Message(1, "short", null));

		var result = processor.generateDialogueReport("session-1", dialogue);

		assertFalse(result.summary().isBlank());
		verify(reportRepository).save(eq("scene-1"), eq("session-1"), eq(result));
		verify(llmClient, never()).assessDialogue(any());
	}

	@Test
	void fallsBackToPersistedTurnScoresWhenLanguageProviderIsUnavailable() {
		CustomSceneSession session = session("session-1", "scene-1", "user-1");
		when(activeSessions.findById("session-1")).thenReturn(Optional.of(session));
		when(reportRepository.find("session-1")).thenReturn(Optional.empty());
		when(turnRepository.findAll("session-1")).thenReturn(List.of(turn()));
		List<Message> dialogue = List.of(
				new Message(0, "What improved?", null),
				new Message(1, "I improved latency.", null));
		when(llmClient.assessDialogue(dialogue))
				.thenThrow(new EvaluationException(EvaluationErrorCode.PROVIDER_CALL_FAILED));

		var result = processor.generateDialogueReport("session-1", dialogue);

		assertTrue(result.summary().contains("语言模型文字报告暂不可用"));
		assertTrue(result.finalScore().compareTo(BigDecimal.ZERO) > 0);
		verify(reportRepository).save(eq("scene-1"), eq("session-1"), eq(result));
	}

	@Test
	void validatesDialogueBeforeLookingUpRuntimeSession() {
		EvaluationException invalid = assertThrows(EvaluationException.class,
				() -> processor.generateDialogueReport("session-1", List.of()));
		assertEquals(EvaluationErrorCode.SESSION_NOT_FOUND, invalid.errorCode());
		verify(activeSessions).findById(any());
	}

	@Test
	void rejectsNullOrNonPositiveIeltsTurnCommandsBeforeSessionLookup() {
		EvaluationException nullCommand = assertThrows(EvaluationException.class,
				() -> processor.evaluateIeltsTurn("ielts-1", null));
		assertEquals(EvaluationErrorCode.INVALID_REQUEST, nullCommand.errorCode());
		EvaluationException invalidTurn = assertThrows(EvaluationException.class,
				() -> processor.evaluateIeltsTurn("ielts-1",
						new DialogueTurnEvaluationCommand("session-1", 0, null, "hello")));
		assertEquals(EvaluationErrorCode.INVALID_REQUEST, invalidTurn.errorCode());
		verify(activeSessions, never()).findById(any());
	}

	@Test
	void evaluatesAndCachesPartPracticeWithPartTwoCueCard() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "ielts-part-two";
		String sessionId = "part-two-session";
		IeltsContent content = new IeltsContent(
				List.of(),
				List.of(new IeltsContentQuestion(
						"Describe a useful object.",
						List.of("what it is", "why it is useful"),
						List.of())),
				List.of());
		IeltsPracticeRecord practice = new IeltsPracticeRecord(
				ieltsId, userId, IeltsMode.PART_PRACTICE, IeltsPart.PART_2,
				"topic-2", content);
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(practice));
		when(practiceSessionRepository.findBySceneId(ieltsId)).thenReturn(List.of(
				practiceSession(sessionId, userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED)));
		when(ieltsEvaluationRepository.findPart(sessionId)).thenReturn(Optional.empty());
		when(messageRepository.findMessages(sessionId)).thenReturn(List.of(
				new Message(0, "Describe a useful object.", null),
				new Message(1, "This laptop helps me study every day.", null)));
		when(turnRepository.findAll(sessionId)).thenReturn(List.of(scorableTurn(ieltsId, sessionId)));
		IeltsTextAssessment assessment = new IeltsTextAssessment(
				IeltsPart.PART_2, new BigDecimal("7.0"), new BigDecimal("6.5"),
				new BigDecimal("6.0"), "流利", "词汇", "语法", "分析完成",
				List.of("展开自然"), List.of("补充例子"), "HIGH");
		when(ieltsLlmClient.assessPart(any(), any(), any(), any()))
				.thenReturn(assessment);

		var result = processor.generateIeltsEvaluation(ieltsId, sessionId);

		assertAll(
				() -> assertEquals(IeltsPart.PART_2, result.part()),
				() -> assertEquals(new BigDecimal("7.0"), result.fluencyCoherenceScore()),
				() -> assertEquals(new BigDecimal("7.0"), result.pronunciationScore()),
				() -> assertTrue(result.summary().contains("分析完成")));
		verify(ieltsEvaluationRepository).savePart(ieltsId, sessionId, result);
		verify(practiceRepository).incrementCompletedCount(userId);
	}

	@Test
	void derivesPartFromCompletedSessionOrderWhenPracticeHasNoSelectedPart() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "ielts-derived-part";
		String sessionId = "derived-part-session";
		IeltsContent content = new IeltsContent(
				List.of(),
				List.of(new IeltsContentQuestion(
						"Describe a useful object.", List.of(), List.of())),
				List.of());
		IeltsPracticeRecord practice = new IeltsPracticeRecord(
				ieltsId, userId, IeltsMode.MOCK_TEST, null, null, "RANDOM",
				"topic-1", "topic-2", "topic-3", content);
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(practice));
		when(practiceSessionRepository.findBySceneId(ieltsId)).thenReturn(List.of(
				practiceSession("first-session", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession(sessionId, userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED)));
		when(ieltsEvaluationRepository.findPart(sessionId)).thenReturn(Optional.empty());
		when(messageRepository.findMessages(sessionId)).thenReturn(List.of(
				new Message(1, "This laptop helps me study every day.", null)));
		when(turnRepository.findAll(sessionId)).thenReturn(List.of());
		when(ieltsLlmClient.assessPart(eq(IeltsPart.PART_2), any(), eq("Describe a useful object."), eq(null)))
				.thenReturn(new IeltsTextAssessment(
						IeltsPart.PART_2, new BigDecimal("6.5"), new BigDecimal("6.0"),
						new BigDecimal("6.0"), "流利", "词汇", "语法", "按顺序推导",
						List.of(), List.of(), "LOW"));

		var result = processor.generateIeltsEvaluation(ieltsId, sessionId);

		assertEquals(IeltsPart.PART_2, result.part());
		verify(ieltsLlmClient).assessPart(
				eq(IeltsPart.PART_2), any(), eq("Describe a useful object."), eq(null));
	}

	@Test
	void formatsPartTwoCueCardWithoutCuePoints() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "ielts-empty-cue-points";
		String sessionId = "empty-cue-session";
		IeltsPracticeRecord practice = new IeltsPracticeRecord(
				ieltsId, userId, IeltsMode.PART_PRACTICE, IeltsPart.PART_2,
				"topic-2", new IeltsContent(
						List.of(),
						List.of(new IeltsContentQuestion(
								"Talk about a memorable meal.", List.of(), List.of())),
						List.of()));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(practice));
		when(practiceSessionRepository.findBySceneId(ieltsId)).thenReturn(List.of(
				practiceSession(sessionId, userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED)));
		when(ieltsEvaluationRepository.findPart(sessionId)).thenReturn(Optional.empty());
		when(messageRepository.findMessages(sessionId)).thenReturn(List.of(
				new Message(0, "Talk about a memorable meal.", null),
				new Message(1, "I remember a family dinner last summer.", null)));
		when(turnRepository.findAll(sessionId)).thenReturn(List.of());
		when(ieltsLlmClient.assessPart(
				eq(IeltsPart.PART_2), any(), eq("Talk about a memorable meal."), eq(null)))
				.thenReturn(new IeltsTextAssessment(
						IeltsPart.PART_2, new BigDecimal("6.5"), new BigDecimal("6.0"),
						new BigDecimal("6.0"), "流利", "词汇", "语法", "无提示点",
						List.of(), List.of(), "LOW"));

		var result = processor.generateIeltsEvaluation(ieltsId, sessionId);

		assertTrue(result.summary().startsWith("无提示点"));
	}

	@Test
	void rejectsIeltsPartWithoutCandidateAnswerBeforeCallingTextProvider() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "ielts-no-answer";
		String sessionId = "no-answer-session";
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				partPractice(ieltsId, userId, IeltsPart.PART_1)));
		when(practiceSessionRepository.findBySceneId(ieltsId)).thenReturn(List.of(
				practiceSession(sessionId, userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED)));
		when(ieltsEvaluationRepository.findPart(sessionId)).thenReturn(Optional.empty());
		when(messageRepository.findMessages(sessionId)).thenReturn(List.of(
				new Message(0, "Please answer this question.", null)));

		EvaluationException exception = assertThrows(EvaluationException.class,
				() -> processor.generateIeltsEvaluation(ieltsId, sessionId));

		assertEquals(EvaluationErrorCode.NO_SCORABLE_UTTERANCES, exception.errorCode());
		verify(ieltsLlmClient, never()).assessPart(any(), any(), any(), any());
	}

	@Test
	void preservesIeltsTranscriptWhenAudioIsMissing() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		CustomSceneSession session = session("missing-audio-ielts", "ielts-1", userId.toString());
		session.setSceneType(SceneType.IELTS_SCENE);
		when(activeSessions.findById(session.getId())).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice("ielts-1")).thenReturn(Optional.of(
				partPractice("ielts-1", userId, IeltsPart.PART_1)));

		var result = processor.evaluateIeltsTurn("ielts-1",
				new DialogueTurnEvaluationCommand(
						session.getId(), 1, null,
						"I would like to explain this topic in several detailed sentences."));

		assertEquals("本轮评分暂不可用，已保留对话内容", result.feedbackSummary());
		verify(turnRepository).upsert(any(CustomTurnEvaluation.class));
		verify(pronunciationClient, never()).evaluate(any(), any());
	}

	@Test
	void propagatesNonRecoverableIeltsPronunciationFailure() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		CustomSceneSession session = session("nonrecoverable-ielts", "ielts-1", userId.toString());
		session.setSceneType(SceneType.IELTS_SCENE);
		when(activeSessions.findById(session.getId())).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice("ielts-1")).thenReturn(Optional.of(
				partPractice("ielts-1", userId, IeltsPart.PART_1)));
		when(pronunciationClient.evaluate(any(), any()))
				.thenThrow(new EvaluationException(EvaluationErrorCode.INVALID_REQUEST));
		when(recordingStore.store(eq(session.getId()), eq(1), any()))
				.thenReturn("https://audio.example/nonrecoverable.wav");

		EvaluationException exception = assertThrows(EvaluationException.class,
				() -> processor.evaluateIeltsTurn("ielts-1",
						new DialogueTurnEvaluationCommand(
								session.getId(), 1, canonicalWav(),
								"I would like to explain this topic in several detailed sentences.")));

		assertEquals(EvaluationErrorCode.INVALID_REQUEST, exception.errorCode());
		verify(turnRepository, never()).upsert(any());
	}

	@Test
	void rejectsIeltsTurnWhenLegacySessionFlowIsAlreadyCompleted() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		CustomSceneSession session = session("completed-flow-ielts", "ielts-1", userId.toString());
		session.setSceneType(SceneType.IELTS_SCENE);
		when(activeSessions.findById(session.getId())).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice("ielts-1")).thenReturn(Optional.of(
				new IeltsPracticeRecord(
						"ielts-1", userId, IeltsMode.MOCK_TEST, null, null, "RANDOM",
						null, null, null, new IeltsContent(List.of(), List.of(), List.of()))));
		when(pronunciationClient.evaluate(any(), any())).thenReturn(completeAssessment());
		when(sceneFlowService.current("ielts-1"))
				.thenReturn(com.unispeaking.domain.vo.scene.IeltsStage.COMPLETED);
		when(recordingStore.store(eq(session.getId()), eq(1), any()))
				.thenReturn("https://audio.example/completed.wav");

		EvaluationException exception = assertThrows(EvaluationException.class,
				() -> processor.evaluateIeltsTurn("ielts-1",
						new DialogueTurnEvaluationCommand(
								session.getId(), 1, canonicalWav(),
								"I would like to explain this topic in several detailed sentences.")));

		assertEquals(EvaluationErrorCode.SESSION_NOT_FOUND, exception.errorCode());
			verify(pronunciationClient, never()).evaluate(any(), any());
	}

	@Test
	void ignoresIncompleteCachedPartAndReevaluatesIt() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "ielts-incomplete-cache";
		String sessionId = "incomplete-cache-session";
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				partPractice(ieltsId, userId, IeltsPart.PART_1)));
		when(practiceSessionRepository.findBySceneId(ieltsId)).thenReturn(List.of(
				practiceSession(sessionId, userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED)));
		IeltsPartEvaluationEntity incomplete = cachedPart(ieltsId, sessionId, IeltsPart.PART_1);
		incomplete.setEvaluationStatus("FAILED");
		when(ieltsEvaluationRepository.findPart(sessionId)).thenReturn(Optional.of(incomplete));
		when(messageRepository.findMessages(sessionId)).thenReturn(List.of(
				new Message(1, "I enjoy reading books.", null)));
		when(turnRepository.findAll(sessionId)).thenReturn(List.of());
		when(ieltsLlmClient.assessPart(any(), any(), eq(null), eq(null)))
				.thenReturn(new IeltsTextAssessment(
						IeltsPart.PART_1, new BigDecimal("6.5"), new BigDecimal("6.0"),
						new BigDecimal("6.0"), "流利", "词汇", "语法", "重新评估",
						List.of(), List.of(), "MEDIUM"));

		var result = processor.generateIeltsEvaluation(ieltsId, sessionId);

		assertTrue(result.summary().startsWith("重新评估"));
		verify(ieltsLlmClient).assessPart(any(), any(), eq(null), eq(null));
		verify(ieltsEvaluationRepository).savePart(ieltsId, sessionId, result);
	}

	@Test
	void returnsCompletedCachedFinalMockEvaluationAndMapsPartResults() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "ielts-cached-final";
		List<PracticeSessionRecord> sessions = List.of(
				practiceSession("cached-1", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("cached-2", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("cached-3", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(ieltsId, userId, IeltsMode.MOCK_TEST, null, null,
						"RANDOM", "topic-1", "topic-2", "topic-3",
						new IeltsContent(List.of(), List.of(), List.of()))));
		when(practiceSessionRepository.findBySceneId(ieltsId)).thenReturn(sessions);
		IeltsEvaluationEntity cached = cachedFinal(ieltsId);
		when(ieltsEvaluationRepository.findFinal(ieltsId)).thenReturn(Optional.of(cached));
		when(ieltsEvaluationRepository.findParts(ieltsId)).thenReturn(List.of(
				cachedPart(ieltsId, "cached-1", IeltsPart.PART_1)));

		var result = processor.generateIeltsEvaluation(ieltsId, "cached-3");

		assertAll(
				() -> assertEquals("FINAL", result.assessmentType()),
				() -> assertEquals(new BigDecimal("7.0"), result.overallBandScore()),
				() -> assertEquals(1, result.partEvaluations().size()),
				() -> assertEquals(List.of("优势"), result.strengths()),
				() -> assertEquals(List.of("改进"), result.improvements()),
				() -> assertEquals(List.of("Use detail."), result.recommendedExpressions()));
		verify(ieltsEvaluationRepository, never()).saveFinal(any(), any());
		verify(ieltsLlmClient, never()).assessPart(any(), any(), any(), any());
	}

	@Test
	void buildsIeltsHistoryWithMockAndPartRecordsAndSignedRecordingFallback() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String mockId = "history-mock";
		String partId = "history-part";
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceSessionRepository.findCompletedByUserAndSceneType(
				eq(userId), eq(SceneType.IELTS_SCENE))).thenReturn(List.of(
				practiceSession("history-mock-1", userId, mockId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("history-mock-2", userId, mockId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("history-mock-3", userId, mockId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("history-part-1", userId, partId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED)));
		IeltsPracticeRecord mockPractice = new IeltsPracticeRecord(
				mockId, userId, IeltsMode.MOCK_TEST, null, null, "RANDOM",
				"topic-1", "topic-2", "topic-missing", new IeltsContent(List.of(), List.of(), List.of()));
		IeltsPracticeRecord partPractice = partPractice(partId, userId, IeltsPart.PART_1);
		when(practiceRepository.findPractice(mockId)).thenReturn(Optional.of(mockPractice));
		when(practiceRepository.findPractice(partId)).thenReturn(Optional.of(partPractice));
		when(ieltsRepository.findTopicsByIds(any())).thenReturn(List.of(
				new IeltsTopic("topic-1", "Work", null, null, null, null),
				new IeltsTopic("topic-2", "Travel", null, null, null, null)));
		when(ieltsEvaluationRepository.findFinal(mockId)).thenReturn(Optional.of(cachedFinal(mockId)));
		when(ieltsEvaluationRepository.findParts(mockId)).thenReturn(List.of(
				cachedPart(mockId, "history-mock-1", IeltsPart.PART_1)));
		when(ieltsEvaluationRepository.findPart("history-part-1"))
				.thenReturn(Optional.of(cachedPart(partId, "history-part-1", IeltsPart.PART_1)));
		when(messageRepository.findAudioUrls(any())).thenReturn(List.of());
		when(objectStorage.available()).thenReturn(true);
		when(messageRepository.findAudioObjectKeys(any())).thenReturn(List.of("audio/key.wav"));
		when(objectStorage.signGetUrl(eq("audio/key.wav"), any()))
				.thenReturn(URI.create("https://cdn.example/audio.wav"));

		var history = processor.getIeltsEvaluationHistory();

		assertEquals(2, history.size());
		assertAll(
				() -> assertEquals("FINAL", history.get(0).assessmentType()),
				() -> assertEquals("DIAGNOSTIC", history.get(1).assessmentType()),
				() -> assertEquals("Work", history.get(0).topicTitles().get(IeltsPart.PART_1)),
				() -> assertEquals("topic-missing", history.get(0).topicTitles().get(IeltsPart.PART_3)),
				() -> assertEquals(
						List.of("https://cdn.example/audio.wav",
								"https://cdn.example/audio.wav",
								"https://cdn.example/audio.wav"),
						history.get(0).recordingUrls()));
	}

	@Test
	void handlesUnavailableStorageAndSigningFailuresWhenReadingHistory() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "history-storage";
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceSessionRepository.findCompletedByUserAndSceneType(eq(userId), eq(SceneType.IELTS_SCENE)))
				.thenReturn(List.of(practiceSession("storage-session", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED)));
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				partPractice(ieltsId, userId, IeltsPart.PART_1)));
		when(ieltsRepository.findTopicsByIds(any())).thenReturn(List.of());
		when(ieltsEvaluationRepository.findPart("storage-session"))
				.thenReturn(Optional.of(cachedPart(ieltsId, "storage-session", IeltsPart.PART_1)));
		when(messageRepository.findAudioUrls("storage-session")).thenReturn(List.of());
		when(objectStorage.available()).thenReturn(true);
		when(messageRepository.findAudioObjectKeys("storage-session"))
				.thenReturn(List.of("bad-key", "good-key"));
		when(objectStorage.signGetUrl(eq("bad-key"), any()))
				.thenThrow(new IllegalStateException("sign failed"));
		when(objectStorage.signGetUrl(eq("good-key"), any()))
				.thenReturn(URI.create("https://cdn.example/good.wav"));

		var history = processor.getIeltsEvaluationHistory();

		assertEquals(List.of("https://cdn.example/good.wav"), history.getFirst().recordingUrls());
	}

	@Test
	void storesAndAttachesValidIeltsRecordingAfterUnavailableScoring() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		CustomSceneSession session = session("session-ielts", "ielts-1", userId.toString());
		session.setSceneType(SceneType.IELTS_SCENE);
		when(activeSessions.findById("session-ielts")).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice("ielts-1")).thenReturn(Optional.of(
				new IeltsPracticeRecord("ielts-1", userId, IeltsMode.PART_PRACTICE,
						IeltsPart.PART_1, "topic-1", new IeltsContent(List.of(), List.of(), List.of()))));
		byte[] audio = canonicalWav();
		when(recordingStore.store("session-ielts", 1, audio)).thenReturn("https://audio.example/turn.wav");

		var result = processor.evaluateIeltsTurn("ielts-1",
				new DialogueTurnEvaluationCommand("session-ielts", 1, audio, "Hello"));

		assertEquals(1, result.turnNo());
		verify(recordingStore).store("session-ielts", 1, audio);
		verify(messageRepository).attachLearnerAudioUrl("session-ielts", 1, "https://audio.example/turn.wav");
		verify(recordingStore, never()).delete(any(), anyInt());
	}

	@Test
	void rejectsBlankTranscriptAndInvalidTurnNumberBeforeProvider() {
		EvaluationException blank = assertThrows(EvaluationException.class,
				() -> processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
						"session-1", 1, null, "  ")));
		assertEquals(EvaluationErrorCode.TRANSCRIPT_REQUIRED, blank.errorCode());
		EvaluationException zero = assertThrows(EvaluationException.class,
				() -> processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
						"session-1", 0, null, "hello")));
		assertEquals(EvaluationErrorCode.INVALID_REQUEST, zero.errorCode());
		verify(activeSessions, never()).findById(any());
	}

	@Test
	void rejectsNullAndMalformedDialogueMessagesBeforeReadingOrScoring() {
		CustomSceneSession session = session("invalid-dialogue", "scene-1", "user-1");
		when(activeSessions.findById(session.getId())).thenReturn(Optional.of(session));

		EvaluationException nullOwner = assertThrows(EvaluationException.class,
				() -> processor.generateDialogueReport(session.getId(), List.of(
						new Message(null, "answer", null))));
		assertEquals(EvaluationErrorCode.INVALID_REQUEST, nullOwner.errorCode());

		EvaluationException nullContent = assertThrows(EvaluationException.class,
				() -> processor.generateDialogueReport(session.getId(), List.of(
						new Message(1, null, null))));
		assertEquals(EvaluationErrorCode.INVALID_REQUEST, nullContent.errorCode());

		EvaluationException blankContent = assertThrows(EvaluationException.class,
				() -> processor.generateDialogueReport(session.getId(), List.of(
						new Message(1, "  ", null))));
		assertEquals(EvaluationErrorCode.INVALID_REQUEST, blankContent.errorCode());
		verify(reportRepository, never()).find(any());
	}

	@Test
	void persistsUnavailableCustomTurnWhenPronunciationProviderFails() {
		CustomSceneSession session = session("custom-provider-failure", "scene-1", "user-1");
		when(activeSessions.findById(session.getId())).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-1", null)));
		when(pronunciationClient.evaluate(any(), any()))
				.thenThrow(new EvaluationException(EvaluationErrorCode.PROVIDER_CALL_FAILED));

		var result = processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
				session.getId(), 1, canonicalWav(),
				"I can explain this complete answer with several details."));

		assertEquals("本轮评分暂不可用，已保留对话内容", result.feedbackSummary());
		verify(turnRepository).upsert(any(CustomTurnEvaluation.class));
			verify(llmClient, timeout(1000)).assessTurn(any());
	}

	@Test
	void propagatesNonRecoverableCustomFeedbackFailureWithoutPersistingResult() {
		CustomSceneSession session = session("custom-feedback-failure", "scene-1", "user-1");
		when(activeSessions.findById(session.getId())).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-1", null)));
		when(pronunciationClient.evaluate(any(), any())).thenReturn(completeAssessment());
		when(llmClient.assessTurn(any()))
				.thenThrow(new EvaluationException(EvaluationErrorCode.INVALID_REQUEST));

		EvaluationException exception = assertThrows(EvaluationException.class,
				() -> processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
						session.getId(), 1, canonicalWav(),
						"I can explain this complete answer with several details.")));

		assertEquals(EvaluationErrorCode.INVALID_REQUEST, exception.errorCode());
		verify(turnRepository, never()).upsert(any(CustomTurnEvaluation.class));
	}

	@Test
	void buildsCustomPromptHistoryAndRejectsMissingSceneDefinition() {
		CustomSceneSession session = session("custom-history", "scene-1", "user-1");
		when(activeSessions.findById(session.getId())).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-1", null)));
		when(messageRepository.findMessages(session.getId())).thenReturn(List.of(
				new Message(0, "Earlier question", null),
				new Message(1, "Earlier answer with enough words", null),
				new Message(0, "Current question", null)));
		when(turnRepository.findBefore(session.getId(), 2)).thenReturn(List.of(
				scorableTurn("scene-1", session.getId())));
		when(pronunciationClient.evaluate(any(), any())).thenReturn(completeAssessment());
		when(llmClient.assessTurn(any())).thenReturn(new TurnLanguageFeedback(
				"历史上下文已构造", "Use a more precise expression."));

		var result = processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
				session.getId(), 2, canonicalWav(),
				"I can explain the current question with enough detail."));

		assertEquals("历史上下文已构造", result.feedbackSummary());
		verify(llmClient).assessTurn(any());

		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.empty());
		EvaluationException missingScene = assertThrows(EvaluationException.class,
				() -> processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
						session.getId(), 3, canonicalWav(),
						"I can explain another complete answer with details.")));
		assertEquals(EvaluationErrorCode.SESSION_NOT_FOUND, missingScene.errorCode());
	}

	@Test
	void rejectsIeltsBlankTranscriptAndNonRecoverableLanguageFeedbackFailure() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		CustomSceneSession session = session("ielts-nonrecoverable", "ielts-1", userId.toString());
		session.setSceneType(SceneType.IELTS_SCENE);
		session.setIeltsPart(IeltsPart.PART_1);
		when(activeSessions.findById(session.getId())).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice("ielts-1")).thenReturn(Optional.of(
				partPractice("ielts-1", userId, IeltsPart.PART_1)));

		EvaluationException blank = assertThrows(EvaluationException.class,
				() -> processor.evaluateIeltsTurn("ielts-1",
						new DialogueTurnEvaluationCommand(session.getId(), 1, null, " ")));
		assertEquals(EvaluationErrorCode.TRANSCRIPT_REQUIRED, blank.errorCode());

		when(pronunciationClient.evaluate(any(), any())).thenReturn(completeAssessment());
		when(messageRepository.findMessages(session.getId())).thenReturn(List.of(
				new Message(0, "What do you do?", null)));
		when(llmClient.assessTurn(any()))
				.thenThrow(new EvaluationException(EvaluationErrorCode.INVALID_REQUEST));

		EvaluationException providerFailure = assertThrows(EvaluationException.class,
				() -> processor.evaluateIeltsTurn("ielts-1",
						new DialogueTurnEvaluationCommand(session.getId(), 2,
								canonicalWav(),
								"I work in software and can explain my responsibilities.")));
		assertEquals(EvaluationErrorCode.INVALID_REQUEST, providerFailure.errorCode());
		verify(turnRepository, never()).upsert(any(CustomTurnEvaluation.class));
	}

	@Test
	void rejectsUnownedOrMissingIeltsPracticeBeforeEvaluation() {
		UUID owner = UUID.fromString("11111111-1111-4111-8111-111111111111");
		UUID other = UUID.fromString("22222222-2222-4222-8222-222222222222");
		when(authService.requireUserId(null)).thenReturn(owner.toString());
		when(practiceRepository.findPractice("missing-ielts")).thenReturn(Optional.empty());
		EvaluationException missing = assertThrows(EvaluationException.class,
				() -> processor.generateIeltsEvaluation("missing-ielts", "session-1"));
		assertEquals(EvaluationErrorCode.SESSION_NOT_FOUND, missing.errorCode());

		when(practiceRepository.findPractice("other-ielts")).thenReturn(Optional.of(
				partPractice("other-ielts", other, IeltsPart.PART_1)));
		EvaluationException unowned = assertThrows(EvaluationException.class,
				() -> processor.generateIeltsEvaluation("other-ielts", "session-1"));
		assertEquals(EvaluationErrorCode.SESSION_NOT_FOUND, unowned.errorCode());
		verify(practiceSessionRepository, never()).findBySceneId(any());
	}

	@Test
	void backfillsMissingCustomTurnBeforeGeneratingProviderIndependentReport() {
		CustomSceneSession session = session("backfill-success", "scene-1", "user-1");
		when(activeSessions.findById(session.getId())).thenReturn(Optional.of(session));
		when(reportRepository.find(session.getId())).thenReturn(Optional.empty());
		when(turnRepository.findAll(session.getId())).thenReturn(List.of(turn()));
		List<Message> dialogue = List.of(
				new Message(0, "Question", null),
				new Message(1, "First complete answer with details", null),
				new Message(0, "Follow-up", null),
				new Message(1, "Second complete answer with details", null));
		when(llmClient.assessDialogue(dialogue))
				.thenThrow(new EvaluationException(EvaluationErrorCode.RESULT_INCOMPLETE));

		var result = processor.generateDialogueReport(session.getId(), dialogue);

		assertTrue(result.summary().contains("语言模型文字报告暂不可用"));
		verify(turnRepository).upsert(any(CustomTurnEvaluation.class));
		verify(reportRepository).save(eq("scene-1"), eq(session.getId()), eq(result));
	}

	@Test
	void rejectsDialogueReportWithInvalidMessageShapeAfterSessionLookup() {
		CustomSceneSession session = session("session-1", "scene-1", "user-1");
		when(activeSessions.findById("session-1")).thenReturn(Optional.of(session));
		NullPointerException nullMessage = assertThrows(NullPointerException.class,
				() -> processor.generateDialogueReport("session-1", java.util.Arrays.asList((Message) null)));
		assertTrue(nullMessage.getMessage() == null || nullMessage.getMessage().isBlank());
		EvaluationException noLearner = assertThrows(EvaluationException.class,
				() -> processor.generateDialogueReport("session-1", List.of(
						new Message(0, "examiner only", null))));
		assertEquals(EvaluationErrorCode.NO_SCORABLE_UTTERANCES, noLearner.errorCode());
	}

	@Test
	void skipsIncompleteOrMissingIeltsHistoryEntriesAndReturnsLatestMockScore() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String incompleteMock = "history-incomplete-mock";
		String missingPractice = "history-missing-practice";
		String completedMock = "history-completed-mock";
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceSessionRepository.findCompletedByUserAndSceneType(
				eq(userId), eq(SceneType.IELTS_SCENE))).thenReturn(List.of(
				practiceSession("incomplete-1", userId, incompleteMock,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("missing-1", userId, missingPractice,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("completed-1", userId, completedMock,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("completed-2", userId, completedMock,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("completed-3", userId, completedMock,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED)));
		when(practiceRepository.findPractice(incompleteMock)).thenReturn(Optional.of(
				new IeltsPracticeRecord(incompleteMock, userId, IeltsMode.MOCK_TEST,
						null, null, "RANDOM", null, null, null,
						new IeltsContent(List.of(), List.of(), List.of()))));
		when(practiceRepository.findPractice(missingPractice)).thenReturn(Optional.empty());
		when(practiceRepository.findPractice(completedMock)).thenReturn(Optional.of(
				new IeltsPracticeRecord(completedMock, userId, IeltsMode.MOCK_TEST,
						null, null, "RANDOM", null, null, null,
						new IeltsContent(List.of(), List.of(), List.of()))));
		when(ieltsRepository.findTopicsByIds(any())).thenReturn(List.of());
		when(ieltsEvaluationRepository.findFinal(completedMock))
				.thenReturn(Optional.of(cachedFinal(completedMock)));
		when(ieltsEvaluationRepository.findParts(completedMock)).thenReturn(List.of());
		when(messageRepository.findAudioUrls(any())).thenReturn(List.of());
		when(objectStorage.available()).thenReturn(false);

		assertEquals(new BigDecimal("7.0"), processor.getLatestIeltsEstimatedScore());
		assertEquals(1, processor.getIeltsEvaluationHistory().size());
	}

	@Test
	void skipsPartHistoryWithoutEvaluationAndMapsNullArraysAndStoredAudioUrls() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "history-null-fields";
		String sessionId = "history-null-session";
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceSessionRepository.findCompletedByUserAndSceneType(
				eq(userId), eq(SceneType.IELTS_SCENE))).thenReturn(List.of(
				practiceSession(sessionId, userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED)));
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				partPractice(ieltsId, userId, IeltsPart.PART_1)));
		when(ieltsRepository.findTopicsByIds(any())).thenReturn(List.of());
		when(ieltsEvaluationRepository.findPart(sessionId)).thenReturn(Optional.of(
				cachedPartWithNullLists(ieltsId, sessionId, IeltsPart.PART_1)));
		when(messageRepository.findAudioUrls(sessionId)).thenReturn(
				List.of("https://stored.example/audio.wav"));

		var history = processor.getIeltsEvaluationHistory();

		assertEquals(1, history.size());
		assertEquals(List.of(), history.getFirst().strengths());
		assertEquals(List.of(), history.getFirst().improvements());
		assertEquals(List.of(), history.getFirst().recommendedExpressions());
		assertEquals(List.of("https://stored.example/audio.wav"),
				history.getFirst().recordingUrls());
	}

	@Test
	void reevaluatesIncompleteFinalCacheAndUsesPartScoresForFinalAverage() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String ieltsId = "incomplete-final-cache";
		List<PracticeSessionRecord> sessions = List.of(
				practiceSession("final-cache-1", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("final-cache-2", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED),
				practiceSession("final-cache-3", userId, ieltsId,
						SceneType.IELTS_SCENE, SessionStatus.COMPLETED));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(ieltsId, userId, IeltsMode.MOCK_TEST,
						null, null, "RANDOM", null, null, null,
						new IeltsContent(List.of(), List.of(), List.of()))));
		when(practiceSessionRepository.findBySceneId(ieltsId)).thenReturn(sessions);
		IeltsEvaluationEntity incomplete = cachedFinal(ieltsId);
		incomplete.setEvaluationStatus("FAILED");
		when(ieltsEvaluationRepository.findFinal(ieltsId)).thenReturn(Optional.of(incomplete));
		when(ieltsEvaluationRepository.findPart(any())).thenReturn(Optional.empty());
		when(ieltsLlmClient.assessPart(any(), any(), any(), any()))
				.thenReturn(new IeltsTextAssessment(
					IeltsPart.PART_1, new BigDecimal("6.5"), new BigDecimal("6.0"),
					new BigDecimal("5.5"), "流利", "词汇", "语法", "已完成",
					List.of("strength"), List.of("improvement"), "MEDIUM"));
		for (PracticeSessionRecord session : sessions) {
			when(messageRepository.findMessages(session.sessionId())).thenReturn(List.of(
					new Message(0, "Question", null),
					new Message(1, "I can answer this question in detail.", null)));
			when(turnRepository.findAll(session.sessionId())).thenReturn(List.of());
		}

		var result = processor.generateIeltsEvaluation(ieltsId, "final-cache-3");

		assertEquals("FINAL", result.assessmentType());
		assertEquals(new BigDecimal("6.0"), result.overallBandScore());
		verify(ieltsEvaluationRepository).saveFinal(ieltsId, result);
		verify(practiceRepository).incrementCompletedCount(userId);
	}

	@Test
	void coversIeltsTurnTooShortFeedbackFailureAndRecommendedPromptContext() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		CustomSceneSession session = session("ielts-prompt", "ielts-prompt", userId.toString());
		session.setSceneType(SceneType.IELTS_SCENE);
		session.setIeltsPart(IeltsPart.PART_3);
		IeltsContent content = new IeltsContent(List.of(), List.of(), List.of(
				new IeltsContentQuestion("Why?", List.of(), List.of(
						new RecommendedExpression("phrase", "in the long run",
								"长期来看", "use for future effects")))));
		when(activeSessions.findById(session.getId())).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice("ielts-prompt")).thenReturn(Optional.of(
				new IeltsPracticeRecord("ielts-prompt", userId, IeltsMode.MOCK_TEST,
						null, null, "RANDOM", null, null, null, content)));
		var tooShort = processor.evaluateIeltsTurn("ielts-prompt",
				new DialogueTurnEvaluationCommand(session.getId(), 1, null, "Hello"));
		assertEquals(1, tooShort.turnNo());

		when(messageRepository.findMessages(session.getId())).thenReturn(List.of(
				new Message(0, "Why?", null),
				new Message(0, "Follow-up", null),
				new Message(1, "I think it matters in the long run.", null)));
		when(turnRepository.findBefore(session.getId(), 2)).thenReturn(List.of(
				scorableTurn("ielts-prompt", session.getId())));
		when(pronunciationClient.evaluate(any(), any())).thenReturn(completeAssessment());
		when(llmClient.assessTurn(any())).thenThrow(
				new EvaluationException(EvaluationErrorCode.PROVIDER_RESPONSE_INVALID));
		when(recordingStore.store(eq(session.getId()), eq(2), any()))
				.thenReturn("https://audio.example/prompt.wav");

		var result = processor.evaluateIeltsTurn("ielts-prompt",
				new DialogueTurnEvaluationCommand(session.getId(), 2, canonicalWav(),
						"I think it matters in the long run."));

		assertEquals("本轮发音评分已完成，语言反馈暂不可用。", result.feedbackSummary());
		verify(turnRepository, org.mockito.Mockito.times(2)).upsert(any(CustomTurnEvaluation.class));
	}

	@Test
	void rejectsIeltsSessionWhenPracticeOrRuntimeOwnershipDoesNotMatch() {
		UUID owner = UUID.fromString("11111111-1111-4111-8111-111111111111");
		UUID other = UUID.fromString("22222222-2222-4222-8222-222222222222");
		CustomSceneSession session = session("owned-check", "ielts-owned", other.toString());
		session.setSceneType(SceneType.IELTS_SCENE);
		when(authService.requireUserId(null)).thenReturn(owner.toString());
		when(practiceRepository.findPractice("ielts-owned")).thenReturn(Optional.of(
				partPractice("ielts-owned", other, IeltsPart.PART_1)));
		when(activeSessions.findById("owned-check")).thenReturn(Optional.of(session));

		EvaluationException exception = assertThrows(EvaluationException.class,
				() -> processor.evaluateIeltsTurn("ielts-owned",
						new DialogueTurnEvaluationCommand("owned-check", 1, null,
								"A complete answer with several words.")));

		assertEquals(EvaluationErrorCode.SESSION_NOT_FOUND, exception.errorCode());
		verify(pronunciationClient, never()).evaluate(any(), any());
	}

	@Test
	void handlesCustomReportBackfillAndRejectsNonRecoverableReportFailure() {
		CustomSceneSession session = session("backfill-report", "scene-1", "user-1");
		when(activeSessions.findById(session.getId())).thenReturn(Optional.of(session));
		when(reportRepository.find(session.getId())).thenReturn(Optional.empty());
		when(turnRepository.findAll(session.getId())).thenReturn(List.of(turn()));
		List<Message> dialogue = List.of(
				new Message(0, "Question", null),
				new Message(1, "I answered with enough detail.", null));
		when(llmClient.assessDialogue(dialogue)).thenThrow(
				new EvaluationException(EvaluationErrorCode.INVALID_REQUEST));

		EvaluationException exception = assertThrows(EvaluationException.class,
				() -> processor.generateDialogueReport(session.getId(), dialogue));

		assertEquals(EvaluationErrorCode.INVALID_REQUEST, exception.errorCode());
		verify(turnRepository, never()).upsert(any(CustomTurnEvaluation.class));
		verify(reportRepository, never()).save(any(), any(), any());
	}

	@Test
	void coversSentenceNotFoundInsideSceneAndRecordingValidationFailures() {
		LearningContentItem otherSentence = new LearningContentItem(
				"other", "The other sentence.", "其他", null);
		when(sentenceRepository.findSceneIdBySentenceId("missing-in-scene"))
				.thenReturn(Optional.of("scene-1"));
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-1", otherSentence)));

		EvaluationException missing = assertThrows(EvaluationException.class,
				() -> processor.evaluateSentenceReading("missing-in-scene", canonicalWav()));
		assertEquals(EvaluationErrorCode.SENTENCE_NOT_FOUND, missing.errorCode());

		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		CustomSceneSession session = session("record-invalid", "ielts-1", userId.toString());
		session.setSceneType(SceneType.IELTS_SCENE);
		when(activeSessions.findById(session.getId())).thenReturn(Optional.of(session));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceRepository.findPractice("ielts-1")).thenReturn(Optional.of(
				partPractice("ielts-1", userId, IeltsPart.PART_1)));
		var result = processor.evaluateIeltsTurn("ielts-1",
				new DialogueTurnEvaluationCommand(session.getId(), 1,
						new byte[]{1, 2, 3}, "A complete answer with several words."));
		assertEquals(1, result.turnNo());
		verify(recordingStore, never()).store(any(), anyInt(), any());
	}

	@Test
	void invokesPureBandAndValidationBranchesThroughReflectionForStableContract() throws Exception {
		Method overall = EvaluationProcessor.class.getDeclaredMethod(
				"overallBand", IeltsTextAssessment.class, BigDecimal.class);
		overall.setAccessible(true);
		BigDecimal band = (BigDecimal) overall.invoke(processor,
				new IeltsTextAssessment(IeltsPart.PART_1,
						new BigDecimal("7.0"), new BigDecimal("6.5"),
						new BigDecimal("6.0"), "f", "l", "g", "s",
						List.of(), List.of(), "HIGH"),
				new BigDecimal("7.5"));
		assertEquals(new BigDecimal("7.0"), band);

		Method partByIndex = EvaluationProcessor.class.getDeclaredMethod("partByIndex", int.class);
		partByIndex.setAccessible(true);
		var invalid = assertThrows(java.lang.reflect.InvocationTargetException.class,
				() -> partByIndex.invoke(processor, 99));
		assertEquals(EvaluationErrorCode.INVALID_REQUEST,
				((EvaluationException) invalid.getCause()).errorCode());

		Method pronunciationBand = EvaluationProcessor.class.getDeclaredMethod(
				"pronunciationBand", List.class);
		pronunciationBand.setAccessible(true);
		var emptyPronunciation = assertThrows(InvocationTargetException.class,
				() -> pronunciationBand.invoke(processor, List.of()));
		assertEquals(EvaluationErrorCode.NO_SCORABLE_UTTERANCES,
				((EvaluationException) emptyPronunciation.getCause()).errorCode());

		Method meanAvailable = EvaluationProcessor.class.getDeclaredMethod(
				"meanAvailable", BigDecimal[].class);
		meanAvailable.setAccessible(true);
		assertEquals(new BigDecimal("0.0"), meanAvailable.invoke(processor,
				(Object) new BigDecimal[]{null}));
		assertEquals(new BigDecimal("50.0"), meanAvailable.invoke(processor,
				(Object) new BigDecimal[]{new BigDecimal("-5"),
						new BigDecimal("105")}));

		Method firstScore = EvaluationProcessor.class.getDeclaredMethod(
				"firstScore", BigDecimal.class, BigDecimal.class);
		firstScore.setAccessible(true);
		assertEquals(new BigDecimal("4"), firstScore.invoke(processor,
				null, new BigDecimal("4")));

		Method findAiText = EvaluationProcessor.class.getDeclaredMethod(
				"findAiText", List.class, int.class);
		findAiText.setAccessible(true);
		List<Message> messages = List.of(
				new Message(0, "Question", null),
				new Message(1, "Answer", null));
		assertEquals("Question", findAiText.invoke(processor, messages, 1));
		assertNull(findAiText.invoke(processor, messages, 2));

		Method validateDialogue = EvaluationProcessor.class.getDeclaredMethod(
				"validateDialogue", List.class);
		validateDialogue.setAccessible(true);
		assertInvocationError(validateDialogue, null, EvaluationErrorCode.INVALID_REQUEST);
		assertInvocationError(validateDialogue, List.of(), EvaluationErrorCode.INVALID_REQUEST);
		assertInvocationError(validateDialogue,
				List.of(new Message(2, "invalid owner", null)),
				EvaluationErrorCode.INVALID_REQUEST);
		assertInvocationError(validateDialogue,
				List.of(new Message(0, "examiner only", null)),
				EvaluationErrorCode.NO_SCORABLE_UTTERANCES);

		Method requireComplete = EvaluationProcessor.class.getDeclaredMethod(
				"requireCompleteLearnerTurns", List.class, List.class);
		requireComplete.setAccessible(true);
		List<Message> learnerDialogue = List.of(new Message(1, "answer", null));
		assertInvocationError(requireComplete, learnerDialogue, List.of(),
				EvaluationErrorCode.RESULT_INCOMPLETE);
		assertInvocationError(requireComplete, learnerDialogue,
				List.of(new CustomTurnEvaluation("scene", "session", 2,
						"answer", null, null, null, null, null, null, "saved", "", List.of())),
				EvaluationErrorCode.RESULT_INCOMPLETE);
	}

	@Test
	void coversHistorySkipsLatestFilteringAndIncompleteEvaluationRows() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		String mockId = "history-null-final";
		String partId = "history-null-part";
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(practiceSessionRepository.findCompletedByUserAndSceneType(
				eq(userId), eq(SceneType.IELTS_SCENE))).thenReturn(List.of(
				practiceSession("mock-1", userId, mockId, SceneType.IELTS_SCENE,
						SessionStatus.COMPLETED),
				practiceSession("mock-2", userId, mockId, SceneType.IELTS_SCENE,
						SessionStatus.COMPLETED),
				practiceSession("mock-3", userId, mockId, SceneType.IELTS_SCENE,
						SessionStatus.COMPLETED),
				practiceSession("part-1", userId, partId, SceneType.IELTS_SCENE,
						SessionStatus.COMPLETED)));
		when(practiceRepository.findPractice(mockId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(mockId, userId, IeltsMode.MOCK_TEST, null, null,
						"RANDOM", null, null, null, new IeltsContent(List.of(), List.of(), List.of()))));
		when(practiceRepository.findPractice(partId)).thenReturn(Optional.of(
				partPractice(partId, userId, IeltsPart.PART_1)));
		when(ieltsRepository.findTopicsByIds(any())).thenReturn(List.of());
		when(ieltsEvaluationRepository.findFinal(mockId)).thenReturn(Optional.empty());
		when(ieltsEvaluationRepository.findPart("part-1")).thenReturn(Optional.empty());

		assertTrue(processor.getIeltsEvaluationHistory().isEmpty());
		assertNull(processor.getLatestIeltsEstimatedScore());
	}

	@Test
	void coversOwnershipGuardsAndCustomRuntimeShapeChecks() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(sentenceRepository.findSceneIdBySentenceId("missing-scene"))
				.thenReturn(Optional.of("scene-missing"));
		when(sceneRepository.findCustomDefinitionById("scene-missing"))
				.thenReturn(Optional.empty());
		assertEquals(EvaluationErrorCode.SENTENCE_NOT_FOUND,
				assertThrows(EvaluationException.class,
						() -> processor.evaluateSentenceReading("missing-scene", canonicalWav()))
						.errorCode());

		CustomSceneSession wrongType = session("wrong-type", "scene-1", "user-1");
		wrongType.setSceneType(SceneType.IELTS_SCENE);
		when(activeSessions.findById("wrong-type")).thenReturn(Optional.of(wrongType));
		assertEquals(EvaluationErrorCode.SESSION_NOT_FOUND,
				assertThrows(EvaluationException.class,
						() -> processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
								"wrong-type", 1, null, "A complete answer here")))
						.errorCode());

		CustomSceneSession blankScene = session("blank-scene", "", "user-1");
		when(activeSessions.findById("blank-scene")).thenReturn(Optional.of(blankScene));
		assertEquals(EvaluationErrorCode.SESSION_NOT_FOUND,
				assertThrows(EvaluationException.class,
						() -> processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
								"blank-scene", 1, null, "A complete answer here")))
						.errorCode());

		when(activeSessions.findById("blank-id")).thenReturn(Optional.of(
				session("blank-id", "scene-1", "user-1")));
		assertEquals(EvaluationErrorCode.SESSION_NOT_FOUND,
				assertThrows(EvaluationException.class,
						() -> processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
								"", 1, null, "A complete answer here")))
						.errorCode());

		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		CustomSceneSession mismatched = session("mismatch", "scene-1", "user-2");
		when(activeSessions.findById("mismatch")).thenReturn(Optional.of(mismatched));
		when(sceneRepository.findCustomDefinitionById("scene-1"))
				.thenReturn(Optional.of(scene("scene-1", "user-1", null)));
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		// The different user is intentionally checked after the scene lookup.
		when(authService.requireUserId(null)).thenReturn("user-1");
		assertEquals(EvaluationErrorCode.SESSION_NOT_FOUND,
				assertThrows(EvaluationException.class,
						() -> processor.evaluateDialogueTurn(new DialogueTurnEvaluationCommand(
								"mismatch", 1, null, "A complete answer here")))
						.errorCode());
	}

	private void assertInvocationError(
			Method method,
			Object argument,
			EvaluationErrorCode expected) {
		InvocationTargetException exception = assertThrows(
				InvocationTargetException.class,
				() -> method.invoke(processor, new Object[]{argument}));
		assertEquals(expected, ((EvaluationException) exception.getCause()).errorCode());
	}

	private void assertInvocationError(
			Method method,
			Object first,
			Object second,
			EvaluationErrorCode expected) {
		InvocationTargetException exception = assertThrows(
				InvocationTargetException.class,
				() -> method.invoke(processor, first, second));
		assertEquals(expected, ((EvaluationException) exception.getCause()).errorCode());
	}

	private CustomSceneDefinition scene(
			String sceneId,
			String userId,
			LearningContentItem sentence) {
		return new CustomSceneDefinition(sceneId, userId, "场景", "标签", "背景",
				"AI", "用户", "目标", null, null, List.of(), List.of(),
				sentence == null ? List.of() : List.of(sentence));
	}

	private CustomSceneSession session(String sessionId, String sceneId, String userId) {
		CustomSceneSession session = new CustomSceneSession(sessionId, userId);
		session.setSceneId(sceneId);
		session.setSceneType(SceneType.CUSTOM_SCENE);
		return session;
	}

	private CustomTurnEvaluation turn() {
		return new CustomTurnEvaluation("scene-1", "session-1", 1,
				"I improved the service latency.", new BigDecimal("82"),
				new BigDecimal("81"), new BigDecimal("80"), new BigDecimal("83"),
				new BigDecimal("84"), new BigDecimal("85"), "表达清晰",
				"I improved service latency.", List.of(new PronunciationWordDetail(
						0, "improved", new BigDecimal("84"), List.of(
								new PronunciationWordDetail.Phoneme(0, "ih", "ih",
										new BigDecimal("84"), 0, 20)))));
	}

	private IeltsPracticeRecord partPractice(
			String ieltsId,
			UUID userId,
			IeltsPart part) {
		return new IeltsPracticeRecord(
				ieltsId,
				userId,
				IeltsMode.PART_PRACTICE,
				part,
				"topic-" + part.name(),
				new IeltsContent(List.of(), List.of(), List.of()));
	}

	private PracticeSessionRecord practiceSession(
			String sessionId,
			UUID userId,
			String sceneId,
			SceneType sceneType,
			SessionStatus status) {
		Instant startedAt = Instant.parse("2026-08-21T08:00:00Z");
		return new PracticeSessionRecord(
				sessionId,
				userId,
				sceneId,
				sceneType,
				status,
				startedAt,
				startedAt.plusSeconds(60));
	}

	private IeltsPartEvaluationEntity cachedPart(
			String ieltsId,
			String sessionId,
			IeltsPart part) {
		IeltsPartEvaluationEntity entity = new IeltsPartEvaluationEntity();
		entity.setIeltsId(ieltsId);
		entity.setSessionId(sessionId);
		entity.setPart(part.name());
		entity.setFluencyCoherenceScore(new BigDecimal("7.0"));
		entity.setLexicalResourceScore(new BigDecimal("6.5"));
		entity.setGrammaticalRangeAccuracyScore(new BigDecimal("6.0"));
		entity.setPronunciationScore(new BigDecimal("7.5"));
		entity.setSummary("缓存的单 Part 评分");
		entity.setStrengths(new String[]{"能够展开"});
		entity.setImprovements(new String[]{"增加细节"});
		entity.setRecommendedExpressions(new String[]{"A clearer answer."});
		entity.setFluencyCoherenceReason("表达基本连贯");
		entity.setLexicalResourceReason("词汇够用");
		entity.setGrammaticalRangeAccuracyReason("句式基本准确");
		entity.setPronunciationReason("发音清楚");
		entity.setEvaluationStatus("COMPLETED");
		return entity;
	}

	private IeltsPartEvaluationEntity cachedPartWithNullLists(
			String ieltsId,
			String sessionId,
			IeltsPart part) {
		IeltsPartEvaluationEntity entity = cachedPart(ieltsId, sessionId, part);
		entity.setStrengths(null);
		entity.setImprovements(null);
		entity.setRecommendedExpressions(null);
		return entity;
	}

	private IeltsEvaluationEntity cachedFinal(String ieltsId) {
		IeltsEvaluationEntity entity = new IeltsEvaluationEntity();
		entity.setIeltsId(ieltsId);
		entity.setOverallBandScore(new BigDecimal("7.0"));
		entity.setFluencyCoherenceScore(new BigDecimal("7.0"));
		entity.setLexicalResourceScore(new BigDecimal("6.5"));
		entity.setGrammaticalRangeAccuracyScore(new BigDecimal("6.5"));
		entity.setPronunciationScore(new BigDecimal("7.5"));
		entity.setSummary("模考缓存结果");
		entity.setStrengths(new String[]{"优势"});
		entity.setImprovements(new String[]{"改进"});
		entity.setRecommendedExpressions(new String[]{"Use detail."});
		entity.setFluencyCoherenceReason("流利");
		entity.setLexicalResourceReason("词汇");
		entity.setGrammaticalRangeAccuracyReason("语法");
		entity.setPronunciationReason("发音");
		entity.setEvaluationStatus("COMPLETED");
		return entity;
	}

	private CustomTurnEvaluation scorableTurn(
			String sceneId,
			String sessionId) {
		return new CustomTurnEvaluation(
				sceneId,
				sessionId,
				1,
				"I can explain this topic with several details.",
				new BigDecimal("82"),
				new BigDecimal("78"),
				new BigDecimal("76"),
				new BigDecimal("85"),
				new BigDecimal("80"),
				new BigDecimal("79"),
				"表达清楚",
				"A more natural expression.",
				List.of());
	}

	private PronunciationAssessmentResult completeAssessment() {
		return new PronunciationAssessmentResult(
				new BigDecimal("86"), new BigDecimal("82"), new BigDecimal("80"),
				new BigDecimal("88"), new BigDecimal("84"), new BigDecimal("85"),
				EndingTone.FALL, List.of(new PronunciationWordResult(0, "index",
						WordReadStatus.NORMAL, new BigDecimal("84"), new BigDecimal("84"),
						null, List.of(new PronunciationPhonemeResult(0, "ih", "ih",
								new BigDecimal("84"), 0, 20)))));
	}

	private byte[] canonicalWav() {
		byte[] wav = new byte[46];
		writeAscii(wav, 0, "RIFF");
		writeInt(wav, 4, wav.length - 8);
		writeAscii(wav, 8, "WAVE");
		writeAscii(wav, 12, "fmt ");
		writeInt(wav, 16, 16);
		writeShort(wav, 20, 1);
		writeShort(wav, 22, 1);
		writeInt(wav, 24, 16_000);
		writeInt(wav, 28, 32_000);
		writeShort(wav, 32, 2);
		writeShort(wav, 34, 16);
		writeAscii(wav, 36, "data");
		writeInt(wav, 40, 2);
		return wav;
	}

	private void writeAscii(byte[] target, int offset, String value) {
		byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		System.arraycopy(bytes, 0, target, offset, bytes.length);
	}

	private void writeShort(byte[] target, int offset, int value) {
		target[offset] = (byte) value;
		target[offset + 1] = (byte) (value >>> 8);
	}

	private void writeInt(byte[] target, int offset, int value) {
		target[offset] = (byte) value;
		target[offset + 1] = (byte) (value >>> 8);
		target[offset + 2] = (byte) (value >>> 16);
		target[offset + 3] = (byte) (value >>> 24);
	}
}
