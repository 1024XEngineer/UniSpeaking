package com.unispeaking.infrastructure.persistence.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.baomidou.mybatisplus.annotation.TableId;
import com.unispeaking.infrastructure.persistence.entity.evaluation.TurnEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.ScenePhraseEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneSentenceEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.SceneWordEntity;
import com.unispeaking.infrastructure.persistence.entity.session.SessionMessageEntity;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CompositeKeyEntityTest {

	@Test
	void compositeKeyEntitiesMustNotDeclareAFakeSingleColumnTableId() {
		assertCompositeKey(
				SceneWordEntity.class,
				"sceneId",
				"wordId");
		assertCompositeKey(
				ScenePhraseEntity.class,
				"sceneId",
				"phraseId");
		assertCompositeKey(
				SceneSentenceEntity.class,
				"sceneId",
				"sentenceId");
		assertCompositeKey(
				SessionMessageEntity.class,
				"sessionId",
				"messageNo");
		assertCompositeKey(
				TurnEvaluationEntity.class,
				"sessionId",
				"turnNo");
	}

	private void assertCompositeKey(
			Class<?> entityType,
			String firstKey,
			String secondKey) {
		assertAll(
				() -> assertNotNull(
						entityType.getDeclaredField(firstKey)),
				() -> assertNotNull(
						entityType.getDeclaredField(secondKey)),
				() -> assertFalse(
						Arrays.stream(entityType.getDeclaredFields())
								.anyMatch(this::isTableId),
						() -> entityType.getSimpleName()
								+ " must be addressed by both key columns"));
	}

	private boolean isTableId(Field field) {
		return field.isAnnotationPresent(TableId.class);
	}
}
