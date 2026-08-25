package com.unispeaking.infrastructure.persistence.repository.scene;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.common.persistence.typehandler.PostgresJsonbStringTypeHandler;
import com.unispeaking.infrastructure.persistence.entity.scene.CustomSceneGenerationTaskEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.CustomSceneGenerationTaskMapper;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomSceneGenerationTaskRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(new MybatisConfiguration(), "custom-scene-task-test"),
				CustomSceneGenerationTaskEntity.class);
	}

	@Test
	void bindsCompletedResultAsPostgresJsonb() {
		CustomSceneGenerationTaskMapper mapper = mock(CustomSceneGenerationTaskMapper.class);
		when(mapper.update(isNull(), any())).thenReturn(1);
		CustomSceneGenerationTaskRepository repository =
				new CustomSceneGenerationTaskRepository(mapper);

		repository.markCompleted(UUID.randomUUID(), "{\"sceneId\":\"custom_1\"}");

		@SuppressWarnings("rawtypes")
		ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
		verify(mapper).update(isNull(), wrapper.capture());
		@SuppressWarnings("unchecked")
		LambdaUpdateWrapper<CustomSceneGenerationTaskEntity> update =
				(LambdaUpdateWrapper<CustomSceneGenerationTaskEntity>) wrapper.getValue();
		assertTrue(update.getSqlSet().contains(
				"typeHandler=" + PostgresJsonbStringTypeHandler.class.getName()));
	}
}
