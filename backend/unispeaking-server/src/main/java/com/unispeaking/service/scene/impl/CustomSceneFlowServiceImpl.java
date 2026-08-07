package com.unispeaking.service.scene.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.SceneNotFoundException;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.vo.scene.CustomStage;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.service.scene.SceneFlowService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class CustomSceneFlowServiceImpl implements SceneFlowService<CustomStage> {

	private final SceneRepository sceneRepository;
	private final Map<String, CustomStage> stages = new ConcurrentHashMap<>();

	public CustomSceneFlowServiceImpl(SceneRepository sceneRepository) {
		this.sceneRepository = sceneRepository;
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

	public void clear(String sceneId) {
		stages.remove(sceneId);
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

	private SceneGenerationResponse requireScene(String sceneId) {
		return sceneRepository.findGeneratedById(sceneId)
				.orElseThrow(() -> new SceneNotFoundException(sceneId));
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
