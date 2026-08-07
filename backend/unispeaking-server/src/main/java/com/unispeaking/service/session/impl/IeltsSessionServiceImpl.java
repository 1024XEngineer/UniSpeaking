package com.unispeaking.service.session.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.dto.scene.IeltsDialogueSceneContext;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartIeltsDialogueRequest;
import com.unispeaking.domain.dto.session.StartIeltsSessionResponse;
import com.unispeaking.domain.dto.session.StartIeltsSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import com.unispeaking.service.scene.IeltsSceneService;
import com.unispeaking.service.session.IeltsSessionService;
import org.springframework.stereotype.Service;

@Service
public class IeltsSessionServiceImpl implements IeltsSessionService {

	private final IeltsSceneService sceneService;
	private final IeltsSceneFlowService flowService;
	private final SessionLifecycleManager sessionLifecycle;
	private final RealtimeSessionCoordinator sessionCoordinator;

	public IeltsSessionServiceImpl(
			IeltsSceneService sceneService,
			IeltsSceneFlowService flowService,
			SessionLifecycleManager sessionLifecycle,
			RealtimeSessionCoordinator sessionCoordinator) {
		this.sceneService = sceneService;
		this.flowService = flowService;
		this.sessionLifecycle = sessionLifecycle;
		this.sessionCoordinator = sessionCoordinator;
	}

	@Override
	public StartIeltsSessionResponse startSession(StartIeltsSessionCommand command) {
		String ieltsId = command.ieltsId();
		StartIeltsDialogueRequest request = command.request();
		IeltsDialogueSceneContext prepared = sceneService.prepareDialogue(
				ieltsId,
				request.voiceId());
		StartSessionResponse started = sessionLifecycle.startSession(
				new StartSessionCommand(
						prepared.userId(),
						prepared.ieltsId(),
						SceneType.IELTS_SCENE,
						prepared.activePart().name().replace("PART_", "PART"),
						prepared.prompt()));
		flowService.startSessionState(
				ieltsId,
				started.sessionId(),
				prepared.activePart());
		try {
			return sessionCoordinator.connectIelts(
					prepared.content(),
					prepared.activePart(),
					prepared.topicTitle(),
					prepared.flow().stage(),
					true,
					started,
					ieltsId,
					prepared.prompt(),
					request.offerSdp(),
					request.provider(),
					request.model(),
					prepared.voiceId(),
					request.translationEnabled());
		}
		catch (RuntimeException exception) {
			flowService.clearSessionState(started.sessionId());
			throw exception;
		}
	}

	@Override
	public void addMessage(String sessionId, Message message) {
		sessionLifecycle.addMessage(sessionId, message);
	}

	@Override
	public Void endSession(String sessionId) {
		String userId = sessionLifecycle.requireOwnerId(sessionId);
		if (sessionLifecycle.requireSceneType(userId, sessionId)
				!= SceneType.IELTS_SCENE) {
			throw new BusinessException(
					"IELTS_SESSION_MISMATCH",
					"session does not belong to IELTS");
		}
		String ieltsId = sessionCoordinator
				.requireOwnedSession(userId, sessionId)
				.getSceneId();
		try {
			sessionLifecycle.endSession(sessionId);
			sceneService.completeDialogue(ieltsId, userId);
		}
		finally {
			flowService.clearSessionState(sessionId);
		}
		return null;
	}

}
