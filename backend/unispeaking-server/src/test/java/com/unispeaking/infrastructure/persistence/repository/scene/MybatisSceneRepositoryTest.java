package com.unispeaking.infrastructure.persistence.repository.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.ScenePhraseEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneSentenceEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneWordEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.ScenePhraseMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneSentenceMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneWordMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import java.util.List;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MybatisSceneRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(
						new MybatisConfiguration(),
						"mybatis-scene-repository-test"),
				SceneEntity.class);
	}

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
	void softDeletesOnlyAnActiveOwnedScene() {
		SceneMapper sceneMapper = mock(SceneMapper.class);
		when(sceneMapper.update(any(), any())).thenReturn(1);
		MybatisSceneRepository repository = repository(sceneMapper);

		assertTrue(repository.softDelete(
				"custom_1",
				"11111111-1111-4111-8111-111111111111"));
		assertFalse(repository.softDelete("custom_1", "invalid-user-id"));

		verify(sceneMapper).update(any(), any());
	}

	@Test
	void exposesDefaultSceneProviderAndRejectsDeletedDefinitions() {
		SceneMapper sceneMapper = mock(SceneMapper.class);
		MybatisSceneRepository repository = repository(sceneMapper);
		SceneEntity deleted = new SceneEntity();
		deleted.setDeletedAt(OffsetDateTime.now());
		when(sceneMapper.selectById("deleted")).thenReturn(deleted);

		var config = repository.findByType(SceneType.CUSTOM_SCENE)
				.orElseThrow();

		assertEquals(SceneType.CUSTOM_SCENE, config.type());
		assertEquals(ProviderType.QWEN, config.providerType());
		assertTrue(repository.findCustomDefinitionById("deleted").isEmpty());
	}

	@Test
	void userIdQueriesReturnEmptyForInvalidUuidAndListOwnedIds() {
		SceneMapper sceneMapper = mock(SceneMapper.class);
		SceneEntity first = new SceneEntity();
		first.setId("custom_1");
		SceneEntity second = new SceneEntity();
		second.setId("custom_2");
		when(sceneMapper.selectList(any())).thenReturn(List.of(first, second));
		MybatisSceneRepository repository = repository(sceneMapper);

		assertEquals(0, repository.countActiveByUserId("not-a-uuid"));
		assertTrue(repository.findAllIdsByUserId("not-a-uuid").isEmpty());
		assertEquals(
				List.of("custom_1", "custom_2"),
				repository.findAllIdsByUserId(
						"11111111-1111-4111-8111-111111111111"));
		verify(sceneMapper, never()).selectCount(any());
		verify(sceneMapper).selectList(any());
	}

	private MybatisSceneRepository repository(SceneMapper sceneMapper) {
		return new MybatisSceneRepository(
				sceneMapper,
				mock(SceneWordMapper.class),
				mock(ScenePhraseMapper.class),
				mock(SceneSentenceMapper.class),
				mock(CustomScenePersistence.class));
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
				"住宿",
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
