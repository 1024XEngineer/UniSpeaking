package com.unispeaking.service.scene;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.SceneNotFoundException;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.statemachine.ScenarioDialogueStateMachine;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.scene.CustomStage;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class CustomSceneFlowService extends SceneFlowService<CustomStage> {

	private final SceneRepository sceneRepository;
	private final ScenarioDialogueStateMachine dialogueStateMachine;
	private final RealtimeSessionCoordinator sessionCoordinator;
	private final Map<String, CustomStage> furthestStages = new ConcurrentHashMap<>();

	public CustomSceneFlowService(
			SceneRepository sceneRepository,
			ScenarioDialogueStateMachine dialogueStateMachine,
			RealtimeSessionCoordinator sessionCoordinator) {
		super(
				sceneId -> initialStage(sceneRepository, sceneId),
				(sceneId, stage) -> nextStage(stage),
				stage -> stage == CustomStage.COMPLETED,
				"scene flow has not been started");
		this.sceneRepository = sceneRepository;
		this.dialogueStateMachine = dialogueStateMachine;
		this.sessionCoordinator = sessionCoordinator;
	}

	@Override
	public CustomStage start(String sceneId) {
		CustomStage stage = super.start(sceneId);
		furthestStages.put(sceneId, stage);
		return stage;
	}

	@Override
	public CustomStage current(String sceneId) {
		return super.current(sceneId);
	}

	@Override
	public CustomStage next(String sceneId) {
		return rememberFurthest(sceneId, super.next(sceneId));
	}

	/** 根据客户端声明的当前学习阶段推进，修正客户端回退后的服务端缓存。 */
	public CustomStage next(String sceneId, SceneFlowStage expectedCurrentStage) {
		CustomStage requested = fromLegacyStage(expectedCurrentStage);
		CustomStage current = current(sceneId);
		CustomStage furthest = furthestStages.getOrDefault(sceneId, current);
		if (rank(requested) > rank(furthest)) {
			throw new BusinessException(
					"SCENE_FLOW_STAGE_OUT_OF_ORDER",
					"不能跳过尚未完成的场景学习阶段");
		}
		return rememberFurthest(sceneId, super.nextFrom(sceneId, requested));
	}

	@Override
	public boolean isCompleted(String sceneId) {
		return super.isCompleted(sceneId);
	}

	@Override
	public void clear(String sceneId) {
		super.clear(sceneId);
		furthestStages.remove(sceneId);
	}

	private static CustomStage initialStage(
			SceneRepository sceneRepository,
			String sceneId) {
		sceneRepository.findGeneratedById(sceneId)
				.orElseThrow(() -> new SceneNotFoundException(sceneId));
		return CustomStage.WORD;
	}

	private static CustomStage nextStage(CustomStage stage) {
		return switch (stage) {
			case WORD -> CustomStage.PHRASE;
			case PHRASE -> CustomStage.SENTENCE;
			case SENTENCE -> CustomStage.DIALOGUE;
			case DIALOGUE, COMPLETED -> CustomStage.COMPLETED;
		};
	}
	public SceneFlowResponse response(String sceneId) {
		CustomStage stage = current(sceneId);
		return new SceneFlowResponse(
				sceneId,
				toLegacyStage(stage),
				stage == CustomStage.COMPLETED);
	}
	public List<LearningContentItem> content(String sceneId) {
		return content(sceneId, null);
	}

	/** 返回客户端请求的阶段内容，不要求服务端当前阶段已经同步。 */
	public List<LearningContentItem> content(
			String sceneId,
			SceneFlowStage requestedStage) {
		CustomStage current = current(sceneId);
		CustomStage stage = requestedStage == null
				? current
				: fromLegacyStage(requestedStage);
		CustomStage furthest = furthestStages.getOrDefault(sceneId, current);
		if (rank(stage) > rank(furthest)) {
			throw new BusinessException(
					"SCENE_FLOW_STAGE_OUT_OF_ORDER",
					"不能访问尚未解锁的场景学习阶段");
		}
		SceneGenerationResponse scene = requireScene(sceneId);
		return switch (stage) {
			case WORD -> scene.wordList();
			case PHRASE -> scene.phraseList();
			case SENTENCE -> scene.sentenceList();
			case DIALOGUE, COMPLETED -> List.of();
		};
	}
	public ScenarioDialogueStateResponse startDialogueState(
			String sceneId,
			String sessionId,
			String successFactorJson,
			String learningGoal) {
		requireOwnedBinding(sceneId, sessionId);
		return dialogueStateMachine.start(
				sessionId,
				sceneId,
				successFactorJson,
				learningGoal);
	}
	public ScenarioDialogueStateResponse advanceDialogueState(
			String sceneId,
			String sessionId,
			int turnNo,
			String transcript) {
		requireOwnedBinding(sceneId, sessionId);
		return dialogueStateMachine.advance(sessionId, turnNo, transcript);
	}
	public ScenarioDialogueStateResponse getDialogueState(
			String sceneId,
			String sessionId) {
		requireOwnedBinding(sceneId, sessionId);
		return dialogueStateMachine.getState(sessionId);
	}
	public ScenarioDialogueStateResponse beginDialogueClosing(
			String sceneId,
			String sessionId) {
		requireOwnedBinding(sceneId, sessionId);
		return dialogueStateMachine.findState(sessionId)
				.map(ignored -> dialogueStateMachine.beginClosing(sessionId))
				.orElse(null);
	}
	public void clearDialogueState(String sessionId) {
		dialogueStateMachine.remove(sessionId);
	}

	private SceneGenerationResponse requireScene(String sceneId) {
		return sceneRepository.findGeneratedById(sceneId)
				.orElseThrow(() -> new SceneNotFoundException(sceneId));
	}

	private void requireOwnedBinding(String sceneId, String sessionId) {
		CustomSceneDefinition definition = sceneRepository
				.findCustomDefinitionById(sceneId)
				.orElseThrow(() -> new SceneNotFoundException(sceneId));
		AbstractSceneSession session = sessionCoordinator.requireOwnedSession(
				definition.userId(),
				sessionId);
		if (session.getSceneType() != SceneType.CUSTOM_SCENE
				|| !sceneId.equals(session.getSceneId())) {
			throw new BusinessException(
					"SESSION_ACCESS_DENIED",
					"当前会话不属于该场景");
		}
	}

	private SceneFlowStage toLegacyStage(CustomStage stage) {
		return switch (stage) {
			case WORD -> SceneFlowStage.WORD_LEARNING;
			case PHRASE -> SceneFlowStage.PHRASE_LEARNING;
			case SENTENCE -> SceneFlowStage.SENTENCE_LEARNING;
			case DIALOGUE -> SceneFlowStage.DIALOGUE;
			case COMPLETED -> SceneFlowStage.COMPLETED;
		};
	}

	private static CustomStage fromLegacyStage(SceneFlowStage stage) {
		return switch (stage) {
			case WORD_LEARNING -> CustomStage.WORD;
			case PHRASE_LEARNING -> CustomStage.PHRASE;
			case SENTENCE_LEARNING -> CustomStage.SENTENCE;
			case DIALOGUE -> CustomStage.DIALOGUE;
			case COMPLETED -> CustomStage.COMPLETED;
			case IELTS_PART_1, IELTS_PART_2, IELTS_PART_3 -> throw new BusinessException(
					"SCENE_FLOW_STAGE_INVALID",
					"当前场景不支持 IELTS 阶段");
		};
	}

	private static int rank(CustomStage stage) {
		return switch (stage) {
			case WORD -> 0;
			case PHRASE -> 1;
			case SENTENCE -> 2;
			case DIALOGUE -> 3;
			case COMPLETED -> 4;
		};
	}

	private CustomStage rememberFurthest(String sceneId, CustomStage stage) {
		furthestStages.merge(
				sceneId,
				stage,
				(current, candidate) -> rank(candidate) > rank(current)
						? candidate
						: current);
		return stage;
	}
}
