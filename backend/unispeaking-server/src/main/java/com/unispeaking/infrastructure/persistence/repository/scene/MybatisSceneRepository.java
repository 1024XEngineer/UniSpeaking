package com.unispeaking.infrastructure.persistence.repository.scene;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.scene.SceneAssetSnapshot;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.ScenePhraseEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneSentenceEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneWordEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.ScenePhraseMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneSentenceMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneWordMapper;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class MybatisSceneRepository implements SceneRepository {

	private final SceneMapper sceneMapper;
	private final SceneWordMapper wordMapper;
	private final ScenePhraseMapper phraseMapper;
	private final SceneSentenceMapper sentenceMapper;
	private final CustomScenePersistence scenePersistence;

	public MybatisSceneRepository(
			SceneMapper sceneMapper,
			SceneWordMapper wordMapper,
			ScenePhraseMapper phraseMapper,
			SceneSentenceMapper sentenceMapper,
			CustomScenePersistence scenePersistence) {
		this.sceneMapper = sceneMapper;
		this.wordMapper = wordMapper;
		this.phraseMapper = phraseMapper;
		this.sentenceMapper = sentenceMapper;
		this.scenePersistence = scenePersistence;
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
	public SceneGenerationResponse saveCustomScene(
			CustomSceneDefinition definition,
			SceneGenerationResponse response) {
		scenePersistence.persist(definition);
		return response;
	}

	@Override
	public Optional<SceneGenerationResponse> findGeneratedById(String sceneId) {
		SceneEntity scene = sceneMapper.selectById(sceneId);
		if (scene == null || scene.getDeletedAt() != null) {
			return Optional.empty();
		}
		return Optional.of(new SceneGenerationResponse(
				sceneId,
				loadWords(sceneId),
				loadPhrases(sceneId),
				loadSentences(sceneId),
				""));
	}

	@Override
	public Optional<CustomSceneDefinition> findCustomDefinitionById(String sceneId) {
		SceneEntity scene = sceneMapper.selectById(sceneId);
		if (scene == null || scene.getDeletedAt() != null) {
			return Optional.empty();
		}
		return Optional.of(new CustomSceneDefinition(
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
				loadSentences(sceneId)));
	}

	@Override
	public List<SceneAssetSnapshot> findAssetsByUserId(String userId) {
		UUID ownerId;
		try {
			ownerId = UUID.fromString(userId);
		}
		catch (IllegalArgumentException exception) {
			return List.of();
		}
		return sceneMapper.selectList(new LambdaQueryWrapper<SceneEntity>()
						.eq(SceneEntity::getUserId, ownerId)
						.isNull(SceneEntity::getDeletedAt)
						.orderByDesc(SceneEntity::getUpdatedAt))
				.stream()
				.map(scene -> new SceneAssetSnapshot(
						toDefinition(scene),
						scene.getCreatedAt(),
						scene.getUpdatedAt()))
				.toList();
	}

	private CustomSceneDefinition toDefinition(SceneEntity scene) {
		return new CustomSceneDefinition(
				scene.getId(),
				scene.getUserId().toString(),
				scene.getTitle(),
				scene.getBackground(),
				scene.getAiRole(),
				scene.getUserRole(),
				scene.getLearningGoal(),
				scene.getCustomInstruction(),
				scene.getSuccessFactor(),
				loadWords(scene.getId()),
				loadPhrases(scene.getId()),
				loadSentences(scene.getId()));
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
						entity.getPhraseId(),
						entity.getPhrase(),
						entity.getTranslation(),
						entity.getPhonetic()))
				.toList();
	}

	private List<LearningContentItem> loadSentences(String sceneId) {
		return sentenceMapper.selectList(
						new LambdaQueryWrapper<SceneSentenceEntity>()
						.eq(SceneSentenceEntity::getSceneId, sceneId)
						.orderByAsc(SceneSentenceEntity::getCreatedAt))
				.stream()
				.map(entity -> new LearningContentItem(
						entity.getSentenceId(),
						entity.getSentence(),
						entity.getTranslation(),
						""))
				.toList();
	}

}
