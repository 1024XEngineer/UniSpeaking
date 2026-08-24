package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.SceneNotFoundException;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.statemachine.ScenarioDialogueStateMachine;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.scene.CustomStage;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomSceneFlowServiceTest {

	private final SceneRepository repository = mock(SceneRepository.class);
	private final ScenarioDialogueStateMachine stateMachine = mock(ScenarioDialogueStateMachine.class);
	private final RealtimeSessionCoordinator sessions = mock(RealtimeSessionCoordinator.class);
	private final CustomSceneFlowService service = new CustomSceneFlowService(repository, stateMachine, sessions);

	@BeforeEach
	void provideScene() {
		SceneGenerationResponse generated = scene("scene");
		when(repository.findGeneratedById("scene")).thenReturn(Optional.of(generated));
	}

	@Test
	void startRequiresPersistedSceneAndClearRemovesItsState() {
		when(repository.findGeneratedById("missing")).thenReturn(Optional.empty());
		assertThrows(SceneNotFoundException.class, () -> service.start("missing"));
		assertCode("SCENE_FLOW_NOT_FOUND", () -> service.current("scene"));

		assertEquals(CustomStage.WORD, service.start("scene"));
		service.clear("scene");
		assertCode("SCENE_FLOW_NOT_FOUND", () -> service.current("scene"));
	}

	@Test
	void contentRejectsFutureStagesButPermitsPreviouslyUnlockedStages() {
		service.start("scene");
		assertCode("SCENE_FLOW_STAGE_OUT_OF_ORDER", () -> service.content("scene", SceneFlowStage.PHRASE_LEARNING));
		assertEquals(CustomStage.PHRASE, service.next("scene"));
		assertEquals("phrase", service.content("scene").getFirst().englishText());
		assertEquals("word", service.content("scene", SceneFlowStage.WORD_LEARNING).getFirst().englishText());
		assertCode("SCENE_FLOW_STAGE_INVALID", () -> service.content("scene", SceneFlowStage.IELTS_PART_1));
	}

	@Test
	void clientCannotAdvancePastFurthestUnlockedStage() {
		service.start("scene");
		assertCode("SCENE_FLOW_STAGE_OUT_OF_ORDER", () -> service.next("scene", SceneFlowStage.DIALOGUE));

		service.next("scene");
		service.next("scene");
		assertEquals(CustomStage.PHRASE, service.next("scene", SceneFlowStage.WORD_LEARNING));
		assertEquals(CustomStage.DIALOGUE, service.next("scene", SceneFlowStage.SENTENCE_LEARNING));
		assertTrue(!service.isCompleted("scene"));
	}

	@Test
	void dialogueOperationsRequireTheMatchingCustomSceneSession() {
		CustomSceneDefinition definition = definition("scene", "user");
		when(repository.findCustomDefinitionById("scene")).thenReturn(Optional.of(definition));
		AbstractSceneSession wrongSession = mock(AbstractSceneSession.class);
		when(wrongSession.getSceneType()).thenReturn(SceneType.IELTS_SCENE);
		when(sessions.requireOwnedSession("user", "session")).thenReturn(wrongSession);
		assertCode("SESSION_ACCESS_DENIED", () -> service.getDialogueState("scene", "session"));

		AbstractSceneSession matchingSession = mock(AbstractSceneSession.class);
		when(matchingSession.getSceneType()).thenReturn(SceneType.CUSTOM_SCENE);
		when(matchingSession.getSceneId()).thenReturn("scene");
		when(sessions.requireOwnedSession("user", "session")).thenReturn(matchingSession);
		ScenarioDialogueStateResponse response = mock(ScenarioDialogueStateResponse.class);
		when(stateMachine.getState("session")).thenReturn(response);

		assertEquals(response, service.getDialogueState("scene", "session"));
		verify(stateMachine).getState("session");
	}

	@Test
	void dialogueStartAndAdvanceDelegateOnlyAfterBindingValidation() {
		when(repository.findCustomDefinitionById("scene")).thenReturn(Optional.of(definition("scene", "user")));
		AbstractSceneSession matching = mock(AbstractSceneSession.class);
		when(matching.getSceneType()).thenReturn(SceneType.CUSTOM_SCENE);
		when(matching.getSceneId()).thenReturn("scene");
		when(sessions.requireOwnedSession("user", "session")).thenReturn(matching);
		ScenarioDialogueStateResponse response = mock(ScenarioDialogueStateResponse.class);
		when(stateMachine.start("session", "scene", "{}", "goal")).thenReturn(response);
		when(stateMachine.advance("session", 2, "hello")).thenReturn(response);

		assertEquals(response, service.startDialogueState("scene", "session", "{}", "goal"));
		assertEquals(response, service.advanceDialogueState("scene", "session", 2, "hello"));
		verify(stateMachine).start("session", "scene", "{}", "goal");
		verify(stateMachine).advance("session", 2, "hello");
	}

	@Test
	void responseAndCompletionExposeLegacyStagesAndCompletionFlag() {
		assertCode("SCENE_FLOW_NOT_FOUND", () -> service.response("scene"));
		assertEquals(CustomStage.WORD, service.start("scene"));
		assertEquals(SceneFlowStage.WORD_LEARNING, service.response("scene").stage());
		service.next("scene");
		service.next("scene");
		service.next("scene");
		assertEquals(CustomStage.COMPLETED, service.next("scene"));
		assertEquals(SceneFlowStage.COMPLETED, service.response("scene").stage());
		assertTrue(service.response("scene").completed());
		assertTrue(service.isCompleted("scene"));
	}

	@Test
	void completedAndDialogueContentAreEmptyAndMissingGeneratedSceneIsRejected() {
		service.start("scene");
		service.next("scene");
		service.next("scene");
		service.next("scene");
		service.next("scene");
		assertTrue(service.content("scene", SceneFlowStage.DIALOGUE).isEmpty());
		assertTrue(service.content("scene", SceneFlowStage.COMPLETED).isEmpty());

		when(repository.findGeneratedById("deleted")).thenReturn(Optional.empty());
		assertCode("SCENE_FLOW_NOT_FOUND", () -> service.content("deleted"));
	}

	@Test
	void dialogueBindingRejectsMissingDefinitionAndWrongSceneId() {
		when(repository.findCustomDefinitionById("missing")).thenReturn(Optional.empty());
		assertCode("SCENE_NOT_FOUND", () -> service.getDialogueState("missing", "session"));

		when(repository.findCustomDefinitionById("scene")).thenReturn(Optional.of(definition("scene", "user")));
		AbstractSceneSession wrongScene = mock(AbstractSceneSession.class);
		when(wrongScene.getSceneType()).thenReturn(SceneType.CUSTOM_SCENE);
		when(wrongScene.getSceneId()).thenReturn("other-scene");
		when(sessions.requireOwnedSession("user", "session")).thenReturn(wrongScene);
		assertCode("SESSION_ACCESS_DENIED", () -> service.startDialogueState("scene", "session", "{}", "goal"));
	}

	@Test
	void closingReturnsNullWhenDialogueStateDoesNotExistAndClearDelegates() {
		when(repository.findCustomDefinitionById("scene")).thenReturn(Optional.of(definition("scene", "user")));
		AbstractSceneSession matching = mock(AbstractSceneSession.class);
		when(matching.getSceneType()).thenReturn(SceneType.CUSTOM_SCENE);
		when(matching.getSceneId()).thenReturn("scene");
		when(sessions.requireOwnedSession("user", "session")).thenReturn(matching);
		when(stateMachine.findState("session")).thenReturn(Optional.empty());

		assertNull(service.beginDialogueClosing("scene", "session"));
		service.clearDialogueState("session");
		verify(stateMachine).remove("session");
	}

	private void assertCode(String expected, org.junit.jupiter.api.function.Executable action) {
		assertEquals(expected, assertThrows(BusinessException.class, action).code());
	}

	private SceneGenerationResponse scene(String id) {
		return new SceneGenerationResponse(id, List.of(item("word")), List.of(item("phrase")),
				List.of(item("sentence")), "prompt");
	}

	private CustomSceneDefinition definition(String sceneId, String userId) {
		return new CustomSceneDefinition(sceneId, userId, "title", "label", "background", "ai", "user",
				"goal", "", "{}", List.of(), List.of(), List.of());
	}

	private LearningContentItem item(String english) {
		return new LearningContentItem(english, english, "中文", "");
	}
}
