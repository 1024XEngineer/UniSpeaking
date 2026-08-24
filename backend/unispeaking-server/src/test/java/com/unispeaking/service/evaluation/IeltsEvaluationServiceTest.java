package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.IeltsTextAssessment;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.evaluation.client.EvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.IeltsEvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.PronunciationAssessmentClient;
import com.unispeaking.infrastructure.persistence.entity.evaluation.CustomTurnEvaluation;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsPartEvaluationEntity;
import com.unispeaking.infrastructure.persistence.repository.evaluation.IeltsEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.component.evaluation.EvaluationProcessor;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationDetail;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationReport;
import com.unispeaking.domain.dto.session.SessionDetail;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IeltsEvaluationServiceTest {

	@Test
	void delegatesTurnReportDetailAndHistoryOperations() {
		EvaluationProcessor delegate = mock(EvaluationProcessor.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		IeltsEvaluationService service = new IeltsEvaluationService(delegate, lifecycle);
		IeltsEvaluationResult result = new IeltsEvaluationResult(
				IeltsPart.PART_2,
				"DIAGNOSTIC",
				new BigDecimal("7.0"),
				new BigDecimal("7.5"),
				new BigDecimal("6.5"),
				new BigDecimal("7.0"),
				new BigDecimal("7.0"),
				"summary",
				List.of("strength"),
				List.of("improvement"),
				List.of(),
				List.of());
		when(lifecycle.getSession("session-1")).thenReturn(new SessionDetail(
				"session-1", "scene-1", SceneType.IELTS_SCENE, "PART2", List.of()));
		when(delegate.evaluateIeltsTurn(eq("scene-1"), any())).thenReturn(null);
		when(lifecycle.getBySceneId("scene-1")).thenReturn(List.of(
				new SessionDetail("session-1", "scene-1", SceneType.IELTS_SCENE,
						"PART2", List.of())));
		when(delegate.generateIeltsEvaluation("scene-1", "session-1")).thenReturn(result);
		when(delegate.getLatestIeltsEstimatedScore()).thenReturn(new BigDecimal("6.5"));
		when(delegate.getIeltsEvaluationHistory()).thenReturn(List.of());

		assertEquals(null, service.evaluateTurn(new DialogueTurnEvaluationCommand(
				"session-1", 1, null, "answer")));
		IeltsEvaluationReport report = service.generateReport("scene-1");
		assertEquals(new BigDecimal("7.0"), report.bandScore());
		IeltsEvaluationDetail detail = service.getEvaluation("scene-1");
		assertEquals("summary", detail.result().summary());
		assertEquals(new BigDecimal("6.5"), service.getLatestEstimatedScore());
		assertEquals(List.of(), service.getHistory());
	}

	@Test
	void rejectsReportAndDetailWhenSceneHasNoSessions() {
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		when(lifecycle.getBySceneId("missing-scene")).thenReturn(List.of());
		IeltsEvaluationService service = new IeltsEvaluationService(
				mock(EvaluationProcessor.class), lifecycle);

		BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
				BusinessException.class, () -> service.generateReport("missing-scene"));
		assertEquals("SESSION_NOT_FOUND", exception.code());
		org.junit.jupiter.api.Assertions.assertThrows(
				BusinessException.class, () -> service.getEvaluation("missing-scene"));
	}

	@Test
	void returnsCompletedMockEvaluationFromCacheWithoutCallingProvidersOrIncrementing() {
		UUID userId = UUID.fromString("3d8f80be-6390-4db9-a6cf-c10a0145d4c3");
		String ieltsId = "ielts_mock_cached";
		List<PracticeSessionRecord> sessions = List.of(
				session("cached-p1", userId, ieltsId, 0),
				session("cached-p2", userId, ieltsId, 1),
				session("cached-p3", userId, ieltsId, 2));
		IeltsPracticeRepository practiceRepository = mock(IeltsPracticeRepository.class);
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(ieltsId, userId, IeltsMode.MOCK_TEST,
						null, null, "RANDOM", "topic-p1", "topic-p2", "topic-p3",
						new IeltsContent(List.of(), List.of(), List.of()))));
		PracticeSessionRepository sessionRepository = mock(PracticeSessionRepository.class);
		when(sessionRepository.findBySceneId(ieltsId)).thenReturn(sessions);
		IeltsEvaluationEntity cached = new IeltsEvaluationEntity();
		cached.setEvaluationId("final_cached");
		cached.setIeltsId(ieltsId);
		cached.setEvaluationStatus("COMPLETED");
		cached.setOverallBandScore(new BigDecimal("7.5"));
		cached.setFluencyCoherenceScore(new BigDecimal("7.0"));
		cached.setLexicalResourceScore(new BigDecimal("7.5"));
		cached.setGrammaticalRangeAccuracyScore(new BigDecimal("7.0"));
		cached.setPronunciationScore(new BigDecimal("8.0"));
		cached.setSummary("已缓存的完整模考报告");
		IeltsEvaluationRepository evaluationRepository = mock(IeltsEvaluationRepository.class);
		when(evaluationRepository.findFinal(ieltsId)).thenReturn(Optional.of(cached));
		when(evaluationRepository.findParts(ieltsId)).thenReturn(List.of(
				savedPart(ieltsId, "cached-p1",
						com.unispeaking.domain.vo.scene.IeltsPart.PART_1, "7.5")));
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		IeltsEvaluationLlmClient ieltsLlmClient = mock(IeltsEvaluationLlmClient.class);

		EvaluationProcessor processor = new EvaluationProcessor(
				mock(PronunciationAssessmentClient.class), mock(EvaluationLlmClient.class),
				mock(ActiveSessionRegistry.class), mock(SceneRepository.class),
				mock(SessionMessageRepository.class), mock(TurnEvaluationRepository.class),
				mock(SessionEvaluationRepository.class), mock(SceneSentenceReadingRepository.class),
				practiceRepository,
				mock(com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository.class),
				mock(IeltsSceneFlowService.class), sessionRepository, evaluationRepository,
				ieltsLlmClient, authService,
				mock(com.unispeaking.provider.ObjectStorageProvider.class),
				new com.unispeaking.infrastructure.config.ObjectStorageProperties(),
				mock(com.unispeaking.component.recording.RecordingStore.class));

		var result = processor.generateIeltsEvaluation(ieltsId, "cached-p3");

		assertEquals("FINAL", result.assessmentType());
		assertEquals(new BigDecimal("7.5"), result.overallBandScore());
		assertEquals(1, result.partEvaluations().size());
		verify(ieltsLlmClient, never()).assessPart(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class),
				org.mockito.ArgumentMatchers.anyString());
		verify(evaluationRepository, never()).saveFinal(
				eq(ieltsId), org.mockito.ArgumentMatchers.any());
		verify(practiceRepository, never()).incrementCompletedCount(userId);
	}

	@Test
	void returnsDiagnosticHistoryWithStoredRecordingAndFallbackTopicTitle() {
		UUID userId = UUID.fromString("3d8f80be-6390-4db9-a6cf-c10a0145d4c3");
		String ieltsId = "ielts_part_history";
		PracticeSessionRecord completedSession = session(
				"history-session", userId, ieltsId, 0);
		IeltsPracticeRepository practiceRepository = mock(IeltsPracticeRepository.class);
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(ieltsId, userId, IeltsMode.PART_PRACTICE,
						com.unispeaking.domain.vo.scene.IeltsPart.PART_2, "missing-topic",
						new IeltsContent(List.of(), List.of(), List.of()))));
		PracticeSessionRepository sessionRepository = mock(PracticeSessionRepository.class);
		when(sessionRepository.findCompletedByUserAndSceneType(
				userId, SceneType.IELTS_SCENE)).thenReturn(List.of(completedSession));
		IeltsPartEvaluationEntity part = savedPart(ieltsId, "history-session",
				com.unispeaking.domain.vo.scene.IeltsPart.PART_2, "6.5");
		part.setStrengths(null);
		part.setImprovements(null);
		part.setRecommendedExpressions(null);
		IeltsEvaluationRepository evaluationRepository = mock(IeltsEvaluationRepository.class);
		when(evaluationRepository.findPart("history-session")).thenReturn(Optional.of(part));
		SessionMessageRepository messageRepository = mock(SessionMessageRepository.class);
		when(messageRepository.findAudioUrls("history-session")).thenReturn(List.of(
				"https://recordings.example/history-session.wav"));
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		var topicRepository = mock(
				com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository.class);
		when(topicRepository.findTopicsByIds(any())).thenReturn(List.of());

		EvaluationProcessor processor = new EvaluationProcessor(
				mock(PronunciationAssessmentClient.class), mock(EvaluationLlmClient.class),
				mock(ActiveSessionRegistry.class), mock(SceneRepository.class),
				messageRepository, mock(TurnEvaluationRepository.class),
				mock(SessionEvaluationRepository.class), mock(SceneSentenceReadingRepository.class),
				practiceRepository, topicRepository, mock(IeltsSceneFlowService.class),
				sessionRepository, evaluationRepository, mock(IeltsEvaluationLlmClient.class),
				authService, mock(com.unispeaking.provider.ObjectStorageProvider.class),
				new com.unispeaking.infrastructure.config.ObjectStorageProperties(),
				mock(com.unispeaking.component.recording.RecordingStore.class));

		var history = processor.getIeltsEvaluationHistory();

		assertEquals(1, history.size());
		assertEquals("DIAGNOSTIC", history.getFirst().assessmentType());
		assertEquals(com.unispeaking.domain.vo.scene.IeltsPart.PART_2,
				history.getFirst().part());
		assertEquals("missing-topic", history.getFirst().topicTitles().get(
				com.unispeaking.domain.vo.scene.IeltsPart.PART_2));
		assertEquals(List.of("https://recordings.example/history-session.wav"),
				history.getFirst().recordingUrls());
		assertEquals(List.of(), history.getFirst().strengths());
	}

	@Test
	void preservesPronunciationWhenIeltsLanguageFeedbackProviderFails() {
		UUID userId = UUID.fromString("3d8f80be-6390-4db9-a6cf-c10a0145d4c3");
		String ieltsId = "ielts_part_2";
		String sessionId = "ielts_session_2";
		byte[] audio = canonicalWav();
		PronunciationAssessmentClient pronunciationClient =
				mock(PronunciationAssessmentClient.class);
		when(pronunciationClient.evaluate(
				eq("I would like to describe a memorable journey from last year."),
				aryEq(audio)))
				.thenReturn(pronunciationAssessment());
		EvaluationLlmClient llmClient = mock(EvaluationLlmClient.class);
		when(llmClient.assessTurn(any())).thenThrow(new EvaluationException(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID));
		ActiveSessionRegistry activeSessions = mock(ActiveSessionRegistry.class);
		CustomSceneSession session = new CustomSceneSession(
				sessionId,
				userId.toString());
		session.setSceneId(ieltsId);
		session.setSceneType(SceneType.IELTS_SCENE);
		when(activeSessions.findById(sessionId)).thenReturn(Optional.of(session));
		IeltsPracticeRepository practiceRepository =
				mock(IeltsPracticeRepository.class);
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(
						ieltsId,
						userId,
						IeltsMode.PART_PRACTICE,
						com.unispeaking.domain.vo.scene.IeltsPart.PART_2,
						"topic-p2",
						new IeltsContent(List.of(), List.of(), List.of()))));
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		SessionMessageRepository messageRepository =
				mock(SessionMessageRepository.class);
		when(messageRepository.findMessages(sessionId)).thenReturn(List.of(
				new Message(0, "Describe a memorable journey.", null)));
		TurnEvaluationRepository turnRepository =
				mock(TurnEvaluationRepository.class);
		var recordingStore = mock(
				com.unispeaking.component.recording.RecordingStore.class);
		when(recordingStore.store(sessionId, 1, audio)).thenReturn(
				"/api/ielts/recordings/ielts_session_2/turn-1.wav");
		EvaluationProcessor processor = new EvaluationProcessor(
				pronunciationClient,
				llmClient,
				activeSessions,
				mock(SceneRepository.class),
				messageRepository,
				turnRepository,
				mock(SessionEvaluationRepository.class),
				mock(SceneSentenceReadingRepository.class),
				practiceRepository,
				mock(com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository.class),
				mock(IeltsSceneFlowService.class),
				mock(PracticeSessionRepository.class),
				mock(IeltsEvaluationRepository.class),
				mock(IeltsEvaluationLlmClient.class),
				authService,
				mock(com.unispeaking.provider.ObjectStorageProvider.class),
				new com.unispeaking.infrastructure.config.ObjectStorageProperties(),
				recordingStore);

		var result = processor.evaluateIeltsTurn(
				ieltsId,
				new DialogueTurnEvaluationCommand(
						sessionId,
						1,
						audio,
						"I would like to describe a memorable journey from last year."));

		assertEquals(new BigDecimal("88"), result.pronunciationScore());
		assertEquals("本轮发音评分已完成，语言反馈暂不可用。", result.feedbackSummary());
		ArgumentCaptor<CustomTurnEvaluation> savedTurn =
				ArgumentCaptor.forClass(CustomTurnEvaluation.class);
		verify(turnRepository).upsert(savedTurn.capture());
		assertEquals(
				new BigDecimal("88"),
				savedTurn.getValue().pronunciationScore());
		verify(recordingStore).store(sessionId, 1, audio);
	}

	@Test
	void reusesCompletedPartScoresAndOnlyScoresMissingPartBeforeFinalReport() {
		UUID userId = UUID.fromString("3d8f80be-6390-4db9-a6cf-c10a0145d4c3");
		String ieltsId = "ielts_mock_1";
		IeltsPracticeRepository practiceRepository =
				mock(IeltsPracticeRepository.class);
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(
						ieltsId,
						userId,
						IeltsMode.MOCK_TEST,
						null,
						null,
						"RANDOM",
						"topic-p1",
						"topic-p2",
						"topic-p3",
						new IeltsContent(List.of(), List.of(), List.of()))));
		PracticeSessionRepository sessionRepository =
				mock(PracticeSessionRepository.class);
		List<PracticeSessionRecord> sessions = List.of(
				session("session-p1", userId, ieltsId, 0),
				session("session-p2", userId, ieltsId, 1),
				session("session-p3", userId, ieltsId, 2));
		when(sessionRepository.findBySceneId(ieltsId)).thenReturn(sessions);
		when(sessionRepository.findCompletedByUserAndSceneType(
				userId,
				SceneType.IELTS_SCENE)).thenReturn(sessions);

		SessionMessageRepository messageRepository =
				mock(SessionMessageRepository.class);
		when(messageRepository.findMessages("session-p1")).thenReturn(
				List.of(new Message(0, "Examiner question", null)));
		for (PracticeSessionRecord session : sessions.subList(1, sessions.size())) {
			when(messageRepository.findMessages(session.sessionId())).thenReturn(
					List.of(
							new Message(0, "Examiner question", null),
							new Message(1, "Candidate answer with enough detail", null)));
		}
		TurnEvaluationRepository turnRepository =
				mock(TurnEvaluationRepository.class);
		when(turnRepository.findAll("session-p1")).thenReturn(List.of());
		for (PracticeSessionRecord session : sessions.subList(1, sessions.size())) {
			when(turnRepository.findAll(session.sessionId()))
					.thenReturn(List.of(scorableTurn(
							ieltsId,
							session.sessionId())));
		}

		IeltsEvaluationLlmClient ieltsLlmClient =
				mock(IeltsEvaluationLlmClient.class);
		when(ieltsLlmClient.assessPart(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class),
				org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(new IeltsTextAssessment(
						null,
						new BigDecimal("7.0"),
						new BigDecimal("6.0"),
						new BigDecimal("7.0"),
						"回答能够持续展开，衔接基本清楚。",
						"能够使用话题词汇，但词汇精度有限。",
						"简单句控制稳定，并尝试使用复杂句。",
						"单项表达基本完整。",
						List.of("能够作答"),
						List.of("增加细节"),
						"HIGH"));
		when(ieltsLlmClient.assessFullTest(
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				eq("7.0"))).thenReturn(new IeltsTextAssessment(
						null,
						new BigDecimal("7.0"),
						new BigDecimal("6.0"),
						new BigDecimal("7.0"),
						"三个 Part 均能保持基本连贯。",
						"跨话题词汇够用，但精度不稳定。",
						"能使用不同句式，偶有控制错误。",
						"整场表达连贯。",
						List.of("能持续作答"),
						List.of("提高词汇精度"),
						"HIGH"));
		IeltsEvaluationRepository evaluationRepository =
				mock(IeltsEvaluationRepository.class);
		when(evaluationRepository.findPart("session-p1")).thenReturn(Optional.of(
				savedPart(
						ieltsId,
						"session-p1",
						com.unispeaking.domain.vo.scene.IeltsPart.PART_1,
						"6.5")));
		when(evaluationRepository.findPart("session-p2")).thenReturn(Optional.of(
				savedPart(
						ieltsId,
						"session-p2",
						com.unispeaking.domain.vo.scene.IeltsPart.PART_2,
						"6.5")));
		when(evaluationRepository.findPart("session-p3"))
				.thenReturn(Optional.empty());
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		var topicRepository = mock(
				com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository.class);
		when(topicRepository.findTopicsByIds(any())).thenReturn(List.of(
				new com.unispeaking.domain.po.scene.IeltsTopic(
						"topic-p1", "Work or Studies",
						com.unispeaking.domain.vo.scene.IeltsTopicType.PART_1_POOL,
						"REQUIRED", "XDF", "READY"),
				new com.unispeaking.domain.po.scene.IeltsTopic(
						"topic-p2", "A useful object",
						com.unispeaking.domain.vo.scene.IeltsTopicType.PART_2_3_BUNDLE,
						"OBJECT", "XDF", "READY"),
				new com.unispeaking.domain.po.scene.IeltsTopic(
						"topic-p3", "Technology and society",
						com.unispeaking.domain.vo.scene.IeltsTopicType.PART_2_3_BUNDLE,
						"ABSTRACT", "XDF", "READY")));

		EvaluationProcessor service = new EvaluationProcessor(
				mock(PronunciationAssessmentClient.class),
				mock(EvaluationLlmClient.class),
				mock(ActiveSessionRegistry.class),
				mock(SceneRepository.class),
				messageRepository,
				turnRepository,
				mock(SessionEvaluationRepository.class),
				mock(SceneSentenceReadingRepository.class),
				practiceRepository,
				topicRepository,
				mock(IeltsSceneFlowService.class),
				sessionRepository,
				evaluationRepository,
				ieltsLlmClient,
				authService,
				mock(com.unispeaking.provider.ObjectStorageProvider.class),
				new com.unispeaking.infrastructure.config.ObjectStorageProperties(),
				mock(com.unispeaking.component.recording.RecordingStore.class));

		var result = service.generateIeltsEvaluation(ieltsId, "session-p3");

		assertEquals("FINAL", result.assessmentType());
		assertEquals(new BigDecimal("7.0"), result.pronunciationScore());
		assertEquals(new BigDecimal("7.0"), result.overallBandScore());
		assertEquals(new BigDecimal("6.5"), result.lexicalResourceScore());
		assertEquals(new BigDecimal("6.5"), result.grammaticalRangeAccuracyScore());
		assertEquals(
				"流利与连贯分数由三个 Part 已完成的后台评分取平均并按 0.5 分取整，结果为 7.0。",
				result.fluencyCoherenceReason());
		assertEquals(
				"发音分数由三个 Part 已完成的后台评分取平均并按 0.5 分取整，结果为 7.0。",
				result.pronunciationReason());
		assertEquals(3, result.partEvaluations().size());
		assertEquals(
				new BigDecimal("6.5"),
				result.partEvaluations().get(1).lexicalResourceScore());
		assertEquals(
				new BigDecimal("6.0"),
				result.partEvaluations().get(2).lexicalResourceScore());
		verify(ieltsLlmClient, times(1)).assessPart(
				eq(com.unispeaking.domain.vo.scene.IeltsPart.PART_3),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class),
				org.mockito.ArgumentMatchers.anyString());
		verify(ieltsLlmClient, never()).assessPart(
				eq(com.unispeaking.domain.vo.scene.IeltsPart.PART_1),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class),
				org.mockito.ArgumentMatchers.anyString());
		verify(ieltsLlmClient, never()).assessPart(
				eq(com.unispeaking.domain.vo.scene.IeltsPart.PART_2),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class),
				org.mockito.ArgumentMatchers.anyString());
		verify(ieltsLlmClient, never()).assessFullTest(
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString());
		ArgumentCaptor<com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult>
				captor = ArgumentCaptor.forClass(
						com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult.class);
		verify(evaluationRepository).saveFinal(eq(ieltsId), captor.capture());
		assertEquals(result, captor.getValue());
		verify(evaluationRepository).savePart(
				eq(ieltsId),
				eq("session-p3"),
				any());

		IeltsEvaluationEntity saved = new IeltsEvaluationEntity();
		saved.setEvaluationId("ielts_mock_" + ieltsId);
		saved.setIeltsId(ieltsId);
		saved.setOverallBandScore(new BigDecimal("7.0"));
		saved.setFluencyCoherenceScore(new BigDecimal("7.0"));
		saved.setCreatedAt(OffsetDateTime.parse("2026-08-05T08:00:00Z"));
		when(evaluationRepository.findFinal(ieltsId))
				.thenReturn(Optional.of(saved));
		assertEquals(
				new BigDecimal("7.0"),
				service.getLatestIeltsEstimatedScore());
		assertEquals(
				"A useful object",
				service.getIeltsEvaluationHistory().getFirst()
						.topicTitles().get(
								com.unispeaking.domain.vo.scene.IeltsPart.PART_2));
	}

	private PracticeSessionRecord session(
			String sessionId,
			UUID userId,
			String ieltsId,
			int minute) {
		Instant startedAt = Instant.parse("2026-08-04T08:00:00Z")
				.plusSeconds(minute * 60L);
		return new PracticeSessionRecord(
				sessionId,
				userId,
				ieltsId,
				SceneType.IELTS_SCENE,
				SessionStatus.COMPLETED,
				startedAt,
				startedAt.plusSeconds(60));
	}

	private CustomTurnEvaluation scorableTurn(
			String sceneId,
			String sessionId) {
		return new CustomTurnEvaluation(
				sceneId,
				sessionId,
				1,
				"Candidate answer with enough detail",
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

	private IeltsPartEvaluationEntity savedPart(
			String ieltsId,
			String sessionId,
			com.unispeaking.domain.vo.scene.IeltsPart part,
			String lexicalScore) {
		IeltsPartEvaluationEntity entity = new IeltsPartEvaluationEntity();
		entity.setPartEvaluationId("ielts_part_" + sessionId);
		entity.setIeltsId(ieltsId);
		entity.setSessionId(sessionId);
		entity.setPart(part.name());
		entity.setFluencyCoherenceScore(new BigDecimal("7.0"));
		entity.setLexicalResourceScore(new BigDecimal(lexicalScore));
		entity.setGrammaticalRangeAccuracyScore(new BigDecimal("6.5"));
		entity.setPronunciationScore(new BigDecimal("7.0"));
		entity.setSummary("已缓存的单 Part 评分");
		entity.setStrengths(new String[]{"能够持续作答"});
		entity.setImprovements(new String[]{"提高表达准确性"});
		entity.setRecommendedExpressions(new String[]{"A clearer expression."});
		entity.setEvaluationStatus("COMPLETED");
		return entity;
	}

	private PronunciationAssessmentResult pronunciationAssessment() {
		return new PronunciationAssessmentResult(
				new BigDecimal("86"),
				new BigDecimal("82"),
				null,
				new BigDecimal("90"),
				new BigDecimal("88"),
				new BigDecimal("84"),
				EndingTone.FALL,
				List.of(new PronunciationWordResult(
						0,
						"journey",
						WordReadStatus.NORMAL,
						new BigDecimal("88"),
						new BigDecimal("88"),
						null,
						List.of(new PronunciationPhonemeResult(
								0,
								"dzh",
								"dzh",
								new BigDecimal("88"),
								0,
								20)))));
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
		byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
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
