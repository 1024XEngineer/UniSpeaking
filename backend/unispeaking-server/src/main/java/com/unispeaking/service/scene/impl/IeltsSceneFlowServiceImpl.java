package com.unispeaking.service.scene.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.statemachine.IeltsPart2StateMachine;
import com.unispeaking.component.statemachine.IeltsQuestionStateMachine;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.session.IeltsDialogueStateResponse;
import com.unispeaking.domain.dto.session.IeltsPart2StateResponse;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsPart2Event;
import com.unispeaking.domain.vo.scene.IeltsStage;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class IeltsSceneFlowServiceImpl implements IeltsSceneFlowService {

	private final IeltsPracticeRepository practiceRepository;
	private final IeltsQuestionStateMachine questionStateMachine;
	private final IeltsPart2StateMachine part2StateMachine;
	private final RealtimeSessionCoordinator sessionCoordinator;
	private final Map<String, IeltsStage> stages = new ConcurrentHashMap<>();

	public IeltsSceneFlowServiceImpl(
			IeltsPracticeRepository practiceRepository,
			IeltsQuestionStateMachine questionStateMachine,
			IeltsPart2StateMachine part2StateMachine,
			RealtimeSessionCoordinator sessionCoordinator) {
		this.practiceRepository = practiceRepository;
		this.questionStateMachine = questionStateMachine;
		this.part2StateMachine = part2StateMachine;
		this.sessionCoordinator = sessionCoordinator;
	}

	@Override
	public IeltsStage start(String sceneId) {
		IeltsPracticeRecord scene = requireScene(sceneId);
		IeltsStage stage = scene.mode() == IeltsMode.PART_PRACTICE
				? convertPart(scene.selectedPart())
				: IeltsStage.PART1;
		stages.put(sceneId, stage);
		return stage;
	}

	@Override
	public IeltsStage current(String sceneId) {
		IeltsStage stage = stages.get(sceneId);
		if (stage == null) {
			throw new BusinessException(
					"SCENE_FLOW_NOT_FOUND",
					"IELTS scene flow has not been started");
		}
		return stage;
	}

	@Override
	public IeltsStage next(String sceneId) {
		IeltsPracticeRecord scene = requireScene(sceneId);
		IeltsStage next;
		if (scene.mode() == IeltsMode.PART_PRACTICE) {
			next = IeltsStage.COMPLETED;
		}
		else {
			next = switch (current(sceneId)) {
				case PART1 -> IeltsStage.PART2;
				case PART2 -> IeltsStage.PART3;
				case PART3, COMPLETED -> IeltsStage.COMPLETED;
			};
		}
		stages.put(sceneId, next);
		return next;
	}

	@Override
	public boolean isCompleted(String sceneId) {
		return current(sceneId) == IeltsStage.COMPLETED;
	}

	@Override
	public SceneFlowResponse response(String sceneId) {
		IeltsStage stage = current(sceneId);
		return new SceneFlowResponse(
				sceneId,
				toLegacyStage(stage),
				stage == IeltsStage.COMPLETED);
	}

	@Override
	public void clear(String sceneId) {
		stages.remove(sceneId);
	}

	@Override
	public void startSessionState(
			String sceneId,
			String sessionId,
			IeltsPart part) {
		IeltsPracticeRecord practice = requireOwnedBinding(sceneId, sessionId);
		if (part == IeltsPart.PART_2) {
			part2StateMachine.start(sceneId, sessionId);
		}
		else {
			questionStateMachine.start(
					sceneId,
					sessionId,
					part,
					practice.content().questionsFor(part));
		}
	}

	@Override
	public IeltsDialogueStateResponse advanceDialogueState(
			String sceneId,
			String sessionId,
			int turnNo,
			boolean timedOut) {
		requireOwnedBinding(sceneId, sessionId);
		return questionStateMachine.advance(
				sceneId,
				sessionId,
				turnNo,
				timedOut);
	}

	@Override
	public IeltsDialogueStateResponse getDialogueState(
			String sceneId,
			String sessionId) {
		requireOwnedBinding(sceneId, sessionId);
		return questionStateMachine.get(sceneId, sessionId);
	}

	@Override
	public IeltsPart2StateResponse advancePart2State(
			String sceneId,
			String sessionId,
			IeltsPart2Event event) {
		requireOwnedBinding(sceneId, sessionId);
		return part2StateMachine.advance(sceneId, sessionId, event);
	}

	@Override
	public IeltsPart2StateResponse getPart2State(
			String sceneId,
			String sessionId) {
		requireOwnedBinding(sceneId, sessionId);
		return part2StateMachine.get(sceneId, sessionId);
	}

	@Override
	public void clearSessionState(String sessionId) {
		questionStateMachine.remove(sessionId);
		part2StateMachine.remove(sessionId);
	}

	private IeltsPracticeRecord requireScene(String sceneId) {
		return practiceRepository.findPractice(sceneId)
				.orElseThrow(() -> new BusinessException(
						"IELTS_PRACTICE_NOT_FOUND",
					"IELTS 练习不存在"));
	}

	private IeltsPracticeRecord requireOwnedBinding(
			String sceneId,
			String sessionId) {
		IeltsPracticeRecord practice = requireScene(sceneId);
		AbstractSceneSession session = sessionCoordinator.requireOwnedSession(
				practice.userId().toString(),
				sessionId);
		if (session.getSceneType() != SceneType.IELTS_SCENE
				|| !sceneId.equals(session.getSceneId())) {
			throw new BusinessException(
					"IELTS_SESSION_MISMATCH",
					"IELTS 会话与练习不匹配");
		}
		return practice;
	}

	private IeltsStage convertPart(IeltsPart part) {
		if (part == null) {
			throw new BusinessException(
					"IELTS_PART_REQUIRED",
					"专项训练必须指定 Part");
		}
		return switch (part) {
			case PART_1 -> IeltsStage.PART1;
			case PART_2 -> IeltsStage.PART2;
			case PART_3 -> IeltsStage.PART3;
		};
	}

	private SceneFlowStage toLegacyStage(IeltsStage stage) {
		return switch (stage) {
			case PART1 -> SceneFlowStage.IELTS_PART_1;
			case PART2 -> SceneFlowStage.IELTS_PART_2;
			case PART3 -> SceneFlowStage.IELTS_PART_3;
			case COMPLETED -> SceneFlowStage.COMPLETED;
		};
	}
}
