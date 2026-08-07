package com.unispeaking.service.scene.impl;

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
import com.unispeaking.service.scene.CustomSceneFlowService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class CustomSceneFlowServiceImpl implements CustomSceneFlowService {

	private final SceneRepository sceneRepository;
	private final ScenarioDialogueStateMachine dialogueStateMachine;
	private final RealtimeSessionCoordinator sessionCoordinator;
	private final Map<String, CustomStage> stages = new ConcurrentHashMap<>();

	public CustomSceneFlowServiceImpl(
			SceneRepository sceneRepository,
			ScenarioDialogueStateMachine dialogueStateMachine,
			RealtimeSessionCoordinator sessionCoordinator) {
		this.sceneRepository = sceneRepository;
		this.dialogueStateMachine = dialogueStateMachine;
		this.sessionCoordinator = sessionCoordinator;
	}

	@Override
	public CustomStage start(String sceneId) {
		requireScene(sceneId);
		stages.put(sceneId, CustomStage.WORD);
		return CustomStage.WORD;
	}

	@Override
	public CustomStage current(String sceneId) {
		CustomStage stage = stages.get(sceneId);
		if (stage == null) {
			throw new BusinessException(
					"SCENE_FLOW_NOT_FOUND",
					"scene flow has not been started");
		}
		return stage;
	}

	@Override
	public CustomStage next(String sceneId) {
		CustomStage next = switch (current(sceneId)) {
			case WORD -> CustomStage.PHRASE;
			case PHRASE -> CustomStage.SENTENCE;
			case SENTENCE -> CustomStage.DIALOGUE;
			case DIALOGUE, COMPLETED -> CustomStage.COMPLETED;
		};
		stages.put(sceneId, next);
		return next;
	}

	@Override
	public boolean isCompleted(String sceneId) {
		return current(sceneId) == CustomStage.COMPLETED;
	}

	@Override
	public void clear(String sceneId) {
		stages.remove(sceneId);
	}

	@Override
	public SceneFlowResponse response(String sceneId) {
		CustomStage stage = current(sceneId);
		return new SceneFlowResponse(
				sceneId,
				toLegacyStage(stage),
				stage == CustomStage.COMPLETED);
	}

	@Override
	public List<LearningContentItem> content(String sceneId) {
		CustomStage stage = current(sceneId);
		SceneGenerationResponse scene = requireScene(sceneId);
		return switch (stage) {
			case WORD -> scene.wordList();
			case PHRASE -> scene.phraseList();
			case SENTENCE -> scene.sentenceList();
			case DIALOGUE, COMPLETED -> List.of();
		};
	}

	@Override
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

	@Override
	public ScenarioDialogueStateResponse advanceDialogueState(
			String sceneId,
			String sessionId,
			int turnNo,
			String transcript) {
		requireOwnedBinding(sceneId, sessionId);
		return dialogueStateMachine.advance(sessionId, turnNo, transcript);
	}

	@Override
	public ScenarioDialogueStateResponse getDialogueState(
			String sceneId,
			String sessionId) {
		requireOwnedBinding(sceneId, sessionId);
		return dialogueStateMachine.getState(sessionId);
	}

	@Override
	public ScenarioDialogueStateResponse beginDialogueClosing(
			String sceneId,
			String sessionId) {
		requireOwnedBinding(sceneId, sessionId);
		return dialogueStateMachine.findState(sessionId)
				.map(ignored -> dialogueStateMachine.beginClosing(sessionId))
				.orElse(null);
	}

	@Override
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
}
