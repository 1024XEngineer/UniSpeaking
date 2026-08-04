package com.unispeaking.infrastructure.persistence.repository.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.ScenePhraseEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneSentenceEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneWordEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.ScenePhraseMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneSentenceMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneWordMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MybatisSceneRepositoryTest {

	@Test
	void persistsCustomSceneSynchronouslyWithoutAnInMemoryCache() {
		SceneMapper sceneMapper = mock(SceneMapper.class);
		SceneWordMapper wordMapper = mock(SceneWordMapper.class);
		ScenePhraseMapper phraseMapper = mock(ScenePhraseMapper.class);
		SceneSentenceMapper sentenceMapper = mock(SceneSentenceMapper.class);
		CustomScenePersistence scenePersistence =
				mock(CustomScenePersistence.class);
		var repository = new MybatisSceneRepository(
				sceneMapper,
				wordMapper,
				phraseMapper,
				sentenceMapper,
				scenePersistence);
		var fixture = fixture();

		SceneGenerationResponse saved = repository.saveCustomScene(
				fixture.definition(),
				fixture.response());

		verify(scenePersistence).persist(fixture.definition());
		assertSame(fixture.response(), saved);
	}

	@Test
	void persistsSceneAndAllGeneratedLearningContent() {
		SceneMapper sceneMapper = mock(SceneMapper.class);
		SceneWordMapper wordMapper = mock(SceneWordMapper.class);
		ScenePhraseMapper phraseMapper = mock(ScenePhraseMapper.class);
		SceneSentenceMapper sentenceMapper = mock(SceneSentenceMapper.class);
		var persistence = new CustomScenePersistence(
				sceneMapper,
				wordMapper,
				phraseMapper,
				sentenceMapper);
		var fixture = fixture();

		persistence.persist(fixture.definition());

		verify(sceneMapper).insert(any(SceneEntity.class));
		verify(wordMapper, times(5)).insert(any(SceneWordEntity.class));
		verify(phraseMapper, times(5)).insert(any(ScenePhraseEntity.class));
		verify(sentenceMapper, times(3)).insert(any(SceneSentenceEntity.class));
		ArgumentCaptor<SceneWordEntity> word =
				ArgumentCaptor.forClass(SceneWordEntity.class);
		verify(wordMapper, times(5)).insert(word.capture());
		assertTrue(word.getAllValues().stream()
				.allMatch(value -> value.getSceneId().equals("custom_abc123")
						&& value.getWordId().startsWith("word_")));
		ArgumentCaptor<ScenePhraseEntity> phrase =
				ArgumentCaptor.forClass(ScenePhraseEntity.class);
		verify(phraseMapper, times(5)).insert(phrase.capture());
		assertTrue(phrase.getAllValues().stream()
				.allMatch(value -> value.getSceneId().equals("custom_abc123")
						&& value.getPhraseId().startsWith("phrase_")));
		ArgumentCaptor<SceneSentenceEntity> sentence =
				ArgumentCaptor.forClass(SceneSentenceEntity.class);
		verify(sentenceMapper, times(3)).insert(sentence.capture());
		assertTrue(sentence.getAllValues().stream()
				.allMatch(value -> value.getSceneId().equals("custom_abc123")
						&& value.getSentenceId().startsWith("sentence_")));
	}

	@Test
	void countsOnlyActiveTrainingRecords() {
		SceneMapper sceneMapper = mock(SceneMapper.class);
		when(sceneMapper.selectCount(any())).thenReturn(3L);
		var repository = new MybatisSceneRepository(
				sceneMapper,
				mock(SceneWordMapper.class),
				mock(ScenePhraseMapper.class),
				mock(SceneSentenceMapper.class),
				mock(CustomScenePersistence.class));

		long count = repository.countActiveByUserId(
				"11111111-1111-4111-8111-111111111111");

		assertEquals(3, count);
		verify(sceneMapper).selectCount(any());
	}

	@Test
	void countsHistoricalAssetsIncludingSoftDeletedScenes() {
		SceneMapper sceneMapper = mock(SceneMapper.class);
		when(sceneMapper.selectCount(any())).thenReturn(7L);
		var repository = new MybatisSceneRepository(
				sceneMapper,
				mock(SceneWordMapper.class),
				mock(ScenePhraseMapper.class),
				mock(SceneSentenceMapper.class),
				mock(CustomScenePersistence.class));

		assertEquals(
				7,
				repository.countAllByUserId(
						"11111111-1111-4111-8111-111111111111"));
		assertEquals(0, repository.countAllByUserId("invalid-user-id"));
	}

	private Fixture fixture() {
		String sceneId = "custom_abc123";
		List<LearningContentItem> words = items("word", 5);
		List<LearningContentItem> phrases = items("phrase", 5);
		List<LearningContentItem> sentences = items("sentence", 3);
		var definition = new CustomSceneDefinition(
				sceneId,
				"11111111-1111-4111-8111-111111111111",
				"酒店办理入住",
				"酒店前台",
				"前台接待员",
				"住客",
				"完成入住",
				"保持礼貌",
				"{\"minimum_user_turns\":5}",
				words,
				phrases,
				sentences);
		var response = new SceneGenerationResponse(
				sceneId,
				words,
				phrases,
				sentences,
				"five-layer prompt");
		return new Fixture(definition, response);
	}

	private List<LearningContentItem> items(String prefix, int count) {
		return java.util.stream.IntStream.range(0, count)
				.mapToObj(index -> new LearningContentItem(
						prefix + "_" + index,
						prefix + index,
						"翻译" + index,
						"/" + prefix + index + "/"))
				.toList();
	}

	private record Fixture(
			CustomSceneDefinition definition,
			SceneGenerationResponse response) {
	}
}
