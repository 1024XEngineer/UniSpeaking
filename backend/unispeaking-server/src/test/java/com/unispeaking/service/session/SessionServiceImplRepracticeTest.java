package com.unispeaking.service.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.prompt.FiveLayerPromptBuilder;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.infrastructure.realtime.RealtimeSdpExchange;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.asset.impl.ObsoleteDialogueCleanup;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.evaluation.EvaluationService;
import com.unispeaking.service.profile.ProfileService;
import com.unispeaking.service.scene.SceneFlowService;
import com.unispeaking.service.scene.SceneService;
import com.unispeaking.service.scene.impl.ScenarioDialogueStateMachine;
import com.unispeaking.service.session.impl.SessionServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionServiceImplRepracticeTest {

	@Test
	void repracticeStartsWithoutAnInMemorySceneFlow() {
		String userId = "user_1";
		String sceneId = "custom_repeat123";
		String prompt = "layer 1\n\nlayer 2\n\nlayer 3\n\nlayer 4\n\nlayer 5";
		AuthService authService = mock(AuthService.class);
		SceneFlowService sceneFlowService = mock(SceneFlowService.class);
		SceneRepository sceneRepository = mock(SceneRepository.class);
		RealtimeSdpExchange realtimeSdpExchange = mock(RealtimeSdpExchange.class);
		ScenarioDialogueStateMachine stateMachine =
				mock(ScenarioDialogueStateMachine.class);
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		CustomSceneDefinition definition = new CustomSceneDefinition(
				sceneId,
				userId,
				"Coffee shop",
				"Order a drink",
				"Barista",
				"Customer",
				"Complete the order",
				"",
				"{}",
				List.of(),
				List.of(),
				List.of());
		SceneGenerationResponse scene = new SceneGenerationResponse(
				sceneId,
				List.of(),
				List.of(),
				List.of(),
				prompt);

		when(authService.requireUserId(isNull())).thenReturn(userId);
		when(sceneRepository.findCustomDefinitionById(sceneId))
				.thenReturn(Optional.of(definition));
		when(sceneRepository.findGeneratedById(sceneId))
				.thenReturn(Optional.of(scene));
		when(realtimeSdpExchange.exchangeSdp(
				any(),
				any(),
				any(),
				any()))
				.thenReturn(new RealtimeConnectionResult(
						null,
						"answer-sdp",
						Instant.parse("2026-07-31T05:00:00Z")));
		SessionServiceImpl service = new SessionServiceImpl(
				authService,
				mock(SceneService.class),
				sceneFlowService,
				sceneRepository,
				sessions,
				mock(SessionMessageRepository.class),
				realtimeSdpExchange,
				mock(EvaluationService.class),
				stateMachine,
				mock(ProfileService.class),
				mock(FiveLayerPromptBuilder.class),
				mock(AiProviderRegistry.class),
				mock(ObsoleteDialogueCleanup.class));

		var response = service.startCustomScene(
				sceneId,
				new StartCustomSceneDialogueRequest(
						"offer-sdp",
						ProviderType.QWEN,
						"qwen3.5-omni-flash-realtime",
						"Aiden",
						true));

		assertEquals(sceneId, response.sceneId());
		assertEquals(SceneFlowStage.DIALOGUE, response.currentStage());
		assertEquals("answer-sdp", response.answerSdp());
		assertNotNull(response.sessionId());
		verify(sceneFlowService, never()).getByCurrentStage(
				any(),
				any());
		verify(stateMachine).start(any(), any());
	}
}
