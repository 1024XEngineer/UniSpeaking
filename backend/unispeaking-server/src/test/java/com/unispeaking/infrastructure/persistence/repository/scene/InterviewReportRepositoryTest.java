package com.unispeaking.infrastructure.persistence.repository.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.scene.InterviewReportRecord;
import com.unispeaking.domain.vo.scene.InterviewReportDimension;
import com.unispeaking.domain.vo.scene.InterviewReportType;
import com.unispeaking.infrastructure.persistence.entity.scene.InterviewReportEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.InterviewReportMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InterviewReportRepositoryTest {

	private static final OffsetDateTime NOW = OffsetDateTime.of(
			2026, 8, 4, 8, 0, 0, 0, ZoneOffset.UTC);

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(
						new MybatisConfiguration(),
						"interview-report-repository-test"),
				InterviewReportEntity.class);
	}

	@Test
	void savesAllFiveReportDimensions() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		when(mapper.insert(any(InterviewReportEntity.class))).thenReturn(1);

		new InterviewReportRepository(mapper).save(report());

		ArgumentCaptor<InterviewReportEntity> captor =
				ArgumentCaptor.forClass(InterviewReportEntity.class);
		verify(mapper).insert(captor.capture());
		InterviewReportEntity saved = captor.getValue();
		assertEquals("FULL", saved.getReportType());
		assertEquals(new BigDecimal("88.5"), saved.getOverallScore());
		assertEquals(new BigDecimal("81.1"), saved.getFluencyScore());
		assertEquals("logic evaluation",
				saved.getLogicCoherenceEvaluation());
		assertEquals("grammar action",
				saved.getGrammarControlActionSuggestion());
		assertEquals(new BigDecimal("84.4"),
				saved.getPronunciationIntelligibilityScore());
		assertEquals("vocabulary action",
				saved.getVocabularyExpressionActionSuggestion());
		assertEquals(NOW, saved.getCreatedAt());
		assertEquals(NOW, saved.getUpdatedAt());
	}

	@Test
	void findsAndMapsCompleteFiveDimensionReport() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		when(mapper.selectOne(any(LambdaQueryWrapper.class)))
				.thenReturn(entity());

		InterviewReportRecord found = new InterviewReportRepository(mapper)
				.findByInterviewId("interview_1")
				.orElseThrow();

		assertEquals(report(), found);
		ArgumentCaptor<LambdaQueryWrapper<InterviewReportEntity>> captor =
				queryCaptor();
		verify(mapper).selectOne(captor.capture());
		assertInterviewCondition(captor.getValue());
	}

	@Test
	void missingReportIsEmptyAndDeleteUsesWrapper() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
		when(mapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
		InterviewReportRepository repository =
				new InterviewReportRepository(mapper);

		assertTrue(repository.findByInterviewId("missing").isEmpty());
		assertEquals(1, repository.deleteByInterviewId("interview_1"));

		ArgumentCaptor<LambdaQueryWrapper<InterviewReportEntity>> captor =
				queryCaptor();
		verify(mapper).delete(captor.capture());
		assertInterviewCondition(captor.getValue());
	}

	@Test
	void translatesUnexpectedWritesAndDatabaseFailures() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		when(mapper.insert(any(InterviewReportEntity.class))).thenReturn(0);
		InterviewReportRepository repository =
				new InterviewReportRepository(mapper);

		assertEquals(
				"INTERVIEW_REPORT_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.save(report())).code());

		when(mapper.selectOne(any(LambdaQueryWrapper.class)))
				.thenThrow(new IllegalStateException("database"));
		assertEquals(
				"INTERVIEW_REPORT_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.findByInterviewId("broken")).code());
	}

	@Test
	void translatesInsertAndDeleteDatabaseFailures() {
		InterviewReportMapper mapper = mock(InterviewReportMapper.class);
		InterviewReportRepository repository =
				new InterviewReportRepository(mapper);
		when(mapper.insert(any(InterviewReportEntity.class)))
				.thenThrow(new IllegalStateException("insert"));

		assertEquals(
				"INTERVIEW_REPORT_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.save(report())).code());

		when(mapper.delete(any(LambdaQueryWrapper.class)))
				.thenThrow(new IllegalStateException("delete"));
		assertEquals(
				"INTERVIEW_REPORT_PERSISTENCE_FAILED",
				assertThrows(
						BusinessException.class,
						() -> repository.deleteByInterviewId(
								"interview_1")).code());
	}

	private void assertInterviewCondition(
			LambdaQueryWrapper<InterviewReportEntity> wrapper) {
		String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
		assertTrue(sql.contains("interview_id ="), sql);
		assertTrue(wrapper.getParamNameValuePairs().values()
				.contains("interview_1"));
	}

	private InterviewReportRecord report() {
		return new InterviewReportRecord(
				"interview_1",
				InterviewReportType.FULL,
				new BigDecimal("88.5"),
				"overall summary",
				dimension("81.1", "fluency"),
				dimension("82.2", "logic"),
				dimension("83.3", "grammar"),
				dimension("84.4", "pronunciation"),
				dimension("85.5", "vocabulary"),
				NOW,
				NOW);
	}

	private InterviewReportDimension dimension(String score, String name) {
		return new InterviewReportDimension(
				new BigDecimal(score),
				name + " evaluation",
				name + " action");
	}

	private InterviewReportEntity entity() {
		InterviewReportRecord report = report();
		InterviewReportEntity entity = new InterviewReportEntity();
		entity.setInterviewId(report.interviewId());
		entity.setReportType(report.reportType().name());
		entity.setOverallScore(report.overallScore());
		entity.setOverallSummary(report.overallSummary());
		entity.setFluencyScore(report.fluency().score());
		entity.setFluencyEvaluation(report.fluency().evaluation());
		entity.setFluencyActionSuggestion(report.fluency().actionSuggestion());
		entity.setLogicCoherenceScore(report.logicCoherence().score());
		entity.setLogicCoherenceEvaluation(report.logicCoherence().evaluation());
		entity.setLogicCoherenceActionSuggestion(
				report.logicCoherence().actionSuggestion());
		entity.setGrammarControlScore(report.grammarControl().score());
		entity.setGrammarControlEvaluation(report.grammarControl().evaluation());
		entity.setGrammarControlActionSuggestion(
				report.grammarControl().actionSuggestion());
		entity.setPronunciationIntelligibilityScore(
				report.pronunciationIntelligibility().score());
		entity.setPronunciationIntelligibilityEvaluation(
				report.pronunciationIntelligibility().evaluation());
		entity.setPronunciationIntelligibilityActionSuggestion(
				report.pronunciationIntelligibility().actionSuggestion());
		entity.setVocabularyExpressionScore(
				report.vocabularyExpression().score());
		entity.setVocabularyExpressionEvaluation(
				report.vocabularyExpression().evaluation());
		entity.setVocabularyExpressionActionSuggestion(
				report.vocabularyExpression().actionSuggestion());
		entity.setCreatedAt(report.createdAt());
		entity.setUpdatedAt(report.updatedAt());
		return entity;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private ArgumentCaptor<LambdaQueryWrapper<InterviewReportEntity>>
			queryCaptor() {
		return ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
	}
}
