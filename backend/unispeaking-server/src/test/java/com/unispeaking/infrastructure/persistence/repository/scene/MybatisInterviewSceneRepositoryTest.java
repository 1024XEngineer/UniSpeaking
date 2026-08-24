package com.unispeaking.infrastructure.persistence.repository.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.domain.po.scene.InterviewSceneDefinition;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.infrastructure.persistence.entity.scene.InterviewSceneEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.InterviewSceneMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MybatisInterviewSceneRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(
						new MybatisConfiguration(),
						"mybatis-interview-scene-repository-test"),
				InterviewSceneEntity.class);
	}

	@Test
	void saveMapsDefinitionToEntityAndInserts() {
		InterviewSceneMapper mapper = mock(InterviewSceneMapper.class);
		InterviewSceneRepository repository = new MybatisInterviewSceneRepository(mapper);

		repository.save(definition());

		ArgumentCaptor<InterviewSceneEntity> entity =
				ArgumentCaptor.forClass(InterviewSceneEntity.class);
		verify(mapper).insert(entity.capture());
		InterviewSceneEntity saved = entity.getValue();
		assertEquals("interview_abc", saved.getSceneId());
		assertEquals(
				UUID.fromString("11111111-1111-4111-8111-111111111111"),
				saved.getUserId());
		assertEquals("{\"jobTitle\":\"后端\"}", saved.getConfirmedMaterial());
		assertEquals("展示文本", saved.getFinalText());
		assertEquals("{\"topics\":[]}", saved.getInterviewContext());
		assertEquals("STANDARD", saved.getDifficulty());
		assertEquals("systemPrompt", saved.getScenePrompt());
	}

	@Test
	void findByIdReturnsEmptyForMissingOrSoftDeletedScene() {
		InterviewSceneMapper mapper = mock(InterviewSceneMapper.class);
		InterviewSceneRepository repository = new MybatisInterviewSceneRepository(mapper);
		InterviewSceneEntity deleted = new InterviewSceneEntity();
		deleted.setSceneId("interview_deleted");
		deleted.setDeletedAt(OffsetDateTime.now());
		when(mapper.selectById("missing")).thenReturn(null);
		when(mapper.selectById("interview_deleted")).thenReturn(deleted);

		assertTrue(repository.findById("missing").isEmpty());
		assertTrue(repository.findById("interview_deleted").isEmpty());
	}

	@Test
	void findByIdConvertsActiveEntityToDefinition() {
		InterviewSceneMapper mapper = mock(InterviewSceneMapper.class);
		InterviewSceneRepository repository = new MybatisInterviewSceneRepository(mapper);
		when(mapper.selectById("interview_active")).thenReturn(entity());

		InterviewSceneDefinition definition = repository.findById("interview_active")
				.orElseThrow();

		assertEquals("interview_active", definition.sceneId());
		assertEquals("11111111-1111-4111-8111-111111111111", definition.userId());
		assertEquals(InterviewDifficulty.HARD, definition.difficulty());
		assertEquals("展示文本", definition.finalText());
	}

	@Test
	void findByUserIdIgnoresInvalidUuidAndMapsOwnedScenes() {
		InterviewSceneMapper mapper = mock(InterviewSceneMapper.class);
		InterviewSceneRepository repository = new MybatisInterviewSceneRepository(mapper);
		when(mapper.selectList(any())).thenReturn(List.of(entity()));

		assertTrue(repository.findByUserId("not-a-uuid").isEmpty());
		List<InterviewSceneDefinition> owned = repository.findByUserId(
				"11111111-1111-4111-8111-111111111111");

		assertEquals(1, owned.size());
		assertEquals("interview_active", owned.getFirst().sceneId());
		verify(mapper).selectList(any());
	}

	@Test
	void findOwnedByIdFiltersByOwnerAndSoftDelete() {
		InterviewSceneMapper mapper = mock(InterviewSceneMapper.class);
		InterviewSceneRepository repository = new MybatisInterviewSceneRepository(mapper);
		when(mapper.selectOne(any())).thenReturn(entity());

		InterviewSceneDefinition definition = repository.findOwnedById(
				"interview_active",
				"11111111-1111-4111-8111-111111111111").orElseThrow();

		assertEquals("interview_active", definition.sceneId());
		assertTrue(repository.findOwnedById(
				"interview_active",
				"not-a-uuid").isEmpty());
	}

	@Test
	void softDeleteValidatesOwnerAndReturnsUpdateResult() {
		InterviewSceneMapper mapper = mock(InterviewSceneMapper.class);
		InterviewSceneRepository repository = new MybatisInterviewSceneRepository(mapper);
		when(mapper.update(any(), any())).thenReturn(1, 0);

		assertTrue(repository.softDelete(
				"interview_active", "11111111-1111-4111-8111-111111111111"));
		assertTrue(!repository.softDelete(
				"interview_active", "11111111-1111-4111-8111-111111111111"));
		assertTrue(!repository.softDelete("interview_active", "not-a-uuid"));
	}

	@Test
	void wrapsSaveAndDeleteMapperFailures() {
		InterviewSceneMapper mapper = mock(InterviewSceneMapper.class);
		InterviewSceneRepository repository = new MybatisInterviewSceneRepository(mapper);
		when(mapper.insert((InterviewSceneEntity) any()))
				.thenThrow(new IllegalStateException("db down"));
		BusinessException saveFailure = assertThrows(BusinessException.class,
				() -> repository.save(definition()));
		assertEquals("INTERVIEW_SCENE_PERSISTENCE_FAILED", saveFailure.code());

		when(mapper.update(any(), any())).thenThrow(new IllegalStateException("db down"));
		BusinessException deleteFailure = assertThrows(BusinessException.class,
				() -> repository.softDelete(
						"interview_active", "11111111-1111-4111-8111-111111111111"));
		assertEquals("INTERVIEW_SCENE_PERSISTENCE_FAILED", deleteFailure.code());
	}

	@Test
	void mapsNullableDifficultyAndReturnsEmptyWhenOwnedSceneIsMissing() {
		InterviewSceneMapper mapper = mock(InterviewSceneMapper.class);
		InterviewSceneRepository repository = new MybatisInterviewSceneRepository(mapper);
		InterviewSceneEntity entity = entity();
		entity.setDifficulty(null);
		when(mapper.selectOne(any())).thenReturn(null);
		assertTrue(repository.findOwnedById(
				"interview_active", "11111111-1111-4111-8111-111111111111").isEmpty());
		when(mapper.selectById("nullable")).thenReturn(entity);
		assertTrue(repository.findById("nullable").orElseThrow().difficulty() == null);
	}

	private InterviewSceneDefinition definition() {
		return new InterviewSceneDefinition(
				"interview_abc",
				"11111111-1111-4111-8111-111111111111",
				"{\"jobTitle\":\"后端\"}",
				"展示文本",
				"{\"topics\":[]}",
				InterviewDifficulty.STANDARD,
				"systemPrompt",
				OffsetDateTime.parse("2026-08-09T00:00:00Z"),
				OffsetDateTime.parse("2026-08-09T00:00:00Z"),
				null);
	}

	private InterviewSceneEntity entity() {
		InterviewSceneEntity entity = new InterviewSceneEntity();
		entity.setSceneId("interview_active");
		entity.setUserId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
		entity.setConfirmedMaterial("{\"jobTitle\":\"后端\"}");
		entity.setFinalText("展示文本");
		entity.setInterviewContext("{\"topics\":[]}");
		entity.setDifficulty("HARD");
		entity.setScenePrompt("systemPrompt");
		entity.setCreatedAt(OffsetDateTime.parse("2026-08-09T00:00:00Z"));
		entity.setUpdatedAt(OffsetDateTime.parse("2026-08-09T00:00:00Z"));
		return entity;
	}
}
