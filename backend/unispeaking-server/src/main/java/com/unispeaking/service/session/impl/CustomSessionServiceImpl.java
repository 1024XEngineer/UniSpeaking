package com.unispeaking.service.session.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.component.session.ObsoleteDialogueCleanup;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.scene.CustomDialogueSceneContext;
import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueResponse;
import com.unispeaking.domain.dto.session.EndCustomSessionCommand;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartCustomSessionCommand;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.evaluation.CustomEvaluationService;
import com.unispeaking.service.scene.CustomSceneFlowService;
import com.unispeaking.service.scene.CustomSceneService;
import com.unispeaking.service.session.CustomSessionService;
import org.springframework.stereotype.Service;

@Service
public class CustomSessionServiceImpl implements CustomSessionService {

	private final CustomSceneService sceneService;
	private final SessionLifecycleManager sessionLifecycle;
	private final CustomSceneFlowService flowService;
	private final RealtimeSessionCoordinator sessionCoordinator;
	private final CustomEvaluationService evaluationService;
	private final ObsoleteDialogueCleanup dialogueCleanup;

	public CustomSessionServiceImpl(
			CustomSceneService sceneService,
			SessionLifecycleManager sessionLifecycle,
			CustomSceneFlowService flowService,
			RealtimeSessionCoordinator sessionCoordinator,
			CustomEvaluationService evaluationService,
			ObsoleteDialogueCleanup dialogueCleanup) {
		this.sceneService = sceneService;
		this.sessionLifecycle = sessionLifecycle;
		this.flowService = flowService;
		this.sessionCoordinator = sessionCoordinator;
		this.evaluationService = evaluationService;
		this.dialogueCleanup = dialogueCleanup;
	}

	@Override
	public StartSceneSessionResponse startSession(StartCustomSessionCommand command) {
		String sceneId = command.sceneId();
		StartCustomSceneDialogueRequest request = command.request();
		CustomDialogueSceneContext prepared = sceneService.prepareDialogue(sceneId);
		StartSessionResponse started = sessionLifecycle.startSession(
				new StartSessionCommand(
						prepared.userId(),
						prepared.sceneId(),
						SceneType.CUSTOM_SCENE,
						"DIALOGUE",
						prepared.prompt()));
		flowService.startDialogueState(
				prepared.sceneId(),
				started.sessionId(),
				prepared.successFactorJson(),
				prepared.learningGoal());
		try {
			return sessionCoordinator.connect(
					prepared.scene(),
					prepared.title(),
					SceneFlowStage.DIALOGUE,
					true,
					started,
					SceneType.CUSTOM_SCENE,
					prepared.sceneId(),
					prepared.prompt(),
					request.offerSdp(),
					request.provider(),
					request.model(),
					request.voice(),
					request.translationEnabled());
		}
		catch (RuntimeException exception) {
			flowService.clearDialogueState(started.sessionId());
			throw exception;
		}
	}

	@Override
	public CompleteCustomSceneDialogueResponse endSession(
			EndCustomSessionCommand command) {
		String sceneId = command.sceneId();
		String sessionId = command.sessionId();
		CustomSceneDefinition definition = sceneService.getOwnedDefinition(sceneId);
		AbstractSceneSession session = sessionCoordinator.requireOwnedSession(
				definition.userId(),
				sessionId);
		requireBinding(session, sceneId);
		ScenarioDialogueStateResponse state = flowService.beginDialogueClosing(
				sceneId,
				sessionId);
		sessionLifecycle.endSession(sessionId);
		String endedAt = session.getEndedAt().toString();
		RealtimeFlowLog.info(
				"evaluation.report.start sceneId={} sessionId={}",
				sceneId,
				sessionId);
		DialogueReportResult report;
		try {
			report = evaluationService.generateReport(sceneId);
		}
		finally {
			if (!flowService.isCompleted(sceneId)) flowService.next(sceneId);
			flowService.clearDialogueState(sessionId);
			sessionCoordinator.remove(sessionId);
		}
		dialogueCleanup.retainLatestDialogue(sceneId, sessionId);
		return new CompleteCustomSceneDialogueResponse(
				sceneId,
				sessionId,
				endedAt,
				report,
				state);
	}

	@Override
	public void addMessage(String sessionId, Message message) {
		sessionLifecycle.addMessage(sessionId, message);
	}

	private void requireBinding(AbstractSceneSession session, String sceneId) {
		if (session.getSceneType() != SceneType.CUSTOM_SCENE
				|| !sceneId.equals(session.getSceneId())) {
			throw new BusinessException(
					"SESSION_ACCESS_DENIED",
					"当前会话不属于该场景");
		}
	}
}
