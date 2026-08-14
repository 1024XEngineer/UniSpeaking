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
import org.springframework.stereotype.Service;

@Service
public class CustomSceneFlowService extends SceneFlowService<CustomStage> {

	private final SceneRepository sceneRepository;
	private final ScenarioDialogueStateMachine dialogueStateMachine;
	private final RealtimeSessionCoordinator sessionCoordinator;

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
		return super.start(sceneId);
	}

	@Override
	public CustomStage current(String sceneId) {
		return super.current(sceneId);
	}

	@Override
	public CustomStage next(String sceneId) {
		return super.next(sceneId);
	}

	@Override
	public boolean isCompleted(String sceneId) {
		return super.isCompleted(sceneId);
	}

	@Override
	public void clear(String sceneId) {
		super.clear(sceneId);
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
		CustomStage stage = current(sceneId);
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
}
