package com.unispeaking.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.mybatis.entity.SceneEntity;
import com.unispeaking.infrastructure.persistence.mybatis.entity.ScenePhraseEntity;
import com.unispeaking.infrastructure.persistence.mybatis.entity.SceneSentenceEntity;
import com.unispeaking.infrastructure.persistence.mybatis.entity.SceneWordEntity;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.SceneMapper;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.ScenePhraseMapper;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.SceneSentenceMapper;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.SceneWordMapper;
import com.unispeaking.repository.SceneRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class MybatisSceneRepository implements SceneRepository {

	private final SceneMapper sceneMapper;
	private final SceneWordMapper wordMapper;
	private final ScenePhraseMapper phraseMapper;
	private final SceneSentenceMapper sentenceMapper;
	private final AsyncCustomScenePersistence asyncPersistence;
	private final Map<String, SceneGenerationResponse> generatedSceneCache =
			new ConcurrentHashMap<>();
	private final Map<String, CustomSceneDefinition> customSceneDefinitionCache =
			new ConcurrentHashMap<>();

	public MybatisSceneRepository(
			SceneMapper sceneMapper,
			SceneWordMapper wordMapper,
			ScenePhraseMapper phraseMapper,
			SceneSentenceMapper sentenceMapper,
			AsyncCustomScenePersistence asyncPersistence) {
		this.sceneMapper = sceneMapper;
		this.wordMapper = wordMapper;
		this.phraseMapper = phraseMapper;
		this.sentenceMapper = sentenceMapper;
		this.asyncPersistence = asyncPersistence;
	}

	@Override
	public Optional<SceneConfig> findByType(SceneType type) {
		return Optional.of(new SceneConfig(
				type,
				ProviderType.QWEN,
				null,
				"Katerina",
				true));
	}

	@Override
	public SceneGenerationResponse saveGenerated(SceneGenerationResponse scene) {
		generatedSceneCache.put(scene.sceneId(), scene);
		return scene;
	}

	@Override
	public SceneGenerationResponse saveCustomScene(
			CustomSceneDefinition definition,
			SceneGenerationResponse response) {
		generatedSceneCache.put(response.sceneId(), response);
		customSceneDefinitionCache.put(definition.sceneId(), definition);
		asyncPersistence.persist(definition);
		return response;
	}

	@Override
	public Optional<SceneGenerationResponse> findGeneratedById(String sceneId) {
		SceneGenerationResponse cached = generatedSceneCache.get(sceneId);
		if (cached != null) {
			return Optional.of(cached);
		}
		SceneEntity scene = sceneMapper.selectById(sceneId);
		if (scene == null || scene.getDeletedAt() != null) {
			return Optional.empty();
		}
		SceneGenerationResponse loaded = new SceneGenerationResponse(
				sceneId,
				loadWords(sceneId),
				loadPhrases(sceneId),
				loadSentences(sceneId),
				"");
		generatedSceneCache.put(sceneId, loaded);
		return Optional.of(loaded);
	}

	@Override
	public Optional<CustomSceneDefinition> findCustomDefinitionById(String sceneId) {
		CustomSceneDefinition cached = customSceneDefinitionCache.get(sceneId);
		if (cached != null) {
			return Optional.of(cached);
		}
		SceneEntity scene = sceneMapper.selectById(sceneId);
		if (scene == null || scene.getDeletedAt() != null) {
			return Optional.empty();
		}
		CustomSceneDefinition loaded = new CustomSceneDefinition(
				scene.getId(),
				scene.getUserId().toString(),
				scene.getTitle(),
				scene.getBackground(),
				scene.getAiRole(),
				scene.getUserRole(),
				scene.getLearningGoal(),
				scene.getCustomInstruction(),
				scene.getSuccessFactor(),
				loadWords(sceneId),
				loadPhrases(sceneId),
				loadSentences(sceneId));
		customSceneDefinitionCache.put(sceneId, loaded);
		return Optional.of(loaded);
	}

	private List<LearningContentItem> loadWords(String sceneId) {
		return wordMapper.selectList(new LambdaQueryWrapper<SceneWordEntity>()
						.eq(SceneWordEntity::getSceneId, sceneId)
						.orderByAsc(SceneWordEntity::getCreatedAt))
				.stream()
				.map(entity -> new LearningContentItem(
						entity.getWordId(),
						entity.getWord(),
						entity.getTranslation(),
						entity.getPhonetic()))
				.toList();
	}

	private List<LearningContentItem> loadPhrases(String sceneId) {
		return phraseMapper.selectList(new LambdaQueryWrapper<ScenePhraseEntity>()
						.eq(ScenePhraseEntity::getSceneId, sceneId)
						.orderByAsc(ScenePhraseEntity::getCreatedAt))
				.stream()
				.map(entity -> new LearningContentItem(
						entity.getId(),
						entity.getPhrase(),
						entity.getTranslation(),
						entity.getPhonetic()))
				.toList();
	}

	private List<LearningContentItem> loadSentences(String sceneId) {
		List<SceneSentenceEntity> rows = sentenceMapper.selectList(
				new LambdaQueryWrapper<SceneSentenceEntity>()
						.eq(SceneSentenceEntity::getSceneId, sceneId)
						.orderByAsc(SceneSentenceEntity::getCreatedAt));
		Map<String, LearningContentItem> firstReadingBySentence = new LinkedHashMap<>();
		for (SceneSentenceEntity row : rows) {
			firstReadingBySentence.putIfAbsent(
					row.getSentenceId(),
					new LearningContentItem(
							row.getSentenceId(),
							row.getSentence(),
							row.getTranslation(),
							""));
		}
		return List.copyOf(firstReadingBySentence.values());
	}

}
