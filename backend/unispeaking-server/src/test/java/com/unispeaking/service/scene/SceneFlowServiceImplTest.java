package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.service.scene.impl.SceneFlowServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SceneFlowServiceImplTest {

	@Test
	void freeChatStartsDirectlyAtDialogue() {
		SceneRepository repository = repositoryWith(
				scene("freechat_abc123", false));
		var service = new SceneFlowServiceImpl(repository);

		var flow = service.createFlow("freechat_abc123");

		assertEquals(SceneFlowStage.DIALOGUE, flow.stage());
		assertFalse(flow.completed());
		assertTrue(service.getByCurrentStage(
				"freechat_abc123",
				SceneFlowStage.DIALOGUE).isEmpty());
	}

	@Test
	void customSceneExposesContentForEachLearningStage() {
		SceneRepository repository = repositoryWith(
				scene("custom_def456", true));
		var service = new SceneFlowServiceImpl(repository);

		var wordStage = service.createFlow("custom_def456");
		assertEquals(SceneFlowStage.WORD_LEARNING, wordStage.stage());
		assertEquals("membership", service
					.getByCurrentStage(
							"custom_def456",
							SceneFlowStage.WORD_LEARNING)
				.getFirst()
				.englishText());

		var phraseStage = service.advanceStage(
				"custom_def456",
				SceneFlowStage.WORD_LEARNING);
		assertEquals(SceneFlowStage.PHRASE_LEARNING, phraseStage.stage());
		assertEquals("ask about", service
					.getByCurrentStage(
							"custom_def456",
							SceneFlowStage.PHRASE_LEARNING)
				.getFirst()
				.englishText());

		service.completeFlow("custom_def456", true);
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.getByCurrentStage(
						"custom_def456",
						SceneFlowStage.COMPLETED));
		assertEquals("SCENE_FLOW_NOT_FOUND", exception.code());
	}

	@Test
	void rejectsSceneIdsWithoutARegisteredScenePrefix() {
		SceneRepository repository = repositoryWith(
				scene("scene_legacy", true));
		var service = new SceneFlowServiceImpl(repository);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.createFlow("scene_legacy"));

		assertEquals("INVALID_SCENE_ID", exception.code());
	}

	@Test
	void completingAnAlreadyRemovedFlowIsIdempotent() {
		SceneRepository repository = repositoryWith(
				scene("custom_repeat123", true));
		var service = new SceneFlowServiceImpl(repository);

		service.completeFlow("custom_repeat123", true);

		var flow = service.createFlow("custom_repeat123");
		assertEquals(SceneFlowStage.WORD_LEARNING, flow.stage());
	}

	private SceneGenerationResponse scene(String sceneId, boolean withLearningContent) {
		return new SceneGenerationResponse(
				sceneId,
				withLearningContent ? List.of(item("word_1", "membership")) : List.of(),
				withLearningContent ? List.of(item("phrase_1", "ask about")) : List.of(),
				withLearningContent ? List.of(item("sentence_1", "Could you help me?")) : List.of(),
				"layer 1\n\nlayer 2\n\nlayer 3\n\nlayer 4\n\nlayer 5");
	}

	private SceneRepository repositoryWith(SceneGenerationResponse scene) {
		SceneRepository repository = mock(SceneRepository.class);
		when(repository.findGeneratedById(scene.sceneId()))
				.thenReturn(Optional.of(scene));
		return repository;
	}

	private LearningContentItem item(String contentId, String englishText) {
		return new LearningContentItem(contentId, englishText, "中文", "");
	}
}
