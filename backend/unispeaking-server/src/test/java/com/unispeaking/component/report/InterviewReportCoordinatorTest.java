package com.unispeaking.component.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.evaluation.InterviewReportRecord;
import com.unispeaking.domain.po.scene.InterviewSceneDefinition;
import com.unispeaking.domain.po.session.LearnerMessageRecord;
import com.unispeaking.domain.vo.evaluation.ReportStatus;
import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.infrastructure.evaluation.client.PronunciationAssessmentClient;
import com.unispeaking.infrastructure.persistence.repository.evaluation.InterviewReportRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.InterviewSceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.AiProviderRegistry.RoutedResult;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link InterviewReportCoordinator} 音频降级/重试预算的 Mockito 单测。
 * {@code interviewEvaluationExecutor} 注入同步执行器（{@code command -> command.run()}），
 * 逐轮语音评分为真实有界池，桩数据返回即完成。
 */
class InterviewReportCoordinatorTest {

	private static final String SESSION_ID = "session-1";
	private static final String SCENE_ID = "interview_1";
	private static final String USER_ID = "user-1";

	private final InterviewReportRepository reportRepository =
			mock(InterviewReportRepository.class);
	private final SessionMessageRepository sessionMessageRepository =
			mock(SessionMessageRepository.class);
	private final InterviewSceneRepository sceneRepository =
			mock(InterviewSceneRepository.class);
	private final PronunciationAssessmentClient pronunciationClient =
			mock(PronunciationAssessmentClient.class);
	private final AiProviderRegistry providerRegistry = mock(AiProviderRegistry.class);
	private final RecordingStore recordingStore = mock(RecordingStore.class);

	private InterviewReportCoordinator coordinator;

	@BeforeEach
	void setUp() {
		coordinator = new InterviewReportCoordinator(
				reportRepository,
				sessionMessageRepository,
				sceneRepository,
				pronunciationClient,
				providerRegistry,
				recordingStore,
				command -> command.run(),
				new ObjectMapper());
	}

