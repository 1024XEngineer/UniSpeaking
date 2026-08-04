package com.unispeaking.infrastructure.persistence.repository.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.scene.InterviewRecord;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.TargetRoleSummary;
import com.unispeaking.infrastructure.persistence.codec.scene.InterviewJsonbCodec;
import com.unispeaking.infrastructure.persistence.entity.scene.InterviewEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.InterviewMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InterviewRepositoryTest {

	private static final String JSON = "{\"overview\":\"role\"}";
	private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(
			2026, 8, 4, 8, 0, 0, 0, ZoneOffset.UTC);
	private static final UUID USER_ID = UUID.fromString(
			"11111111-1111-4111-8111-111111111111");

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(
						new MybatisConfiguration(),
						"interview-repository-test"),
				InterviewEntity.class);
	}

	@Test
	void createsEntityUsingCodecWithoutInventingCompletionMetadata() {
		InterviewMapper mapper = mock(InterviewMapper.class);
		InterviewJsonbCodec codec = mock(InterviewJsonbCodec.class);
		when(codec.encodeRoleSummary(summary())).thenReturn(JSON);
		when(mapper.insert(any(InterviewEntity.class))).thenReturn(1);

		new InterviewRepository(mapper, codec).create(record());

		ArgumentCaptor<InterviewEntity> captor =
				ArgumentCaptor.forClass(InterviewEntity.class);
		verify(mapper).insert(captor.capture());
		InterviewEntity saved = captor.getValue();
		assertEquals("interview_1", saved.getId());
		assertEquals(USER_ID, saved.getUserId());
		assertEquals("session_1", saved.getSessionId());
		assertEquals("Product Manager", saved.getJobTitle());
		assertEquals("STANDARD", saved.getDifficulty());
		assertEquals(JSON, saved.getRoleSummary());
		assertNull(saved.getRecordingObjectKey());
		assertNull(saved.getRecordingDurationSeconds());
		assertNull(saved.getCompletedAt());
		assertEquals(CREATED_AT, saved.getCreatedAt());
		assertEquals(CREATED_AT, saved.getUpdatedAt());
	}

	@Test
	void findsByIdAndMapsEveryDatabaseField() {
		InterviewMapper mapper = mock(InterviewMapper.class);
		InterviewJsonbCodec codec = mock(InterviewJsonbCodec.class);
		InterviewEntity entity = entity();
		when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
		when(codec.decodeRoleSummary(JSON)).thenReturn(summary());

		InterviewRecord found = new InterviewRepository(mapper, codec)
				.findById("interview_1")
				.orElseThrow();

		assertEquals("interviews/recordings/1.mp3",
				found.recordingObjectKey());
		assertEquals(95, found.recordingDurationSeconds());
		assertEquals(InterviewDifficulty.STANDARD, found.difficulty());
		assertEquals(summary(), found.roleSummary());
		ArgumentCaptor<LambdaQueryWrapper<InterviewEntity>> captor =
				queryCaptor();
		verify(mapper).selectOne(captor.capture());
		assertColumns(captor.getValue(), "id");
		assertTrue(captor.getValue().getParamNameValuePairs()
				.values().contains("interview_1"));
	}

	@Test
	void userOwnedLookupIncludesBothInterviewAndUserConditions() {
		InterviewMapper mapper = mock(InterviewMapper.class);
		InterviewJsonbCodec codec = mock(InterviewJsonbCodec.class);
		when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

		assertTrue(new InterviewRepository(mapper, codec)
				.findByIdAndUserId("interview_1", USER_ID)
				.isEmpty());

		ArgumentCaptor<LambdaQueryWrapper<InterviewEntity>> captor =
				queryCaptor();
		verify(mapper).selectOne(captor.capture());
		assertColumns(captor.getValue(), "id", "user_id");
		assertTrue(captor.getValue().getParamNameValuePairs()
				.values().containsAll(List.of("interview_1", USER_ID)));
	}

	@Test
	void completesOnlyAssetMetadataColumnsForRequestedInterview() {
		InterviewMapper mapper = mock(InterviewMapper.class);
		when(mapper.update(isNull(), any(LambdaUpdateWrapper.class)))
				.thenReturn(1);
		OffsetDateTime completedAt = CREATED_AT.plusMinutes(5);

		new InterviewRepository(mapper, mock(InterviewJsonbCodec.class))
				.completeAssetMetadata(
						"interview_1",
						"interviews/recordings/1.mp3",
						95,
						completedAt);

		ArgumentCaptor<LambdaUpdateWrapper<InterviewEntity>> captor =
				updateCaptor();
		verify(mapper).update(isNull(), captor.capture());
		LambdaUpdateWrapper<InterviewEntity> update = captor.getValue();
		assertColumns(update, "id");
		String sqlSet = update.getSqlSet().toLowerCase(Locale.ROOT);
		assertTrue(sqlSet.contains("recording_object_key="));
		assertTrue(sqlSet.contains("recording_duration_seconds="));
		assertTrue(sqlSet.contains("completed_at="));
		assertTrue(sqlSet.contains("updated_at="));
		assertEquals(4, sqlSet.split(",").length);
		assertTrue(update.getParamNameValuePairs().values().containsAll(List.of(
				"interview_1",
				"interviews/recordings/1.mp3",
				95,
				completedAt)));
	}

	@Test
	void physicallyDeletesByWrapperAndTranslatesFailures() {
		InterviewMapper mapper = mock(InterviewMapper.class);
		when(mapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
		InterviewRepository repository = new InterviewRepository(
				mapper,
				mock(InterviewJsonbCodec.class));

		assertEquals(1, repository.deleteById("interview_1"));
		ArgumentCaptor<LambdaQueryWrapper<InterviewEntity>> captor =
				queryCaptor();
		verify(mapper).delete(captor.capture());
		assertColumns(captor.getValue(), "id");

		when(mapper.delete(any(LambdaQueryWrapper.class)))
				.thenThrow(new IllegalStateException("database"));
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> repository.deleteById("interview_1"));
		assertEquals("INTERVIEW_PERSISTENCE_FAILED", exception.code());
	}

	@Test
	void rejectsUnexpectedWriteCountsAndPreservesCodecErrors() {
		InterviewMapper mapper = mock(InterviewMapper.class);
		InterviewJsonbCodec codec = mock(InterviewJsonbCodec.class);
		when(codec.encodeRoleSummary(summary())).thenReturn(JSON);
		when(mapper.insert(any(InterviewEntity.class))).thenReturn(0);
		InterviewRepository repository = new InterviewRepository(mapper, codec);

		assertEquals(
				"INTERVIEW_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.create(record())).code());

		BusinessException invalid = new BusinessException(
				"INTERVIEW_DATA_INVALID",
				"invalid");
		when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity());
		when(codec.decodeRoleSummary(JSON)).thenThrow(invalid);
		assertEquals(
				"INTERVIEW_DATA_INVALID",
				assertThrows(
						BusinessException.class,
						() -> repository.findById("interview_1")).code());

		when(mapper.update(isNull(), any(LambdaUpdateWrapper.class)))
				.thenReturn(0);
		assertEquals(
				"INTERVIEW_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.completeAssetMetadata(
								"missing", "key", 1, CREATED_AT)).code());
	}

	@Test
	void translatesMapperFailuresAcrossCreateUpdateAndRead() {
		InterviewMapper mapper = mock(InterviewMapper.class);
		InterviewJsonbCodec codec = mock(InterviewJsonbCodec.class);
		when(codec.encodeRoleSummary(summary())).thenReturn(JSON);
		InterviewRepository repository = new InterviewRepository(mapper, codec);

		when(mapper.insert(any(InterviewEntity.class)))
				.thenThrow(new IllegalStateException("insert"));
		assertEquals(
				"INTERVIEW_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.create(record())).code());

		when(mapper.update(isNull(), any(LambdaUpdateWrapper.class)))
				.thenThrow(new IllegalStateException("update"));
		assertEquals(
				"INTERVIEW_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.completeAssetMetadata(
								"interview_1", "key", 1, CREATED_AT)).code());

		when(mapper.selectOne(any(LambdaQueryWrapper.class)))
				.thenThrow(new IllegalStateException("select"));
		assertEquals(
				"INTERVIEW_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.findById("interview_1")).code());
	}

	private InterviewRecord record() {
		return new InterviewRecord(
				"interview_1",
				USER_ID,
				"session_1",
				"Product Manager",
				InterviewDifficulty.STANDARD,
				summary(),
				null,
				null,
				null,
				CREATED_AT,
				CREATED_AT);
	}

	private InterviewEntity entity() {
		InterviewEntity entity = new InterviewEntity();
		entity.setId("interview_1");
		entity.setUserId(USER_ID);
		entity.setSessionId("session_1");
		entity.setJobTitle("Product Manager");
		entity.setDifficulty("STANDARD");
		entity.setRoleSummary(JSON);
		entity.setRecordingObjectKey("interviews/recordings/1.mp3");
		entity.setRecordingDurationSeconds(95);
		entity.setCompletedAt(CREATED_AT.plusMinutes(5));
		entity.setCreatedAt(CREATED_AT);
		entity.setUpdatedAt(CREATED_AT.plusMinutes(5));
		return entity;
	}

	private TargetRoleSummary summary() {
		return new TargetRoleSummary(
				"role",
				List.of("plan"),
				List.of("analysis"),
				List.of("experience"));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private ArgumentCaptor<LambdaQueryWrapper<InterviewEntity>> queryCaptor() {
		return ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private ArgumentCaptor<LambdaUpdateWrapper<InterviewEntity>> updateCaptor() {
		return ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
	}

	private void assertColumns(
			com.baomidou.mybatisplus.core.conditions.Wrapper<?> wrapper,
			String... columns) {
		String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
		for (String column : columns) {
			assertTrue(sql.contains(column + " ="), sql);
		}
	}
}
