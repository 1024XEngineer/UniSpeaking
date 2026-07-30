package com.unispeaking.infrastructure.persistence.mybatis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.infrastructure.persistence.mybatis.entity.SceneEntity;
import com.unispeaking.infrastructure.persistence.mybatis.entity.ScenePhraseEntity;
import com.unispeaking.infrastructure.persistence.mybatis.entity.SceneSentenceEntity;
import com.unispeaking.infrastructure.persistence.mybatis.entity.SceneWordEntity;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.SceneMapper;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.ScenePhraseMapper;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.SceneSentenceMapper;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.SceneWordMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MybatisSceneRepositoryTest {

	@Test
	void cachesGeneratedSceneBeforeSchedulingPersistence() {
		SceneMapper sceneMapper = mock(SceneMapper.class);
		SceneWordMapper wordMapper = mock(SceneWordMapper.class);
		ScenePhraseMapper phraseMapper = mock(ScenePhraseMapper.class);
		SceneSentenceMapper sentenceMapper = mock(SceneSentenceMapper.class);
		AsyncCustomScenePersistence asyncPersistence =
				mock(AsyncCustomScenePersistence.class);
		var repository = new MybatisSceneRepository(
				sceneMapper,
				wordMapper,
				phraseMapper,
				sentenceMapper,
				asyncPersistence);
		var fixture = fixture();

		repository.saveCustomScene(fixture.definition(), fixture.response());

		verify(asyncPersistence).persist(fixture.definition());
		assertEquals(
				"five-layer prompt",
				repository.findGeneratedById(fixture.definition().sceneId())
						.orElseThrow()
						.scenePrompt());
		assertEquals(
				"酒店办理入住",
				repository.findCustomDefinitionById(fixture.definition().sceneId())
						.orElseThrow()
						.title());
	}

	@Test
	void persistsSceneAndAllGeneratedLearningContent() {
		SceneMapper sceneMapper = mock(SceneMapper.class);
		SceneWordMapper wordMapper = mock(SceneWordMapper.class);
		ScenePhraseMapper phraseMapper = mock(ScenePhraseMapper.class);
		SceneSentenceMapper sentenceMapper = mock(SceneSentenceMapper.class);
		var persistence = new AsyncCustomScenePersistence(
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
		ArgumentCaptor<SceneSentenceEntity> sentence =
				ArgumentCaptor.forClass(SceneSentenceEntity.class);
		verify(sentenceMapper, times(3)).insert(sentence.capture());
		assertTrue(sentence.getAllValues().stream()
				.allMatch(value -> value.getId().startsWith("sentence_reading_")));
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
