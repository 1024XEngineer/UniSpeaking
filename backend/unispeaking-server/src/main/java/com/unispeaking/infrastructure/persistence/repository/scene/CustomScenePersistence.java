package com.unispeaking.infrastructure.persistence.repository.scene;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.ScenePhraseEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneSentenceEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneWordEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.ScenePhraseMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneSentenceMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.SceneWordMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test")
public class CustomScenePersistence {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(CustomScenePersistence.class);

	private final SceneMapper sceneMapper;
	private final SceneWordMapper wordMapper;
	private final ScenePhraseMapper phraseMapper;
	private final SceneSentenceMapper sentenceMapper;

	public CustomScenePersistence(
			SceneMapper sceneMapper,
			SceneWordMapper wordMapper,
			ScenePhraseMapper phraseMapper,
			SceneSentenceMapper sentenceMapper) {
		this.sceneMapper = sceneMapper;
		this.wordMapper = wordMapper;
		this.phraseMapper = phraseMapper;
		this.sentenceMapper = sentenceMapper;
	}

	@Transactional
	public void persist(CustomSceneDefinition definition) {
		long startedAt = System.nanoTime();
		sceneMapper.insert(toSceneEntity(definition));
		definition.wordList().forEach(item -> wordMapper.insert(toWordEntity(
				definition.sceneId(),
				item)));
		definition.phraseList().forEach(item -> phraseMapper.insert(toPhraseEntity(
				definition.sceneId(),
				item)));
		definition.sentenceList().forEach(item -> sentenceMapper.insert(toSentenceEntity(
				definition.sceneId(),
				item)));
		LOGGER.info(
				"custom scene persisted sceneId={} words={} phrases={} sentences={} persistenceMs={}",
				definition.sceneId(),
				definition.wordList().size(),
				definition.phraseList().size(),
				definition.sentenceList().size(),
				elapsedMillis(startedAt));
	}

	private SceneEntity toSceneEntity(CustomSceneDefinition definition) {
		SceneEntity entity = new SceneEntity();
		entity.setId(definition.sceneId());
		entity.setUserId(UUID.fromString(definition.userId()));
		entity.setTitle(definition.title());
		entity.setLabel(definition.label());
		entity.setBackground(definition.background());
		entity.setAiRole(definition.aiRole());
		entity.setUserRole(definition.userRole());
		entity.setLearningGoal(definition.learningGoal());
		entity.setCustomInstruction(definition.customInstruction());
		entity.setSuccessFactor(definition.successFactorJson());
		return entity;
	}

	private SceneWordEntity toWordEntity(
			String sceneId,
			LearningContentItem item) {
		SceneWordEntity entity = new SceneWordEntity();
		entity.setWordId(item.contentId());
		entity.setSceneId(sceneId);
		entity.setWord(item.englishText());
		entity.setPhonetic(item.phonetic());
		entity.setTranslation(item.chineseText());
		return entity;
	}

	private ScenePhraseEntity toPhraseEntity(
			String sceneId,
			LearningContentItem item) {
		ScenePhraseEntity entity = new ScenePhraseEntity();
		entity.setSceneId(sceneId);
		entity.setPhraseId(item.contentId());
		entity.setPhrase(item.englishText());
		entity.setPhonetic(item.phonetic());
		entity.setTranslation(item.chineseText());
		return entity;
	}

	private SceneSentenceEntity toSentenceEntity(
			String sceneId,
			LearningContentItem item) {
		SceneSentenceEntity entity = new SceneSentenceEntity();
		entity.setSceneId(sceneId);
		entity.setSentenceId(item.contentId());
		entity.setSentence(item.englishText());
		entity.setTranslation(item.chineseText());
		return entity;
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}
