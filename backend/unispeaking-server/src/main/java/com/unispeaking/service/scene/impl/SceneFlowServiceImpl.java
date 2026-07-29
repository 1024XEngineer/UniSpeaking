package com.unispeaking.service.scene.impl;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.exception.SceneNotFoundException;
import com.unispeaking.repository.SceneRepository;
import com.unispeaking.service.scene.SceneFlowService;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SceneFlowServiceImpl implements SceneFlowService {

	private final SceneRepository sceneRepository;
	private SceneFlowResponse currentFlow;

	public SceneFlowServiceImpl(SceneRepository sceneRepository) {
		this.sceneRepository = sceneRepository;
	}

	@Override
	public synchronized SceneFlowResponse createFlow(String sceneId) {
		findScene(sceneId);
		SceneType sceneType = parseSceneType(sceneId);
		SceneFlowStage initialStage = sceneType == SceneType.FREE_CHAT
				? SceneFlowStage.DIALOGUE
				: SceneFlowStage.WORD_LEARNING;
		currentFlow = new SceneFlowResponse(
				sceneId,
				initialStage,
				false);
		return currentFlow;
	}

	@Override
	public synchronized SceneFlowResponse advanceStage(SceneFlowStage stage) {
		SceneFlowResponse current = requireFlow();
		requireCurrentStage(current, stage);
		SceneFlowStage nextStage = next(current.stage());
		currentFlow = new SceneFlowResponse(
				current.sceneId(),
				nextStage,
				nextStage == SceneFlowStage.COMPLETED);
		return currentFlow;
	}

	@Override
	public synchronized void completeFlow(Boolean completed) {
		if (!Boolean.TRUE.equals(completed)) {
			return;
		}
		SceneFlowResponse current = requireFlow();
		currentFlow = new SceneFlowResponse(
				current.sceneId(),
				SceneFlowStage.COMPLETED,
				true);
	}

	@Override
	public synchronized List<LearningContentItem> getByCurrentStage(SceneFlowStage stage) {
		SceneFlowResponse flow = requireFlow();
		requireCurrentStage(flow, stage);
		SceneGenerationResponse scene = findScene(flow.sceneId());
		return switch (flow.stage()) {
			case WORD_LEARNING -> scene.wordList();
			case PHRASE_LEARNING -> scene.phraseList();
			case SENTENCE_LEARNING -> scene.sentenceList();
			case DIALOGUE, COMPLETED -> List.of();
		};
	}

	private SceneType parseSceneType(String sceneId) {
		return SceneType.fromSceneId(sceneId)
				.orElseThrow(() -> new BusinessException(
						"INVALID_SCENE_ID",
						"unsupported scene id prefix: " + sceneId));
	}

	private SceneFlowResponse requireFlow() {
		if (currentFlow == null) {
			throw new BusinessException("SCENE_FLOW_NOT_FOUND", "scene flow has not been created");
		}
		return currentFlow;
	}

	private SceneGenerationResponse findScene(String sceneId) {
		return sceneRepository.findGeneratedById(sceneId)
				.orElseThrow(() -> new SceneNotFoundException(sceneId));
	}

	private void requireCurrentStage(
			SceneFlowResponse flow,
			SceneFlowStage requestedStage) {
		if (requestedStage != null && requestedStage != flow.stage()) {
			throw new BusinessException(
					"SCENE_STAGE_MISMATCH",
					"requested stage " + requestedStage
							+ " does not match current stage " + flow.stage());
		}
	}

	private SceneFlowStage next(SceneFlowStage current) {
		return switch (current) {
			case WORD_LEARNING -> SceneFlowStage.PHRASE_LEARNING;
			case PHRASE_LEARNING -> SceneFlowStage.SENTENCE_LEARNING;
			case SENTENCE_LEARNING -> SceneFlowStage.DIALOGUE;
			case DIALOGUE, COMPLETED -> SceneFlowStage.COMPLETED;
		};
	}
}
