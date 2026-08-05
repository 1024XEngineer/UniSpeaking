package com.unispeaking.service.scene.impl;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.SceneNotFoundException;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.service.scene.SceneFlowService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SceneFlowServiceImpl implements SceneFlowService {

	private final SceneRepository sceneRepository;
	private final IeltsPracticeRepository ieltsPracticeRepository;
	private final Map<String, SceneFlowResponse> flows = new ConcurrentHashMap<>();

	public SceneFlowServiceImpl(
			SceneRepository sceneRepository,
			IeltsPracticeRepository ieltsPracticeRepository) {
		this.sceneRepository = sceneRepository;
		this.ieltsPracticeRepository = ieltsPracticeRepository;
	}

	@Override
	public SceneFlowResponse createFlow(String sceneId) {
		SceneType sceneType = parseSceneType(sceneId);
		if (sceneType != SceneType.FREE_CHAT
				&& sceneType != SceneType.IELTS_SCENE
				&& sceneType != SceneType.INTERVIEW_SCENE) {
			findScene(sceneId);
		}
		SceneFlowStage initialStage = initialStage(sceneId, sceneType);
		SceneFlowResponse flow = new SceneFlowResponse(
				sceneId,
				initialStage,
				false);
		flows.put(sceneId, flow);
		return flow;
	}

	@Override
	public SceneFlowResponse getFlow(String sceneId) {
		return requireFlow(sceneId);
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
		if (completed == null) {
			throw new BusinessException(
					"SCENE_FLOW_COMPLETION_STATUS_REQUIRED",
					"scene flow completion status is required");
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
				|| flow.stage() == SceneFlowStage.IELTS_PART_1
				|| flow.stage() == SceneFlowStage.IELTS_PART_2
				|| flow.stage() == SceneFlowStage.IELTS_PART_3
				|| flow.stage() == SceneFlowStage.COMPLETED) {
			return List.of();
		}
		SceneGenerationResponse scene = findScene(flow.sceneId());
		return switch (flow.stage()) {
			case WORD_LEARNING -> scene.wordList();
			case PHRASE_LEARNING -> scene.phraseList();
			case SENTENCE_LEARNING -> scene.sentenceList();
			case DIALOGUE, IELTS_PART_1, IELTS_PART_2, IELTS_PART_3, COMPLETED -> throw new IllegalStateException(
					"dialogue stages do not expose learning content");
		};
	}

	private SceneFlowStage initialStage(String sceneId, SceneType sceneType) {
		if (sceneType == SceneType.FREE_CHAT
				|| sceneType == SceneType.INTERVIEW_SCENE) {
			return SceneFlowStage.DIALOGUE;
		}
		if (sceneType == SceneType.IELTS_SCENE) {
			return ieltsPracticeRepository.findPractice(sceneId)
					.orElseThrow(() -> new BusinessException(
							"IELTS_PRACTICE_NOT_FOUND",
							"IELTS 练习不存在"))
					.mode() == IeltsMode.MOCK_TEST
					? SceneFlowStage.IELTS_PART_1
					: SceneFlowStage.DIALOGUE;
		}
		return SceneFlowStage.WORD_LEARNING;
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
			case IELTS_PART_1 -> SceneFlowStage.IELTS_PART_2;
			case IELTS_PART_2 -> SceneFlowStage.IELTS_PART_3;
			case IELTS_PART_3 -> SceneFlowStage.COMPLETED;
			case DIALOGUE, COMPLETED -> SceneFlowStage.COMPLETED;
		};
	}
}
