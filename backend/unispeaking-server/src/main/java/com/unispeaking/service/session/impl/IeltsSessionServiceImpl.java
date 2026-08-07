package com.unispeaking.service.session.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.component.statemachine.IeltsPart2StateMachine;
import com.unispeaking.component.statemachine.IeltsQuestionStateMachine;
import com.unispeaking.domain.dto.scene.IeltsDialogueSceneContext;
import com.unispeaking.domain.dto.session.IeltsDialogueStateResponse;
import com.unispeaking.domain.dto.session.IeltsPart2StateResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.SessionDetail;
import com.unispeaking.domain.dto.session.StartIeltsDialogueRequest;
import com.unispeaking.domain.dto.session.StartIeltsSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsPart2Event;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.scene.impl.IeltsSceneServiceImpl;
import com.unispeaking.service.session.SessionService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IeltsSessionServiceImpl implements SessionService {

	private final IeltsSceneServiceImpl sceneService;
	private final SessionLifecycleManager sessionLifecycle;
	private final RealtimeSessionCoordinator sessionCoordinator;
	private final IeltsQuestionStateMachine questionStateMachine;
	private final IeltsPart2StateMachine part2StateMachine;

	public IeltsSessionServiceImpl(
			IeltsSceneServiceImpl sceneService,
			SessionLifecycleManager sessionLifecycle,
			RealtimeSessionCoordinator sessionCoordinator,
			IeltsQuestionStateMachine questionStateMachine,
			IeltsPart2StateMachine part2StateMachine) {
		this.sceneService = sceneService;
		this.sessionLifecycle = sessionLifecycle;
		this.sessionCoordinator = sessionCoordinator;
		this.questionStateMachine = questionStateMachine;
		this.part2StateMachine = part2StateMachine;
	}

	public StartIeltsSessionResponse startSession(
			String ieltsId,
			StartIeltsDialogueRequest request) {
		IeltsDialogueSceneContext prepared = sceneService.prepareDialogue(
				ieltsId,
				request.voiceId());
		StartSessionResponse started = startSession(
				new StartSessionCommand(
						prepared.userId(),
						prepared.ieltsId(),
						SceneType.IELTS_SCENE,
						prepared.activePart().name().replace("PART_", "PART"),
						prepared.prompt()));
		if (prepared.activePart() == IeltsPart.PART_2) {
			part2StateMachine.start(ieltsId, started.sessionId());
		}
		else {
			questionStateMachine.start(
					ieltsId,
					started.sessionId(),
					prepared.activePart(),
					prepared.content().questionsFor(prepared.activePart()));
		}
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
			questionStateMachine.remove(started.sessionId());
			part2StateMachine.remove(started.sessionId());
			throw exception;
		}
	}

	@Override
	public StartSessionResponse startSession(StartSessionCommand command) {
		requireSceneType(command, SceneType.IELTS_SCENE);
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
		endSession(sessionLifecycle.requireOwnerId(sessionId), sessionId, null);
	}

	public void endSession(String userId, String sessionId, String stopTime) {
		if (sessionLifecycle.requireSceneType(userId, sessionId)
				!= SceneType.IELTS_SCENE) {
			throw new BusinessException(
					"IELTS_SESSION_MISMATCH",
					"session does not belong to IELTS");
		}
		String ieltsId = sessionCoordinator
				.requireOwnedSession(userId, sessionId)
				.getSceneId();
		sessionLifecycle.endSession(userId, sessionId, stopTime);
		sceneService.completeDialogue(ieltsId, userId);
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

	public IeltsDialogueStateResponse advanceState(
			String ieltsId,
			String sessionId,
			int turnNo,
			boolean timedOut) {
		requireOwnedSession(ieltsId, sessionId);
		return questionStateMachine.advance(
				ieltsId,
				sessionId,
				turnNo,
				timedOut);
	}

	public IeltsDialogueStateResponse getState(
			String ieltsId,
			String sessionId) {
		requireOwnedSession(ieltsId, sessionId);
		return questionStateMachine.get(ieltsId, sessionId);
	}

	public IeltsPart2StateResponse advancePart2State(
			String ieltsId,
			String sessionId,
			IeltsPart2Event event) {
		requireOwnedSession(ieltsId, sessionId);
		return part2StateMachine.advance(ieltsId, sessionId, event);
	}

	public IeltsPart2StateResponse getPart2State(
			String ieltsId,
			String sessionId) {
		requireOwnedSession(ieltsId, sessionId);
		return part2StateMachine.get(ieltsId, sessionId);
	}

	private void requireOwnedSession(String ieltsId, String sessionId) {
		IeltsPracticeRecord practice = sceneService.requireOwnedPractice(ieltsId);
		String userId = practice.userId().toString();
		var session = sessionCoordinator.requireOwnedSession(userId, sessionId);
		if (session.getSceneType() != SceneType.IELTS_SCENE
				|| !ieltsId.equals(session.getSceneId())) {
			throw new BusinessException(
					"IELTS_SESSION_MISMATCH",
					"IELTS 会话与练习不匹配");
		}
	}
}
