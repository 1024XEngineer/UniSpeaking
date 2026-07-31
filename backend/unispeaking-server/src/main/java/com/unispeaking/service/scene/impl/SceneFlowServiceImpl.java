package com.unispeaking.service.scene.impl;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.SceneNotFoundException;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.service.scene.SceneFlowService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SceneFlowServiceImpl implements SceneFlowService {

	private final SceneRepository sceneRepository;
	private final Map<String, SceneFlowResponse> flows = new ConcurrentHashMap<>();

	public SceneFlowServiceImpl(SceneRepository sceneRepository) {
		this.sceneRepository = sceneRepository;
	}

	@Override
	public SceneFlowResponse createFlow(String sceneId) {
		SceneType sceneType = parseSceneType(sceneId);
		if (sceneType != SceneType.FREE_CHAT) {
			findScene(sceneId);
		}
		SceneFlowStage initialStage = sceneType == SceneType.FREE_CHAT
				? SceneFlowStage.DIALOGUE
				: SceneFlowStage.WORD_LEARNING;
		SceneFlowResponse flow = new SceneFlowResponse(
				sceneId,
				initialStage,
				false);
		flows.put(sceneId, flow);
		return flow;
	}

	@Override
	public SceneFlowResponse advanceStage(String sceneId, SceneFlowStage stage) {
		SceneFlowResponse current = requireFlow(sceneId);
		requireCurrentStage(current, stage);
		SceneFlowStage nextStage = next(current.stage());
		SceneFlowResponse next = new SceneFlowResponse(
				current.sceneId(),
				nextStage,
				nextStage == SceneFlowStage.COMPLETED);
		flows.put(sceneId, next);
		return next;
	}

	@Override
	public void completeFlow(String sceneId, Boolean completed) {
		if (!Boolean.TRUE.equals(completed)) {
			return;
		}
		flows.remove(sceneId);
	}

	@Override
	public List<LearningContentItem> getByCurrentStage(
			String sceneId,
			SceneFlowStage stage) {
		SceneFlowResponse flow = requireFlow(sceneId);
		requireCurrentStage(flow, stage);
		if (flow.stage() == SceneFlowStage.DIALOGUE
				|| flow.stage() == SceneFlowStage.COMPLETED) {
			return List.of();
		}
		SceneGenerationResponse scene = findScene(flow.sceneId());
		return switch (flow.stage()) {
			case WORD_LEARNING -> scene.wordList();
			case PHRASE_LEARNING -> scene.phraseList();
			case SENTENCE_LEARNING -> scene.sentenceList();
			case DIALOGUE, COMPLETED -> throw new IllegalStateException(
					"dialogue stages do not expose learning content");
		};
	}

	private SceneType parseSceneType(String sceneId) {
		return SceneType.fromSceneId(sceneId)
				.orElseThrow(() -> new BusinessException(
						"INVALID_SCENE_ID",
						"unsupported scene id prefix: " + sceneId));
	}

	private SceneFlowResponse requireFlow(String sceneId) {
		SceneFlowResponse flow = flows.get(sceneId);
		if (flow == null) {
			throw new BusinessException("SCENE_FLOW_NOT_FOUND", "scene flow has not been created");
		}
		return flow;
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
