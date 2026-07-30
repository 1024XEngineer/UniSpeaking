package com.unispeaking.infrastructure.persistence.mybatis;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.infrastructure.persistence.mybatis.entity.SceneEntity;
import com.unispeaking.infrastructure.persistence.mybatis.entity.ScenePhraseEntity;
import com.unispeaking.infrastructure.persistence.mybatis.entity.SceneSentenceEntity;
import com.unispeaking.infrastructure.persistence.mybatis.entity.SceneWordEntity;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.SceneMapper;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.ScenePhraseMapper;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.SceneSentenceMapper;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.SceneWordMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test")
public class AsyncCustomScenePersistence {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(AsyncCustomScenePersistence.class);

	private final SceneMapper sceneMapper;
	private final SceneWordMapper wordMapper;
	private final ScenePhraseMapper phraseMapper;
	private final SceneSentenceMapper sentenceMapper;

	public AsyncCustomScenePersistence(
			SceneMapper sceneMapper,
			SceneWordMapper wordMapper,
			ScenePhraseMapper phraseMapper,
			SceneSentenceMapper sentenceMapper) {
		this.sceneMapper = sceneMapper;
		this.wordMapper = wordMapper;
		this.phraseMapper = phraseMapper;
		this.sentenceMapper = sentenceMapper;
	}

	@Async("scenePersistenceExecutor")
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
		entity.setId(item.contentId());
		entity.setSceneId(sceneId);
		entity.setPhrase(item.englishText());
		entity.setPhonetic(item.phonetic());
		entity.setTranslation(item.chineseText());
		return entity;
	}

	private SceneSentenceEntity toSentenceEntity(
			String sceneId,
			LearningContentItem item) {
		SceneSentenceEntity entity = new SceneSentenceEntity();
		entity.setId("sentence_reading_" + compactId());
		entity.setSentenceId(item.contentId());
		entity.setSceneId(sceneId);
		entity.setSentence(item.englishText());
		entity.setTranslation(item.chineseText());
		return entity;
	}

	private String compactId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}
