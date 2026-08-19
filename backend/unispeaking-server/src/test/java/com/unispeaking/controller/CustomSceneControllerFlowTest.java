package com.unispeaking.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.scene.AdvanceSceneStageRequest;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.service.asset.LearningAssetService;
import com.unispeaking.service.evaluation.CustomEvaluationService;
import com.unispeaking.service.scene.CustomSceneFlowService;
import com.unispeaking.service.scene.CustomSceneService;
import com.unispeaking.service.session.CustomSessionService;
import org.junit.jupiter.api.Test;

class CustomSceneControllerFlowTest {

	@Test
	void advanceUsesTheClientCurrentStageAfterARewind() {
		CustomSceneFlowService flow = mock(CustomSceneFlowService.class);
		when(flow.response("scene_1")).thenReturn(new SceneFlowResponse(
				"scene_1",
				SceneFlowStage.PHRASE_LEARNING,
				false));
		CustomSceneController controller = controller(flow);

		controller.advanceStage(new AdvanceSceneStageRequest(
				"scene_1",
				SceneFlowStage.WORD_LEARNING));

		verify(flow).next("scene_1", SceneFlowStage.WORD_LEARNING);
		verify(flow, never()).next("scene_1");
	}

	@Test
	void contentUsesTheExplicitlyRequestedLearningStage() {
		CustomSceneFlowService flow = mock(CustomSceneFlowService.class);
		CustomSceneController controller = controller(flow);

		controller.getByCurrentStage(
				"scene_1",
				SceneFlowStage.WORD_LEARNING);

		verify(flow).content("scene_1", SceneFlowStage.WORD_LEARNING);
	}

	private CustomSceneController controller(CustomSceneFlowService flow) {
		return new CustomSceneController(
				mock(CustomSceneService.class),
				flow,
				mock(CustomEvaluationService.class),
				mock(CustomSessionService.class),
				mock(LearningAssetService.class));
	}
}
