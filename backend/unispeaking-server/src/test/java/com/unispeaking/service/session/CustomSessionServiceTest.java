package com.unispeaking.service.session;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.unispeaking.component.session.ObsoleteDialogueCleanup;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.scene.CustomDialogueSceneContext;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.EndCustomSessionCommand;
import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueResponse;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartCustomSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.scene.CustomStage;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.service.evaluation.CustomEvaluationService;
import com.unispeaking.service.scene.CustomSceneFlowService;
import com.unispeaking.service.scene.CustomSceneService;
import com.unispeaking.service.session.CustomSessionService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomSessionServiceTest {

	private CustomSessionService service(
			CustomSceneService scenes,
			SessionLifecycleManager lifecycle,
			CustomSceneFlowService flow,
			RealtimeSessionCoordinator coordinator,
			CustomEvaluationService evaluation,
			ObsoleteDialogueCleanup cleanup) {
		return new CustomSessionService(scenes, lifecycle, flow, coordinator, evaluation, cleanup);
	}

	@Test
	void repracticeReusesDialogueFlowWithoutReplayingLearningStages() {
		CustomSceneService scenes = mock(CustomSceneService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		CustomSceneFlowService flow = mock(CustomSceneFlowService.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		CustomSessionService service = service(scenes, lifecycle, flow, coordinator,
				mock(CustomEvaluationService.class), mock(ObsoleteDialogueCleanup.class));
		String sceneId = "scene-1";
		StartCustomSceneDialogueRequest request = mock(StartCustomSceneDialogueRequest.class);
		CustomDialogueSceneContext context = mock(CustomDialogueSceneContext.class);
		StartSessionResponse started = mock(StartSessionResponse.class);
		when(scenes.prepareDialogue(sceneId)).thenReturn(context);
		when(context.sceneId()).thenReturn(sceneId);
		when(context.userId()).thenReturn("user-1");
		when(context.scene()).thenReturn(mock(SceneGenerationResponse.class));
		when(flow.current(sceneId)).thenReturn(CustomStage.DIALOGUE);
		when(lifecycle.startSession(any())).thenReturn(started);
		when(started.sessionId()).thenReturn("session-1");

		service.startSession(new StartCustomSessionCommand(sceneId, request));

		verify(flow, never()).start(sceneId);
		verify(flow, never()).next(sceneId);
		verify(flow).startDialogueState(
				sceneId,
				"session-1",
				context.successFactorJson(),
				context.learningGoal());
	}

	@Test
	void endSessionGeneratesTheSceneReportAndReturnsIt() {
		CustomSceneService scenes = mock(CustomSceneService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		CustomSceneFlowService flow = mock(CustomSceneFlowService.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		CustomEvaluationService evaluation = mock(CustomEvaluationService.class);
		ObsoleteDialogueCleanup cleanup = mock(ObsoleteDialogueCleanup.class);
		CustomSessionService service = new CustomSessionService(
				scenes,
				lifecycle,
				flow,
				coordinator,
				evaluation,
				cleanup);

		String sceneId = "scene-1";
		String sessionId = "session-1";
		String userId = "user-1";
		CustomSceneDefinition definition = new CustomSceneDefinition(
				sceneId,
				userId,
				"Ordering",
				"餐饮",
				null,
				null,
				null,
				"order a drink",
				null,
				null,
				List.of(),
				List.of(),
				List.of());
		CustomSceneSession session = new CustomSceneSession(sessionId, userId);
		session.setSceneId(sceneId);
		session.setSceneType(SceneType.CUSTOM_SCENE);
		DialogueReportResult report = new DialogueReportResult(
				new BigDecimal("80"),
				new BigDecimal("81"),
				new BigDecimal("82"),
				new BigDecimal("83"),
				new BigDecimal("84"),
				new BigDecimal("82"),
				"summary",
				List.of("clear"),
				List.of("more variety"));
		when(scenes.getOwnedDefinition(sceneId)).thenReturn(definition);
		when(coordinator.requireOwnedSession(userId, sessionId))
				.thenReturn(session);
		when(flow.beginDialogueClosing(sceneId, sessionId)).thenReturn(null);
		when(flow.isCompleted(sceneId)).thenReturn(true);
		when(evaluation.generateReport(sceneId)).thenReturn(report);
		doAnswer(invocation -> {
			session.complete();
			return null;
		}).when(lifecycle).endSession(sessionId);

		CompleteCustomSceneDialogueResponse response = service.endSession(
				new EndCustomSessionCommand(sceneId, sessionId, "client-time"));

		assertSame(report, response.evaluation());
		verify(lifecycle).endSession(sessionId);
		verify(evaluation).generateReport(sceneId);
		verify(flow).clearDialogueState(sessionId);
		verify(coordinator).remove(sessionId);
		verify(cleanup).retainLatestDialogue(sceneId, sessionId);
	}

	@Test
	void startsDialogueFromMissingFlowAndAdvancesThroughLearningStages() {
		CustomSceneService scenes = mock(CustomSceneService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		CustomSceneFlowService flow = mock(CustomSceneFlowService.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		CustomDialogueSceneContext context = mock(CustomDialogueSceneContext.class);
		StartSessionResponse started = mock(StartSessionResponse.class);
		StartCustomSceneDialogueRequest request = mock(StartCustomSceneDialogueRequest.class);
		when(scenes.prepareDialogue("scene")).thenReturn(context);
		when(context.sceneId()).thenReturn("scene");
		when(context.userId()).thenReturn("user");
		when(context.scene()).thenReturn(mock(SceneGenerationResponse.class));
		when(flow.current("scene")).thenThrow(new BusinessException("SCENE_FLOW_NOT_FOUND", "missing"));
		when(flow.start("scene")).thenReturn(CustomStage.WORD);
		when(flow.next("scene")).thenReturn(CustomStage.DIALOGUE);
		when(lifecycle.startSession(any())).thenReturn(started);
		when(started.sessionId()).thenReturn("session");
		when(coordinator.connect(any(), any(), any(), any(Boolean.class), any(), any(), any(), any(),
				any(), any(), any(), any(), any())).thenReturn(mock(StartSceneSessionResponse.class));

		service(scenes, lifecycle, flow, coordinator, mock(CustomEvaluationService.class),
				mock(ObsoleteDialogueCleanup.class)).startSession(
				new StartCustomSessionCommand("scene", request));

		verify(flow).start("scene");
		verify(flow).next("scene");
	}

	@Test
	void restartsCompletedFlowAndCleansStateWhenRealtimeConnectFails() {
		CustomSceneService scenes = mock(CustomSceneService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		CustomSceneFlowService flow = mock(CustomSceneFlowService.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		CustomDialogueSceneContext context = mock(CustomDialogueSceneContext.class);
		StartSessionResponse started = mock(StartSessionResponse.class);
		StartCustomSceneDialogueRequest request = mock(StartCustomSceneDialogueRequest.class);
		when(scenes.prepareDialogue("scene")).thenReturn(context);
		when(context.sceneId()).thenReturn("scene");
		when(context.userId()).thenReturn("user");
		when(context.scene()).thenReturn(mock(SceneGenerationResponse.class));
		when(flow.current("scene")).thenReturn(CustomStage.COMPLETED);
		when(flow.start("scene")).thenReturn(CustomStage.DIALOGUE);
		when(lifecycle.startSession(any())).thenReturn(started);
		when(started.sessionId()).thenReturn("session");
		doThrow(new IllegalStateException("connect failed")).when(coordinator).connect(
			any(), any(), any(), any(Boolean.class), any(), any(), any(), any(),
			any(), any(), any(), any(), any());

		assertThrows(IllegalStateException.class, () -> service(scenes, lifecycle, flow, coordinator,
				mock(CustomEvaluationService.class), mock(ObsoleteDialogueCleanup.class)).startSession(
				new StartCustomSessionCommand("scene", request)));
		verify(flow).start("scene");
		verify(flow).clearDialogueState("session");
	}

	@Test
	void rejectsNonCustomSessionBindingAndForwardsMessages() {
		CustomSceneService scenes = mock(CustomSceneService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		CustomSceneFlowService flow = mock(CustomSceneFlowService.class);
		CustomSceneDefinition definition = new CustomSceneDefinition(
				"scene", "user", "title", "label", null, null, null, "goal", null, null,
				List.of(), List.of(), List.of());
		CustomSceneSession session = new CustomSceneSession("session", "user");
		session.setSceneId("other");
		session.setSceneType(SceneType.CUSTOM_SCENE);
		when(scenes.getOwnedDefinition("scene")).thenReturn(definition);
		when(coordinator.requireOwnedSession("user", "session")).thenReturn(session);
		CustomSessionService service = service(scenes, lifecycle, flow,
				coordinator, mock(CustomEvaluationService.class), mock(ObsoleteDialogueCleanup.class));

		BusinessException exception = assertThrows(BusinessException.class,
				() -> service.endSession(new EndCustomSessionCommand("scene", "session", "time")));
		assertEquals("SESSION_ACCESS_DENIED", exception.code());
		Message message = mock(Message.class);
		service.addMessage("session", message);
		verify(lifecycle).addMessage("session", message);
	}
}