	@Test
	void failsWithProviderRetryableWhenAllAudioTurnsFailWithinRetryBudget() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of(turn(1, "first transcript")));
		when(recordingStore.readAudio(SESSION_ID, "turn-1.wav"))
				.thenReturn(wavWithSampleRate(16_000));
		when(pronunciationClient.evaluate(anyString(), any()))
				.thenThrow(new EvaluationException(
						EvaluationErrorCode.PROVIDER_NOT_CONFIGURED));
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.of(record(ReportStatus.PROCESSING, 0)))
				.thenReturn(Optional.of(record(ReportStatus.FAILED, 0)));
		when(reportRepository.retryFromFailed(SESSION_ID, 0)).thenReturn(false);

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(reportRepository).markFailed(SESSION_ID, "PROVIDER_RETRYABLE");
		verify(reportRepository).retryFromFailed(SESSION_ID, 0);
		verify(reportRepository, never()).markCompleted(any());
	}

	@Test
	void degradesToCompletedWhenAllAudioTurnsFailAfterRetryBudgetExhausted() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of(turn(1, "first transcript")));
		when(recordingStore.readAudio(SESSION_ID, "turn-1.wav"))
				.thenReturn(wavWithSampleRate(16_000));
		when(pronunciationClient.evaluate(anyString(), any()))
				.thenThrow(new EvaluationException(
						EvaluationErrorCode.PROVIDER_NOT_CONFIGURED));
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.of(record(ReportStatus.PROCESSING, 1)));
		when(sessionMessageRepository.findMessages(SESSION_ID))
				.thenReturn(List.of(
						new Message(0, "Tell me about yourself", null),
						new Message(1, "I am a backend engineer", null)));
		when(sceneRepository.findById(SCENE_ID))
				.thenReturn(Optional.of(sceneDefinition()));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed(validLlmJson()));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		ArgumentCaptor<InterviewReportRecord> completed =
				ArgumentCaptor.forClass(InterviewReportRecord.class);
		verify(reportRepository).markCompleted(completed.capture());
		verify(reportRepository, never()).markFailed(anyString(), anyString());
		InterviewReportRecord record = completed.getValue();
		assertEquals(ReportStatus.COMPLETED, record.status());
		assertNull(record.fluencyScore());
		assertNull(record.pronunciationIntelligibilityScore());
		assertTrue(record.summary().contains("发音评分服务暂不可用"));
	}

	@Test
	void completesWithPartialTurnsWhenOnlySomeAudioTurnsFail() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of(
						turn(1, "first transcript"),
						turn(2, "second transcript")));
		when(recordingStore.readAudio(SESSION_ID, "turn-1.wav"))
				.thenReturn(wavWithSampleRate(16_000));
		when(recordingStore.readAudio(SESSION_ID, "turn-2.wav"))
				.thenReturn(wavWithSampleRate(16_000));
		when(pronunciationClient.evaluate(eq("first transcript"), any()))
				.thenThrow(new EvaluationException(
						EvaluationErrorCode.PROVIDER_NOT_CONFIGURED));
		when(pronunciationClient.evaluate(eq("second transcript"), any()))
				.thenReturn(validAssessment());
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.of(record(ReportStatus.PROCESSING, 0)));
		when(sessionMessageRepository.findMessages(SESSION_ID))
				.thenReturn(List.of(
						new Message(0, "Tell me about yourself", null),
						new Message(1, "I am a backend engineer", null)));
		when(sceneRepository.findById(SCENE_ID))
				.thenReturn(Optional.of(sceneDefinition()));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed(validLlmJson()));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		ArgumentCaptor<InterviewReportRecord> completed =
				ArgumentCaptor.forClass(InterviewReportRecord.class);
		verify(reportRepository).markCompleted(completed.capture());
		verify(reportRepository, never()).markFailed(anyString(), anyString());
		InterviewReportRecord record = completed.getValue();
		assertEquals(ReportStatus.COMPLETED, record.status());
		assertTrue(record.fluencyScore().compareTo(BigDecimal.ZERO) > 0);
		assertTrue(record.pronunciationIntelligibilityScore().compareTo(BigDecimal.ZERO) > 0);
		assertTrue(record.summary().contains("部分轮次"));
	}

	@Test
	void failsWithLlmUnparseableWhenLlmReturnsNonJson() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of());
		when(sessionMessageRepository.findMessages(SESSION_ID))
				.thenReturn(List.of(new Message(1, "I am a backend engineer", null)));
		when(sceneRepository.findById(SCENE_ID))
				.thenReturn(Optional.of(sceneDefinition()));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed("this is not json"));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(reportRepository).markFailed(SESSION_ID, "LLM_UNPARSEABLE");
		verify(reportRepository, never()).markCompleted(any());
	}

	@Test
	void failsWithProviderRetryableWhenLlmProviderThrows() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of());
		when(sessionMessageRepository.findMessages(SESSION_ID))
				.thenReturn(List.of(new Message(1, "I am a backend engineer", null)));
		when(sceneRepository.findById(SCENE_ID))
				.thenReturn(Optional.of(sceneDefinition()));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenThrow(new BusinessException("QWEN_LLM_CALL_FAILED", "llm unavailable"));
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.of(record(ReportStatus.FAILED, 0)));
		when(reportRepository.retryFromFailed(SESSION_ID, 0)).thenReturn(false);

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(reportRepository).markFailed(SESSION_ID, "PROVIDER_RETRYABLE");
		verify(reportRepository, never()).markCompleted(any());
	}

	@Test
	void asksLlmToWriteReportNarrativesInSimplifiedChinese() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of());
		when(sessionMessageRepository.findMessages(SESSION_ID))
				.thenReturn(List.of(new Message(1, "I am a backend engineer", null)));
		when(sceneRepository.findById(SCENE_ID))
				.thenReturn(Optional.of(sceneDefinition()));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed(validLlmJson()));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(providerRegistry).executeLlmTaskRouted(prompt.capture(), isNull());
		assertTrue(prompt.getValue().contains("Simplified Chinese"));
		assertTrue(prompt.getValue().contains("ALL \"evaluation\""));
		assertTrue(prompt.getValue().contains("\"advice\", and \"summary\" fields"));
		assertTrue(prompt.getValue().contains("property names and score values"));
	}

	@Test
	void redispatchesOnlyExpiredProcessingReports() {
		List<Runnable> queued = new java.util.ArrayList<>();
		InterviewReportCoordinator queuedCoordinator = new InterviewReportCoordinator(
				reportRepository, sessionMessageRepository, sceneRepository, pronunciationClient,
				providerRegistry, recordingStore, queued::add, new ObjectMapper());
		when(reportRepository.findById(SESSION_ID)).thenReturn(Optional.of(
				record(ReportStatus.PROCESSING, 0, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(3))));

		queuedCoordinator.redispatchIfStale(SESSION_ID, SCENE_ID, USER_ID);

		assertEquals(1, queued.size());
	}

	@Test
	void doesNotRedispatchFreshOrTerminalReports() {
		List<Runnable> queued = new java.util.ArrayList<>();
		InterviewReportCoordinator queuedCoordinator = new InterviewReportCoordinator(
				reportRepository, sessionMessageRepository, sceneRepository, pronunciationClient,
				providerRegistry, recordingStore, queued::add, new ObjectMapper());
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.of(record(ReportStatus.PROCESSING, 0, OffsetDateTime.now(ZoneOffset.UTC))))
				.thenReturn(Optional.of(record(ReportStatus.COMPLETED, 0, null)));

		queuedCoordinator.redispatchIfStale(SESSION_ID, SCENE_ID, USER_ID);
		queuedCoordinator.redispatchIfStale(SESSION_ID, SCENE_ID, USER_ID);

		assertTrue(queued.isEmpty());
	}

	@Test
	void sweepsEveryStuckReportAndIgnoresRepositoryFailure() {
		List<Runnable> queued = new java.util.ArrayList<>();
		InterviewReportCoordinator queuedCoordinator = new InterviewReportCoordinator(
				reportRepository, sessionMessageRepository, sceneRepository, pronunciationClient,
				providerRegistry, recordingStore, queued::add, new ObjectMapper());
		when(reportRepository.findStuckProcessingBefore(any())).thenReturn(List.of(
				record(ReportStatus.PROCESSING, 0),
				new InterviewReportRecord("session-2", SCENE_ID, USER_ID, ReportStatus.PROCESSING,
					null, null, null, null, null, null, null, null, null, null, null, null, null,
					null, null, null, null, 0, null, null, null)));

		queuedCoordinator.sweepStuckProcessing();
		assertEquals(2, queued.size());

		when(reportRepository.findStuckProcessingBefore(any())).thenThrow(new IllegalStateException("db down"));
		queuedCoordinator.sweepStuckProcessing();
		assertEquals(2, queued.size());
	}

	@Test
	void responseContainsReportOnlyForCompletedRecord() {
		InterviewReportRecord completed = new InterviewReportRecord(
				SESSION_ID, SCENE_ID, USER_ID, ReportStatus.COMPLETED, new BigDecimal("81.0"),
				"summary", new BigDecimal("80.0"), "fluency", "advice", new BigDecimal("79.0"),
				"pronunciation", "advice", new BigDecimal("78.0"), "logic", "advice",
				new BigDecimal("77.0"), "grammar", "advice", new BigDecimal("76.0"),
				"vocabulary", "advice", 0, null, OffsetDateTime.now(ZoneOffset.UTC),
				OffsetDateTime.now(ZoneOffset.UTC));

		assertEquals(5, coordinator.toResponse(completed).report().dimensions().size());
		assertFalse(coordinator.toResponse(record(ReportStatus.FAILED, 0)).report() != null);
	}

	@Test
	void rejectsDuplicateSubmissionAndCleansUpRejectedExecutorSubmission() {
		List<Runnable> queued = new java.util.ArrayList<>();
		InterviewReportCoordinator queuedCoordinator = new InterviewReportCoordinator(
				reportRepository, sessionMessageRepository, sceneRepository, pronunciationClient,
				providerRegistry, recordingStore, queued::add, new ObjectMapper());

		queuedCoordinator.submit(SESSION_ID, SCENE_ID, USER_ID);
		queuedCoordinator.submit(SESSION_ID, SCENE_ID, USER_ID);
		assertEquals(1, queued.size());

		InterviewReportCoordinator rejectedCoordinator = new InterviewReportCoordinator(
				reportRepository, sessionMessageRepository, sceneRepository, pronunciationClient,
				providerRegistry, recordingStore,
				command -> { throw new java.util.concurrent.RejectedExecutionException("full"); },
				new ObjectMapper());
		rejectedCoordinator.submit("rejected", SCENE_ID, USER_ID);
		verifyNoInteractionsForSession("rejected");
	}

	@Test
	void acceptsFencedLlmJsonAndFallsBackWhenSceneTopicsAreUnavailable() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID)).thenReturn(List.of());
		when(sessionMessageRepository.findMessages(SESSION_ID))
				.thenReturn(List.of(new Message(1, "candidate answer", null)));
		when(sceneRepository.findById(SCENE_ID)).thenReturn(Optional.empty());
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed("```json\n" + validLlmJson().strip() + "\n```"));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		ArgumentCaptor<InterviewReportRecord> completed = ArgumentCaptor.forClass(InterviewReportRecord.class);
		verify(reportRepository).markCompleted(completed.capture());
		assertTrue(completed.getValue().summary().contains("无可用录音"));
	}

	@Test
	void rejectsInvalidLlmFieldsWithoutPersistingCompletedReport() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID)).thenReturn(List.of());
		when(sessionMessageRepository.findMessages(SESSION_ID))
				.thenReturn(List.of(new Message(1, "candidate answer", null)));
		when(sceneRepository.findById(SCENE_ID)).thenReturn(Optional.of(sceneDefinition()));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed(validLlmJson().replace("\"overall_score\": 80", "\"overall_score\": 101")));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(reportRepository).markFailed(SESSION_ID, "LLM_UNPARSEABLE");
		verify(reportRepository, never()).markCompleted(any());
	}

	@Test
	void retriesProviderFailureAndResubmitsOnlyAfterTheFirstTaskLeavesRunningSet() {
		List<Runnable> queued = new ArrayList<>();
		InterviewReportCoordinator queuedCoordinator = new InterviewReportCoordinator(
				reportRepository,
				sessionMessageRepository,
				sceneRepository,
				pronunciationClient,
				providerRegistry,
				recordingStore,
				queued::add,
				new ObjectMapper());
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of(turn(1, "first transcript")))
				.thenReturn(List.of());
		when(recordingStore.readAudio(SESSION_ID, "turn-1.wav"))
				.thenReturn(wavWithSampleRate(16_000));
		when(pronunciationClient.evaluate(anyString(), any()))
				.thenThrow(new EvaluationException(EvaluationErrorCode.PROVIDER_NOT_CONFIGURED));
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.of(record(ReportStatus.PROCESSING, 0)))
				.thenReturn(Optional.of(record(ReportStatus.FAILED, 0)))
				.thenReturn(Optional.of(record(ReportStatus.PROCESSING, 1)));
		when(reportRepository.retryFromFailed(SESSION_ID, 0)).thenReturn(true);
		when(sessionMessageRepository.findMessages(SESSION_ID)).thenReturn(List.of());
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed(validLlmJson()));

		queuedCoordinator.submit(SESSION_ID, SCENE_ID, USER_ID);
		assertEquals(1, queued.size());
		Runnable firstTask = queued.removeFirst();
		firstTask.run();

		assertEquals(1, queued.size());
		queued.removeFirst().run();
		verify(reportRepository).retryFromFailed(SESSION_ID, 0);
		verify(reportRepository).markCompleted(any(InterviewReportRecord.class));
	}

	@Test
	void mapsUnexpectedProcessingFailureToProviderRetryable() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenThrow(new IllegalStateException("database unavailable"));
		when(reportRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(reportRepository).markFailed(SESSION_ID, "PROVIDER_RETRYABLE");
		verify(reportRepository, never()).retryFromFailed(anyString(), any(Integer.class));
	}

	@Test
	void stopsWhenMarkingFailureThrowsAndDoesNotAttemptRetry() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenThrow(new IllegalStateException("database unavailable"));
		doThrow(new IllegalStateException("write unavailable"))
				.when(reportRepository).markFailed(SESSION_ID, "PROVIDER_RETRYABLE");

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(reportRepository, never()).retryFromFailed(anyString(), any(Integer.class));
	}

	@Test
	void doesNotRetryWhenReportIsMissingTerminalOrAlreadyRetried() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenThrow(new IllegalStateException("temporary"));
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(record(ReportStatus.COMPLETED, 0)))
				.thenReturn(Optional.of(record(ReportStatus.FAILED, 1)));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);
		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);
		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(reportRepository, never()).retryFromFailed(anyString(), any(Integer.class));
	}

	@Test
	void keepsProcessingWhenRetryCasReturnsFalse() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenThrow(new IllegalStateException("temporary"));
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.of(record(ReportStatus.FAILED, 0)));
		when(reportRepository.retryFromFailed(SESSION_ID, 0)).thenReturn(false);

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(reportRepository).retryFromFailed(SESSION_ID, 0);
	}

	@Test
	void ignoresAutoRetryRepositoryFailure() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenThrow(new IllegalStateException("temporary"));
		when(reportRepository.findById(SESSION_ID))
				.thenThrow(new IllegalStateException("database unavailable"));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(reportRepository, never()).retryFromFailed(anyString(), any(Integer.class));
	}

	@Test
	void skipsBlankMissingAndInvalidAudioBeforeCallingPronunciationProvider() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of(
						new LearnerMessageRecord(1, "blank key", " "),
						new LearnerMessageRecord(2, "missing audio", "missing.wav"),
						new LearnerMessageRecord(3, "bad wav", "bad.wav")));
		when(recordingStore.readAudio(SESSION_ID, "missing.wav")).thenReturn(null);
		when(recordingStore.readAudio(SESSION_ID, "bad.wav"))
				.thenReturn(new byte[] {1, 2, 3});
		when(sessionMessageRepository.findMessages(SESSION_ID)).thenReturn(List.of());
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed(validLlmJson()));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(pronunciationClient, never()).evaluate(anyString(), any());
		ArgumentCaptor<InterviewReportRecord> completed = ArgumentCaptor.forClass(InterviewReportRecord.class);
		verify(reportRepository).markCompleted(completed.capture());
		assertTrue(completed.getValue().summary().contains("无可用录音"));
	}

	@Test
	void degradesWhenProviderReturnsAnIncompleteAssessment() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of(turn(1, "first transcript")));
		when(recordingStore.readAudio(SESSION_ID, "turn-1.wav"))
				.thenReturn(wavWithSampleRate(16_000));
		when(pronunciationClient.evaluate(anyString(), any()))
				.thenThrow(new EvaluationException(EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE));
		when(reportRepository.findById(SESSION_ID)).thenReturn(Optional.of(record(ReportStatus.PROCESSING, 1)));
		when(sessionMessageRepository.findMessages(SESSION_ID)).thenReturn(List.of());
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed(validLlmJson()));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		ArgumentCaptor<InterviewReportRecord> completed = ArgumentCaptor.forClass(InterviewReportRecord.class);
		verify(reportRepository).markCompleted(completed.capture());
		assertNull(completed.getValue().fluencyScore());
		assertTrue(completed.getValue().summary().contains("服务暂不可用"));
	}

	@Test
	void countsUnexpectedAudioProviderExceptionsAsFailedTurns() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of(turn(1, "first transcript")));
		when(recordingStore.readAudio(SESSION_ID, "turn-1.wav"))
				.thenReturn(wavWithSampleRate(16_000));
		when(pronunciationClient.evaluate(anyString(), any()))
				.thenThrow(new IllegalStateException("provider bug"));
		when(reportRepository.findById(SESSION_ID)).thenReturn(Optional.of(record(ReportStatus.PROCESSING, 1)));
		when(sessionMessageRepository.findMessages(SESSION_ID)).thenReturn(List.of());
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed(validLlmJson()));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		ArgumentCaptor<InterviewReportRecord> completed = ArgumentCaptor.forClass(InterviewReportRecord.class);
		verify(reportRepository).markCompleted(completed.capture());
		assertTrue(completed.getValue().summary().contains("有效语音不足"));
	}

	@Test
	void readsOnlyTextTopicsAndFallsBackForMalformedSceneContext() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(anyString())).thenReturn(List.of());
		when(sessionMessageRepository.findMessages(anyString())).thenReturn(List.of(new Message(1, "answer", null)));
		when(sceneRepository.findById(SCENE_ID)).thenReturn(Optional.of(new InterviewSceneDefinition(
				SCENE_ID, USER_ID, "{}", "final", "{\"interviewTopics\":[\" topic \",\" \",3]}",
				InterviewDifficulty.STANDARD, "prompt", OffsetDateTime.now(), OffsetDateTime.now(), null)));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull())).thenReturn(routed(validLlmJson()));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(providerRegistry).executeLlmTaskRouted(prompt.capture(), isNull());
		assertTrue(prompt.getValue().contains("- topic"));
		assertFalse(prompt.getValue().contains("- 3"));

		when(sceneRepository.findById(SCENE_ID)).thenReturn(Optional.of(new InterviewSceneDefinition(
				SCENE_ID, USER_ID, "{}", "final", "not-json", InterviewDifficulty.STANDARD,
				"prompt", OffsetDateTime.now(), OffsetDateTime.now(), null)));
		coordinator.submit("session-2", SCENE_ID, USER_ID);
		verify(providerRegistry, org.mockito.Mockito.atLeast(2)).executeLlmTaskRouted(anyString(), isNull());
	}

	@Test
	void storesConcatenatedValidSessionAudioAndIgnoresStoreFailure() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(anyString())).thenReturn(List.of(
				turn(1, "first"), new LearnerMessageRecord(2, "second", "turn-2.wav"),
				new LearnerMessageRecord(3, "invalid", "invalid.wav")));
		byte[] first = wavWithSampleRate(16_000);
		byte[] second = wavWithSampleRate(16_000);
		when(recordingStore.readAudio(anyString(), eq("turn-1.wav"))).thenReturn(first);
		when(recordingStore.readAudio(anyString(), eq("turn-2.wav"))).thenReturn(second);
		when(recordingStore.readAudio(anyString(), eq("invalid.wav"))).thenReturn(new byte[] {1});
		when(pronunciationClient.evaluate(anyString(), any())).thenReturn(validAssessment());
		when(sessionMessageRepository.findMessages(anyString())).thenReturn(List.of());
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull())).thenReturn(routed(validLlmJson()));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
		verify(recordingStore).storeSessionAudio(eq(SESSION_ID), stored.capture());
		assertEquals(48, stored.getValue().length);
		assertEquals('R', stored.getValue()[0]);
		assertEquals(16_000, littleEndianInt(stored.getValue(), 24));

		doThrow(new IllegalStateException("storage unavailable"))
				.when(recordingStore).storeSessionAudio(eq(SESSION_ID), any());
		coordinator.submit("session-2", SCENE_ID, USER_ID);
		verify(reportRepository, org.mockito.Mockito.atLeast(2)).markCompleted(any(InterviewReportRecord.class));
	}

	@Test
	void coversNullAndFreshStatesWhenRedispatching() {
		List<Runnable> queued = new ArrayList<>();
		InterviewReportCoordinator queuedCoordinator = new InterviewReportCoordinator(
				reportRepository, sessionMessageRepository, sceneRepository, pronunciationClient,
				providerRegistry, recordingStore, queued::add, new ObjectMapper());
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(record(ReportStatus.PROCESSING, 0, null)));

		queuedCoordinator.redispatchIfStale(SESSION_ID, SCENE_ID, USER_ID);
		queuedCoordinator.redispatchIfStale(SESSION_ID, SCENE_ID, USER_ID);

		assertEquals(1, queued.size());
	}

	private void verifyNoInteractionsForSession(String sessionId) {
		verify(reportRepository, never()).markFailed(eq(sessionId), anyString());
		verify(sessionMessageRepository, never()).findMessagesWithAudioObjectKeys(eq(sessionId));
	}

	private LearnerMessageRecord turn(int messageNo, String content) {
		return new LearnerMessageRecord(
				messageNo,
				content,
				"turn-" + messageNo + ".wav");
	}

	private InterviewReportRecord record(ReportStatus status, int retryCount) {
		return record(status, retryCount, null);
	}

	private InterviewReportRecord record(
			ReportStatus status,
			int retryCount,
			OffsetDateTime updatedAt) {
		return new InterviewReportRecord(
				SESSION_ID,
				SCENE_ID,
				USER_ID,
				status,
				null,
				null,
				null, null, null,
				null, null, null,
				null, null, null,
				null, null, null,
				null, null, null,
				retryCount,
				null,
				null,
				updatedAt);
	}

	private InterviewSceneDefinition sceneDefinition() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return new InterviewSceneDefinition(
				SCENE_ID,
				USER_ID,
				"{}",
				"final text",
				"{\"interviewTopics\":[\"自我介绍\",\"项目经历\"]}",
				InterviewDifficulty.STANDARD,
				"prompt",
				now,
				now,
				null);
	}

	private RoutedResult<String> routed(String content) {
		return new RoutedResult<>(
				"qwen3.5-plus",
				"qwen",
				AiCapability.LLM,
				content);
	}

	private String validLlmJson() {
		return """
				{
				  "logic_coherence": {"score": 80, "evaluation": "clear structure", "advice": "keep it up"},
				  "grammar_control": {"score": 75, "evaluation": "mostly accurate", "advice": "mind tenses"},
				  "vocabulary_expression": {"score": 85, "evaluation": "rich vocabulary", "advice": "vary more"},
				  "fluency": {"evaluation": "fluent", "advice": "maintain pace"},
				  "pronunciation_intelligibility": {"evaluation": "clear", "advice": "watch intonation"},
				  "overall_score": 80,
				  "summary": "Overall solid performance."
				}
				""";
	}

	private PronunciationAssessmentResult validAssessment() {
		PronunciationPhonemeResult phoneme = new PronunciationPhonemeResult(
				0, "t", "t", new BigDecimal("80.0"), 0, 60);
		PronunciationWordResult word = new PronunciationWordResult(
				0,
				"test",
				WordReadStatus.NORMAL,
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				false,
				List.of(phoneme));
		return new PronunciationAssessmentResult(
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				EndingTone.FALL,
				List.of(word));
	}

	private byte[] wavWithSampleRate(int sampleRate) {
		ByteBuffer wav = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
		wav.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.putInt(38);
		wav.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.putInt(16);
		wav.putShort((short) 1);
		wav.putShort((short) 1);
		wav.putInt(sampleRate);
		wav.putInt(sampleRate * 2);
		wav.putShort((short) 2);
		wav.putShort((short) 16);
		wav.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.putInt(2);
		wav.putShort((short) 0);
		return wav.array();
	}

	private int littleEndianInt(byte[] value, int offset) {
		return (value[offset] & 0xff)
				| ((value[offset + 1] & 0xff) << 8)
				| ((value[offset + 2] & 0xff) << 16)
				| ((value[offset + 3] & 0xff) << 24);
	}
}
