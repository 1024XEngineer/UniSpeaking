package com.unispeaking.infrastructure.persistence.repository.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.scene.InterviewQuestionRecord;
import com.unispeaking.domain.vo.scene.InterviewQuestionType;
import com.unispeaking.infrastructure.persistence.entity.scene.InterviewQuestionEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.InterviewQuestionMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InterviewQuestionRepositoryTest {

	private static final OffsetDateTime NOW = OffsetDateTime.of(
			2026, 8, 4, 8, 0, 0, 0, ZoneOffset.UTC);

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(
						new MybatisConfiguration(),
						"interview-question-repository-test"),
				InterviewQuestionEntity.class);
	}

	@Test
	void batchSavesMappedQuestionsAndTreatsEmptyInputAsNoOp() {
		InterviewQuestionMapper mapper = mock(InterviewQuestionMapper.class);
		when(mapper.insert(anyList())).thenReturn(List.of());
		InterviewQuestionRepository repository =
				new InterviewQuestionRepository(mapper);

		repository.saveAll(List.of(
				record(2, InterviewQuestionType.FOLLOW_UP),
				record(1, InterviewQuestionType.MAIN)));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<InterviewQuestionEntity>> captor =
				ArgumentCaptor.forClass(List.class);
		verify(mapper).insert(captor.capture());
		assertEquals(List.of(2, 1), captor.getValue().stream()
				.map(InterviewQuestionEntity::getQuestionNo)
				.toList());
		assertEquals("FOLLOW_UP",
				captor.getValue().getFirst().getQuestionType());
		assertEquals(NOW, captor.getValue().getFirst().getCreatedAt());

		InterviewQuestionMapper emptyMapper = mock(
				InterviewQuestionMapper.class);
		new InterviewQuestionRepository(emptyMapper).saveAll(List.of());
		verify(emptyMapper, never()).insert(anyList());
	}

	@Test
	void listsQuestionsWithInterviewFilterAndQuestionNumberOrdering() {
		InterviewQuestionMapper mapper = mock(InterviewQuestionMapper.class);
		when(mapper.selectList(any(LambdaQueryWrapper.class)))
				.thenReturn(List.of(entity(1, "MAIN"), entity(2, "FOLLOW_UP")));

		List<InterviewQuestionRecord> found =
				new InterviewQuestionRepository(mapper)
						.findByInterviewId("interview_1");

		assertEquals(List.of(1, 2), found.stream()
				.map(InterviewQuestionRecord::questionNo)
				.toList());
		assertEquals(InterviewQuestionType.FOLLOW_UP,
				found.get(1).questionType());
		ArgumentCaptor<LambdaQueryWrapper<InterviewQuestionEntity>> captor =
				queryCaptor();
		verify(mapper).selectList(captor.capture());
		String sql = captor.getValue().getSqlSegment()
				.toLowerCase(Locale.ROOT);
		assertTrue(sql.contains("interview_id ="), sql);
		assertTrue(sql.contains("order by question_no asc"), sql);
		assertEquals(List.of("interview_1"),
				List.copyOf(captor.getValue()
						.getParamNameValuePairs().values()));
	}

	@Test
	void completeCompositeKeyIsUsedForSingleLookup() {
		InterviewQuestionMapper mapper = mock(InterviewQuestionMapper.class);
		when(mapper.selectOne(any(LambdaQueryWrapper.class)))
				.thenReturn(entity(2, "FOLLOW_UP"));

		InterviewQuestionRecord found = new InterviewQuestionRepository(mapper)
				.findByKey("interview_1", 2)
				.orElseThrow();

		assertEquals(2, found.questionNo());
		ArgumentCaptor<LambdaQueryWrapper<InterviewQuestionEntity>> captor =
				queryCaptor();
		verify(mapper).selectOne(captor.capture());
		assertCompleteKey(captor.getValue(), 2);
	}

	@Test
	void completeCompositeKeyIsUsedForSingleDelete() {
		InterviewQuestionMapper mapper = mock(InterviewQuestionMapper.class);
		when(mapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

		assertEquals(
				1,
				new InterviewQuestionRepository(mapper)
						.deleteByKey("interview_1", 2));

		ArgumentCaptor<LambdaQueryWrapper<InterviewQuestionEntity>> captor =
				queryCaptor();
		verify(mapper).delete(captor.capture());
		assertCompleteKey(captor.getValue(), 2);
	}

	@Test
	void bulkDeleteUsesInterviewFilterAndMissingLookupIsEmpty() {
		InterviewQuestionMapper mapper = mock(InterviewQuestionMapper.class);
		when(mapper.delete(any(LambdaQueryWrapper.class))).thenReturn(2);
		when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
		InterviewQuestionRepository repository =
				new InterviewQuestionRepository(mapper);

		assertEquals(2, repository.deleteByInterviewId("interview_1"));
		assertTrue(repository.findByKey("interview_1", 99).isEmpty());

		ArgumentCaptor<LambdaQueryWrapper<InterviewQuestionEntity>> captor =
				queryCaptor();
		verify(mapper).delete(captor.capture());
		String sql = captor.getValue().getSqlSegment()
				.toLowerCase(Locale.ROOT);
		assertTrue(sql.contains("interview_id ="), sql);
		assertEquals(List.of("interview_1"),
				List.copyOf(captor.getValue()
						.getParamNameValuePairs().values()));
	}

	@Test
	void rejectsNullBatchAndTranslatesMapperFailures() {
		InterviewQuestionMapper mapper = mock(InterviewQuestionMapper.class);
		InterviewQuestionRepository repository =
				new InterviewQuestionRepository(mapper);

		assertThrows(NullPointerException.class, () -> repository.saveAll(null));
		when(mapper.selectList(any(LambdaQueryWrapper.class)))
				.thenThrow(new IllegalStateException("database"));
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> repository.findByInterviewId("interview_1"));
		assertEquals("INTERVIEW_QUESTION_PERSISTENCE_FAILED", exception.code());
	}

	@Test
	void translatesBatchLookupAndDeleteFailures() {
		InterviewQuestionMapper mapper = mock(InterviewQuestionMapper.class);
		InterviewQuestionRepository repository =
				new InterviewQuestionRepository(mapper);

		when(mapper.insert(anyList()))
				.thenThrow(new IllegalStateException("batch"));
		assertEquals(
				"INTERVIEW_QUESTION_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.saveAll(List.of(record(
								1,
								InterviewQuestionType.MAIN)))).code());

		when(mapper.selectOne(any(LambdaQueryWrapper.class)))
				.thenThrow(new IllegalStateException("select"));
		assertEquals(
				"INTERVIEW_QUESTION_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.findByKey("interview_1", 1)).code());

		when(mapper.delete(any(LambdaQueryWrapper.class)))
				.thenThrow(new IllegalStateException("delete"));
		assertEquals(
				"INTERVIEW_QUESTION_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.deleteByKey("interview_1", 1)).code());
		assertEquals(
				"INTERVIEW_QUESTION_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.deleteByInterviewId(
								"interview_1")).code());
	}

	private void assertCompleteKey(
			LambdaQueryWrapper<InterviewQuestionEntity> wrapper,
			int questionNo) {
		String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
		assertTrue(sql.contains("interview_id ="), sql);
		assertTrue(sql.contains("question_no ="), sql);
		assertTrue(wrapper.getParamNameValuePairs().values()
				.containsAll(List.of("interview_1", questionNo)));
	}

	private InterviewQuestionRecord record(
			int questionNo,
			InterviewQuestionType type) {
		return new InterviewQuestionRecord(
				"interview_1",
				questionNo,
				type,
				"Question " + questionNo,
				NOW,
				NOW);
	}

	private InterviewQuestionEntity entity(int questionNo, String type) {
		InterviewQuestionEntity entity = new InterviewQuestionEntity();
		entity.setInterviewId("interview_1");
		entity.setQuestionNo(questionNo);
		entity.setQuestionType(type);
		entity.setQuestionText("Question " + questionNo);
		entity.setCreatedAt(NOW);
		entity.setUpdatedAt(NOW);
		return entity;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private ArgumentCaptor<LambdaQueryWrapper<InterviewQuestionEntity>>
			queryCaptor() {
		return ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
	}
}
