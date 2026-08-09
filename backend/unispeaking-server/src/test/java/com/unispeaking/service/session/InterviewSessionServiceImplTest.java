package com.unispeaking.service.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.common.exception.SessionNotFoundException;
import com.unispeaking.component.policy.DailyQuotaPolicy;
import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.component.report.InterviewReportCoordinator;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.dto.evaluation.InterviewEndResponse;
import com.unispeaking.domain.dto.evaluation.InterviewReportResponse;
import com.unispeaking.domain.dto.scene.InterviewDialogueSceneContext;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.InterviewTurnResult;
import com.unispeaking.domain.po.evaluation.InterviewReportRecord;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.vo.evaluation.ReportStatus;
import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.InterviewTopicEvent;
import com.unispeaking.domain.vo.scene.InterviewTopicState;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.persistence.repository.evaluation.InterviewReportRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.InterviewSceneService;
import com.unispeaking.service.session.impl.InterviewSessionServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InterviewSessionServiceImplTest {

	private final InterviewSceneService scenes = mock(InterviewSceneService.class);
	private final DailyQuotaPolicy quota = mock(DailyQuotaPolicy.class);
	private final SessionLifecycleManager lifecycle =
			mock(SessionLifecycleManager.class);
	private final RealtimeSessionCoordinator coordinator =
			mock(RealtimeSessionCoordinator.class);
	private final AuthService authService = mock(AuthService.class);
	private final SessionMessageRepository sessionMessageRepository =
			mock(SessionMessageRepository.class);
	private final InterviewReportRepository interviewReportRepository =
			mock(InterviewReportRepository.class);
	private final InterviewReportCoordinator reportCoordinator =
			mock(InterviewReportCoordinator.class);
	private final RecordingStore interviewRecordingStore =
			mock(RecordingStore.class);
	private final AiProviderRegistry providerRegistry =
			mock(AiProviderRegistry.class);
	private final InterviewSessionServiceImpl service =
			new InterviewSessionServiceImpl(
					scenes,
					quota,
					lifecycle,
					coordinator,
					authService,
					sessionMessageRepository,
					interviewReportRepository,
					reportCoordinator,
					interviewRecordingStore,
					providerRegistry,
					new tools.jackson.databind.ObjectMapper());

	private final String sceneId = "interview_1";
	private final String userId = "user-1";
	private final String scenePrompt = "interview system prompt";
	private final StartCustomSceneDialogueRequest request =
			new StartCustomSceneDialogueRequest(
					"offer-sdp",
					ProviderType.QWEN,
					"qwen3.5-plus",
					"Katerina",
					true);

	@Test
	void startSessionPreparesDialogueChecksQuotaAndConnects() {
		InterviewDialogueSceneContext prepared = new InterviewDialogueSceneContext(
				userId,
				sceneId,
				scenePrompt,
				InterviewDifficulty.STANDARD);
		when(scenes.prepareDialogue(sceneId)).thenReturn(prepared);
		StartSessionResponse started = new StartSessionResponse(
				"session-1",
				"2026-08-09T00:00:00Z");
		when(lifecycle.startSession(any())).thenReturn(started);
		StartSceneSessionResponse expected = response("session-1");
		when(coordinator.connect(
				any(SceneGenerationResponse.class),
				anyString(),
				any(SceneFlowStage.class),
				anyBoolean(),
				any(StartSessionResponse.class),
				any(SceneType.class),
				anyString(),
				anyString(),
				anyString(),
				any(ProviderType.class),
				anyString(),
				anyString(),
				any()))
				.thenReturn(expected);

		StartSceneSessionResponse response =
				service.startSession(sceneId, request);

		assertSame(expected, response);
		ArgumentCaptor<StartSessionCommand> command =
				ArgumentCaptor.forClass(StartSessionCommand.class);
		verify(lifecycle).startSession(command.capture());
		assertEquals(userId, command.getValue().userId());
		assertEquals(sceneId, command.getValue().sceneId());
		assertEquals(SceneType.INTERVIEW_SCENE, command.getValue().sceneType());
		assertEquals(SceneFlowStage.DIALOGUE.name(), command.getValue().stage());
		assertEquals(scenePrompt, command.getValue().prompt());
		verify(coordinator).connect(
				any(SceneGenerationResponse.class),
				eq("模拟面试"),
				eq(SceneFlowStage.DIALOGUE),
				eq(true),
				same(started),
				eq(SceneType.INTERVIEW_SCENE),
				eq(sceneId),
				eq(scenePrompt),
				eq("offer-sdp"),
				eq(ProviderType.QWEN),
				eq("qwen3.5-plus"),
				eq("Katerina"),
				eq(true));
		verify(quota).assertWithinQuota(
				userId,
				SceneType.INTERVIEW_SCENE,
				5);
	}

	@Test
	void startSessionPropagatesQuotaExceeded() {
		InterviewDialogueSceneContext prepared = new InterviewDialogueSceneContext(
				userId,
				sceneId,
				scenePrompt,
				InterviewDifficulty.STANDARD);
		when(scenes.prepareDialogue(sceneId)).thenReturn(prepared);
		doThrow(new BusinessException(
				"INTERVIEW_DAILY_LIMIT_REACHED",
				"今日次数已用尽"))
				.when(quota)
				.assertWithinQuota(userId, SceneType.INTERVIEW_SCENE, 5);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.startSession(sceneId, request));

		assertEquals("INTERVIEW_DAILY_LIMIT_REACHED", exception.code());
		verify(lifecycle, never()).startSession(any());
		verifyNoInteractions(coordinator);
	}

	@Test
	void startSessionPropagatesSceneOwnershipDenied() {
		when(scenes.prepareDialogue(sceneId))
				.thenThrow(new BusinessException(
						"INTERVIEW_SCENE_ACCESS_DENIED",
						"无权访问"));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.startSession(sceneId, request));

		assertEquals("INTERVIEW_SCENE_ACCESS_DENIED", exception.code());
		verify(quota, never()).assertWithinQuota(
				anyString(),
				any(SceneType.class),
				anyInt());
	}

	@Test
	void addMessageDelegatesToLifecycle() {
		Message message = new Message(1, "hello", null);

		service.addMessage("session-1", message);

		verify(lifecycle).addMessage("session-1", message);
	}

	@Test
	void submitTurnRejectsPendingMessage() {
		AbstractSceneSession session = interviewSession(SessionStatus.ACTIVE);
		when(authService.requireUserId(null)).thenReturn(userId);
		when(coordinator.requireOwnedSession(userId, "session-1"))
				.thenReturn(session);
		when(sessionMessageRepository.findLearnerMessages("session-1"))
				.thenReturn(List.of(new Message(1, "first turn", null)));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.submitTurn(sceneId, "session-1", 2, "second turn", null));

		assertEquals(
				InterviewErrorCode.INTERVIEW_TURN_MESSAGE_PENDING,
				exception.code());
	}

	@Test
	void submitTurnRejectsOutOfOrderAndNonPositiveTurn() {
		AbstractSceneSession session = interviewSession(SessionStatus.ACTIVE);
		when(authService.requireUserId(null)).thenReturn(userId);
		when(coordinator.requireOwnedSession(userId, "session-1"))
				.thenReturn(session);
		when(sessionMessageRepository.findLearnerMessages("session-1"))
				.thenReturn(List.of(new Message(1, "first turn", null)));

		assertEquals(
				InterviewErrorCode.INTERVIEW_TURN_OUT_OF_ORDER,
				assertThrows(
						BusinessException.class,
						() -> service.submitTurn(
								sceneId, "session-1", 3, "third", null))
						.code());
		assertEquals(
				InterviewErrorCode.INTERVIEW_TURN_OUT_OF_ORDER,
				assertThrows(
						BusinessException.class,
						() -> service.submitTurn(
								sceneId, "session-1", 0, "zero", null))
						.code());
	}

	@Test
	void submitTurnRejectsContentMismatch() {
		AbstractSceneSession session = interviewSession(SessionStatus.ACTIVE);
		when(authService.requireUserId(null)).thenReturn(userId);
		when(coordinator.requireOwnedSession(userId, "session-1"))
				.thenReturn(session);
		when(sessionMessageRepository.findLearnerMessages("session-1"))
				.thenReturn(List.of(new Message(1, "recorded transcript", null)));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.submitTurn(
						sceneId, "session-1", 1, "different transcript", null));

		assertEquals(
				InterviewErrorCode.INTERVIEW_TURN_CONTENT_MISMATCH,
				exception.code());
	}

	@Test
	void submitTurnRejectsEndedSession() {
		AbstractSceneSession session = interviewSession(SessionStatus.COMPLETED);
		when(authService.requireUserId(null)).thenReturn(userId);
		when(coordinator.requireOwnedSession(userId, "session-1"))
				.thenReturn(session);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.submitTurn(sceneId, "session-1", 1, "hello", null));

		assertEquals(
				InterviewErrorCode.INTERVIEW_SESSION_ENDED,
				exception.code());
	}

	@Test
	void submitTurnIdentifiesTopicAndAdvancesState() {
		AbstractSceneSession session = interviewSession(SessionStatus.ACTIVE);
		when(authService.requireUserId(null)).thenReturn(userId);
		when(coordinator.requireOwnedSession(userId, "session-1"))
				.thenReturn(session);
		when(sessionMessageRepository.findLearnerMessages("session-1"))
				.thenReturn(List.of(new Message(1, "recorded transcript", null)));
		when(scenes.interviewTopics(sceneId))
				.thenReturn(List.of("自我介绍", "项目经历", "团队协作"));
		when(providerRegistry.executeLlmTaskRouted(anyString(), any()))
				.thenReturn(completed(
						"{\"topic\":\"自我介绍\",\"topicCompleted\":false}"));
		when(scenes.advanceTopicState(
				eq(sceneId), eq("session-1"), eq(1), any(InterviewTopicEvent.class)))
				.thenReturn(new InterviewTopicState(
						"自我介绍", 0, 0, 0, 0, false, false));

		InterviewTurnResult result =
				service.submitTurn(sceneId, "session-1", 1, "recorded transcript", null);

		assertEquals("自我介绍", result.state().currentTopic());
		assertEquals(0, result.state().completedTopicCount());
		verify(scenes).advanceTopicState(
				eq(sceneId), eq("session-1"), eq(1), any(InterviewTopicEvent.class));
	}

	private AbstractSceneSession interviewSession(SessionStatus status) {
		AbstractSceneSession session = mock(AbstractSceneSession.class);
		when(session.getSceneType()).thenReturn(SceneType.INTERVIEW_SCENE);
		when(session.getSceneId()).thenReturn(sceneId);
		when(session.getStatus()).thenReturn(status);
		return session;
	}

	@Test
	void endInterviewOrchestratesTerminateCreateAndSubmit() {
		AbstractSceneSession session = interviewSession(SessionStatus.ACTIVE);
		when(authService.requireUserId(null)).thenReturn(userId);
		when(coordinator.requireOwnedSession(userId, "session-1"))
				.thenReturn(session);
		when(interviewReportRepository.createIfAbsent("session-1", sceneId, userId))
				.thenReturn(true);
		when(interviewReportRepository.findById("session-1"))
				.thenReturn(Optional.of(record(ReportStatus.PROCESSING)));

		InterviewEndResponse response = service.endInterview(sceneId, "session-1");

		assertEquals("session-1", response.sessionId());
		assertEquals(ReportStatus.PROCESSING, response.reportStatus());
		verify(lifecycle).terminateSceneSession(
				eq(userId),
				eq("session-1"),
				eq(SessionStatus.COMPLETED),
				any());
		verify(reportCoordinator).submit("session-1", sceneId, userId);
		verify(coordinator).remove("session-1");
	}

	@Test
	void endInterviewDoesNotResubmitWhenReportRowAlreadyExists() {
		AbstractSceneSession session = interviewSession(SessionStatus.COMPLETED);
		when(authService.requireUserId(null)).thenReturn(userId);
		when(coordinator.requireOwnedSession(userId, "session-1"))
				.thenReturn(session);
		when(interviewReportRepository.createIfAbsent("session-1", sceneId, userId))
				.thenReturn(false);
		when(interviewReportRepository.findById("session-1"))
				.thenReturn(Optional.of(record(ReportStatus.COMPLETED)));

		InterviewEndResponse response = service.endInterview(sceneId, "session-1");

		assertEquals(ReportStatus.COMPLETED, response.reportStatus());
		verify(reportCoordinator, never()).submit(anyString(), anyString(), anyString());
		verify(coordinator).remove("session-1");
	}

	@Test
	void endInterviewIsIdempotentWhenSessionAlreadyRemoved() {
		when(authService.requireUserId(null)).thenReturn(userId);
		when(coordinator.requireOwnedSession(userId, "session-1"))
				.thenThrow(new SessionNotFoundException("session-1"));
		when(interviewReportRepository.findById("session-1"))
				.thenReturn(Optional.of(record(ReportStatus.FAILED)));

		InterviewEndResponse response = service.endInterview(sceneId, "session-1");

		assertEquals(ReportStatus.FAILED, response.reportStatus());
		verify(reportCoordinator, never()).submit(anyString(), anyString(), anyString());
	}

	@Test
	void getReportReturnsReadModelWithoutRedispatchForFreshRow() {
		InterviewReportRecord record = record(ReportStatus.COMPLETED);
		InterviewReportResponse response = new InterviewReportResponse(
				"session-1",
				sceneId,
				ReportStatus.COMPLETED,
				null,
				null);
		when(authService.requireUserId(null)).thenReturn(userId);
		when(interviewReportRepository.findById("session-1"))
				.thenReturn(Optional.of(record));
		when(reportCoordinator.toResponse(record)).thenReturn(response);

		InterviewReportResponse actual = service.getReport(sceneId, "session-1");

		assertSame(response, actual);
		verify(reportCoordinator, never()).redispatchIfStale(
				anyString(), anyString(), anyString());
	}

	@Test
	void getReportThrowsNotFoundForMissingRow() {
		when(authService.requireUserId(null)).thenReturn(userId);
		when(interviewReportRepository.findById("session-1"))
				.thenReturn(Optional.empty());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.getReport(sceneId, "session-1"));

		assertEquals(
				InterviewErrorCode.INTERVIEW_REPORT_NOT_FOUND,
				exception.code());
	}

	@Test
	void retryReportCasFailedToProcessingAndSubmits() {
		when(authService.requireUserId(null)).thenReturn(userId);
		when(interviewReportRepository.findById("session-1"))
				.thenReturn(
						Optional.of(record(ReportStatus.FAILED)),
						Optional.of(record(ReportStatus.PROCESSING)));
		when(interviewReportRepository.casFailedToProcessing("session-1"))
				.thenReturn(true);
		when(reportCoordinator.toResponse(any()))
				.thenReturn(new InterviewReportResponse(
						"session-1",
						sceneId,
						ReportStatus.PROCESSING,
						null,
						null));

		InterviewReportResponse response =
				service.retryReport(sceneId, "session-1");

		assertEquals(ReportStatus.PROCESSING, response.status());
		verify(reportCoordinator).submit("session-1", sceneId, userId);
	}

	private InterviewReportRecord record(ReportStatus status) {
		return new InterviewReportRecord(
				"session-1",
				sceneId,
				userId,
				status,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				0,
				status == ReportStatus.FAILED ? "PROVIDER_RETRYABLE" : null,
				null,
				null);
	}

	private AiProviderRegistry.RoutedResult completed(String response) {
		return new AiProviderRegistry.RoutedResult(
				"qwen3.5-plus",
				ProviderType.QWEN.name(),
				AiCapability.LLM,
				response);
	}

	private StartSceneSessionResponse response(String sessionId) {
		return new StartSceneSessionResponse(
				sceneId,
				"模拟面试",
				SceneType.INTERVIEW_SCENE,
				List.of(),
				List.of(),
				List.of(),
				SceneFlowStage.DIALOGUE,
				true,
				sessionId,
				"provider-session",
				"answer-sdp",
				null,
				"Katerina",
				SessionStatus.WAITING_CLIENT,
				"2026-08-09T00:00:00Z",
				scenePrompt);
	}
}
