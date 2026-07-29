package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.infrastructure.persistence.inmemory.InMemorySceneRepository;
import com.unispeaking.service.scene.impl.SceneFlowServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;

class SceneFlowServiceImplTest {

	@Test
	void freeChatStartsDirectlyAtDialogue() {
		var repository = new InMemorySceneRepository();
		repository.saveGenerated(scene("freechat_abc123", false));
		var service = new SceneFlowServiceImpl(repository);

		var flow = service.createFlow("freechat_abc123");

		assertEquals(SceneFlowStage.DIALOGUE, flow.stage());
		assertFalse(flow.completed());
		assertTrue(service.getByCurrentStage(SceneFlowStage.DIALOGUE).isEmpty());
	}

	@Test
	void customSceneExposesContentForEachLearningStage() {
		var repository = new InMemorySceneRepository();
		repository.saveGenerated(scene("custom_def456", true));
		var service = new SceneFlowServiceImpl(repository);

		var wordStage = service.createFlow("custom_def456");
		assertEquals(SceneFlowStage.WORD_LEARNING, wordStage.stage());
		assertEquals("membership", service
				.getByCurrentStage(SceneFlowStage.WORD_LEARNING)
				.getFirst()
				.englishText());

		var phraseStage = service.advanceStage(SceneFlowStage.WORD_LEARNING);
		assertEquals(SceneFlowStage.PHRASE_LEARNING, phraseStage.stage());
		assertEquals("ask about", service
				.getByCurrentStage(SceneFlowStage.PHRASE_LEARNING)
				.getFirst()
				.englishText());

		service.completeFlow(true);
		assertTrue(service.getByCurrentStage(SceneFlowStage.COMPLETED).isEmpty());
	}

	@Test
	void rejectsSceneIdsWithoutARegisteredScenePrefix() {
		var repository = new InMemorySceneRepository();
		repository.saveGenerated(scene("scene_legacy", true));
		var service = new SceneFlowServiceImpl(repository);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.createFlow("scene_legacy"));

		assertEquals("INVALID_SCENE_ID", exception.code());
	}

	private SceneGenerationResponse scene(String sceneId, boolean withLearningContent) {
		return new SceneGenerationResponse(
				sceneId,
				withLearningContent ? List.of(item("word_1", "membership")) : List.of(),
				withLearningContent ? List.of(item("phrase_1", "ask about")) : List.of(),
				withLearningContent ? List.of(item("sentence_1", "Could you help me?")) : List.of(),
				"layer 1\n\nlayer 2\n\nlayer 3\n\nlayer 4\n\nlayer 5");
	}

	private LearningContentItem item(String contentId, String englishText) {
		return new LearningContentItem(contentId, englishText, "中文", "");
	}
}
