package com.unispeaking.service.session.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.component.statemachine.ScenarioDialogueStateMachine;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.scene.CustomDialogueSceneContext;
import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.SessionDetail;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.component.session.ObsoleteDialogueCleanup;
import com.unispeaking.service.evaluation.impl.CustomEvaluationServiceImpl;
import com.unispeaking.service.scene.impl.CustomSceneFlowServiceImpl;
import com.unispeaking.service.scene.impl.CustomSceneServiceImpl;
import com.unispeaking.service.session.SessionService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomSessionServiceImpl implements SessionService {

	private final CustomSceneServiceImpl sceneService;
	private final SessionLifecycleManager sessionLifecycle;
	private final CustomSceneFlowServiceImpl flowService;
	private final RealtimeSessionCoordinator sessionCoordinator;
	private final CustomEvaluationServiceImpl evaluationService;
	private final ScenarioDialogueStateMachine stateMachine;
	private final SessionMessageRepository messageRepository;
	private final ObsoleteDialogueCleanup dialogueCleanup;

	public CustomSessionServiceImpl(
			CustomSceneServiceImpl sceneService,
			SessionLifecycleManager sessionLifecycle,
			CustomSceneFlowServiceImpl flowService,
			RealtimeSessionCoordinator sessionCoordinator,
			CustomEvaluationServiceImpl evaluationService,
			ScenarioDialogueStateMachine stateMachine,
			SessionMessageRepository messageRepository,
			ObsoleteDialogueCleanup dialogueCleanup) {
		this.sceneService = sceneService;
		this.sessionLifecycle = sessionLifecycle;
		this.flowService = flowService;
		this.sessionCoordinator = sessionCoordinator;
		this.evaluationService = evaluationService;
		this.stateMachine = stateMachine;
		this.messageRepository = messageRepository;
		this.dialogueCleanup = dialogueCleanup;
	}

	public StartSceneSessionResponse startSession(
			String sceneId,
			StartCustomSceneDialogueRequest request) {
		CustomDialogueSceneContext prepared = sceneService.prepareDialogue(sceneId);
		StartSessionResponse started = startSession(
				new StartSessionCommand(
						prepared.userId(),
						prepared.sceneId(),
						SceneType.CUSTOM_SCENE,
						"DIALOGUE",
						prepared.prompt()));
		stateMachine.start(
				started.sessionId(),
				prepared.sceneId(),
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
			stateMachine.remove(started.sessionId());
			throw exception;
		}
	}

	public CompleteCustomSceneDialogueResponse completeSession(
			String sceneId,
			String sessionId,
			String stopTime) {
		CustomSceneDefinition definition = sceneService.getOwnedDefinition(sceneId);
		AbstractSceneSession session = sessionCoordinator.requireOwnedSession(
				definition.userId(),
				sessionId);
		requireBinding(session, sceneId);
		ScenarioDialogueStateResponse state = stateMachine.findState(sessionId)
				.map(ignored -> stateMachine.beginClosing(sessionId))
				.orElse(null);
		endSession(sessionId);
		String endedAt = session.getEndedAt().toString();
		RealtimeFlowLog.info(
				"evaluation.report.start sceneId={} sessionId={}",
				sceneId,
				sessionId);
		DialogueReportResult report;
		try {
			report = evaluationService.generateDialogueReport(
					sessionId,
					messageRepository.findMessages(sessionId));
		}
		finally {
			if (!flowService.isCompleted(sceneId)) flowService.next(sceneId);
			stateMachine.remove(sessionId);
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
	public StartSessionResponse startSession(StartSessionCommand command) {
		requireSceneType(command, SceneType.CUSTOM_SCENE);
		return sessionLifecycle.startSession(command);
	}

	@Override
	public void addMessage(String sessionId, Message message) {
		sessionLifecycle.addMessage(sessionId, message);
	}

	public void addMessage(String userId, String sessionId, Message message) {
		sessionLifecycle.addMessage(userId, sessionId, message);
	}

	@Override
	public void endSession(String sessionId) {
		sessionLifecycle.endSession(sessionId);
	}

	public void endSession(String userId, String sessionId, String stopTime) {
		sessionLifecycle.endSession(userId, sessionId, stopTime);
	}

	@Override
	public SessionDetail getSession(String sessionId) {
		return sessionLifecycle.getSession(sessionId);
	}

	@Override
	public List<SessionDetail> getBySceneId(String sceneId) {
		return sessionLifecycle.getBySceneId(sceneId);
	}

	private void requireSceneType(StartSessionCommand command, SceneType expected) {
		if (command == null || command.sceneType() != expected) {
			throw new BusinessException(
					"SESSION_SCENE_TYPE_MISMATCH",
					"session command does not belong to " + expected);
		}
	}

	public ScenarioDialogueStateResponse advanceState(
			String sceneId,
			String sessionId,
			int turnNo,
			String transcript) {
		requireOwnedBinding(sceneId, sessionId);
		return stateMachine.advance(sessionId, turnNo, transcript);
	}

	public ScenarioDialogueStateResponse getState(
			String sceneId,
			String sessionId) {
		requireOwnedBinding(sceneId, sessionId);
		return stateMachine.getState(sessionId);
	}

	private void requireOwnedBinding(String sceneId, String sessionId) {
		CustomSceneDefinition definition = sceneService.getOwnedDefinition(sceneId);
		requireBinding(
				sessionCoordinator.requireOwnedSession(
						definition.userId(),
						sessionId),
				sceneId);
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
