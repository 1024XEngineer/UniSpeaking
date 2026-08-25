package com.unispeaking.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.scene.CompleteSceneFlowRequest;
import com.unispeaking.domain.dto.scene.CreateSceneFlowRequest;
import com.unispeaking.component.scene.CustomSceneGenerationCoordinator;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.session.AdvanceScenarioDialogueTurnRequest;
import com.unispeaking.domain.dto.scene.AdvanceSceneStageRequest;
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

	@Test
	void createFlowStartsTheFlowBeforeReadingItsResponse() {
		CustomSceneFlowService flow = mock(CustomSceneFlowService.class);
		CustomSceneController controller = controller(flow);

		controller.createFlow(new CreateSceneFlowRequest("scene_1"));

		verify(flow).start("scene_1");
		verify(flow).response("scene_1");
	}

	@Test
	void advanceWithoutAStageUsesTheServerCurrentStage() {
		CustomSceneFlowService flow = mock(CustomSceneFlowService.class);
		CustomSceneController controller = controller(flow);

		controller.advanceStage(new AdvanceSceneStageRequest("scene_1", null));

		verify(flow).next("scene_1");
		verify(flow, never()).next("scene_1", SceneFlowStage.WORD_LEARNING);
		verify(flow).response("scene_1");
	}

	@Test
	void completingAnIncompleteFlowAdvancesUntilCompletedThenClearsIt() {
		CustomSceneFlowService flow = mock(CustomSceneFlowService.class);
		when(flow.isCompleted("scene_1")).thenReturn(false, false, true);
		CustomSceneController controller = controller(flow);

		controller.completeFlow(new CompleteSceneFlowRequest("scene_1", true));

		verify(flow, org.mockito.Mockito.times(2)).next("scene_1");
		verify(flow).clear("scene_1");
	}

	@Test
	void incompleteFlagDoesNotAdvanceOrClearTheFlow() {
		CustomSceneFlowService flow = mock(CustomSceneFlowService.class);
		CustomSceneController controller = controller(flow);

		controller.completeFlow(new CompleteSceneFlowRequest("scene_1", false));

		verify(flow, never()).isCompleted("scene_1");
		verify(flow, never()).next("scene_1");
		verify(flow, never()).clear("scene_1");
	}

	@Test
	void dialogueStateEndpointsDelegateAllPathAndBodyParameters() {
		CustomSceneFlowService flow = mock(CustomSceneFlowService.class);
		CustomSceneController controller = controller(flow);
		AdvanceScenarioDialogueTurnRequest request =
				new AdvanceScenarioDialogueTurnRequest("answer");

		controller.advanceDialogueState("scene_1", "session_1", 3, request);
		controller.getDialogueState("scene_1", "session_1");

		verify(flow).advanceDialogueState("scene_1", "session_1", 3, "answer");
		verify(flow).getDialogueState("scene_1", "session_1");
	}

	private CustomSceneController controller(CustomSceneFlowService flow) {
		return new CustomSceneController(
				mock(CustomSceneService.class),
				flow,
				mock(CustomEvaluationService.class),
				mock(CustomSessionService.class),
					mock(LearningAssetService.class),
					mock(CustomSceneGenerationCoordinator.class));
	}
}
