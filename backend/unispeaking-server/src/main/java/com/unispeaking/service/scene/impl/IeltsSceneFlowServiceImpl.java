package com.unispeaking.service.scene.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsStage;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.service.scene.SceneFlowService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class IeltsSceneFlowServiceImpl implements SceneFlowService<IeltsStage> {

	private final IeltsPracticeRepository practiceRepository;
	private final Map<String, IeltsStage> stages = new ConcurrentHashMap<>();

	public IeltsSceneFlowServiceImpl(IeltsPracticeRepository practiceRepository) {
		this.practiceRepository = practiceRepository;
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

	public SceneFlowResponse response(String sceneId) {
		IeltsStage stage = current(sceneId);
		return new SceneFlowResponse(
				sceneId,
				toLegacyStage(stage),
				stage == IeltsStage.COMPLETED);
	}

	public IeltsPart currentPart(String sceneId) {
		return switch (current(sceneId)) {
			case PART1 -> IeltsPart.PART_1;
			case PART2 -> IeltsPart.PART_2;
			case PART3 -> IeltsPart.PART_3;
			case COMPLETED -> throw new BusinessException(
					"IELTS_FLOW_COMPLETED",
					"IELTS flow is already completed");
		};
	}

	public void clear(String sceneId) {
		stages.remove(sceneId);
	}

	private IeltsPracticeRecord requireScene(String sceneId) {
		return practiceRepository.findPractice(sceneId)
				.orElseThrow(() -> new BusinessException(
						"IELTS_PRACTICE_NOT_FOUND",
						"IELTS 练习不存在"));
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
