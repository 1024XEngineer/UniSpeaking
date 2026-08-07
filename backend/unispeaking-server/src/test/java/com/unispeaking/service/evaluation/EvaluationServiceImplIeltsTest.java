package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.evaluation.model.IeltsTextAssessment;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.evaluation.client.EvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.IeltsEvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.PronunciationAssessmentClient;
import com.unispeaking.infrastructure.persistence.entity.evaluation.CustomTurnEvaluation;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsPartEvaluationEntity;
import com.unispeaking.infrastructure.persistence.repository.evaluation.IeltsEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.component.evaluation.EvaluationProcessor;
import com.unispeaking.service.scene.impl.IeltsSceneFlowServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EvaluationServiceImplIeltsTest {

	@Test
	void reusesCompletedPartScoresAndOnlyScoresMissingPartBeforeFinalReport() {
		UUID userId = UUID.fromString("3d8f80be-6390-4db9-a6cf-c10a0145d4c3");
		String ieltsId = "ielts_mock_1";
		IeltsPracticeRepository practiceRepository =
				mock(IeltsPracticeRepository.class);
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(
						ieltsId,
						userId,
						IeltsMode.MOCK_TEST,
						null,
						null,
						"RANDOM",
						"topic-p1",
						"topic-p2",
						"topic-p3",
						new IeltsContent(List.of(), List.of(), List.of()))));
		PracticeSessionRepository sessionRepository =
				mock(PracticeSessionRepository.class);
		List<PracticeSessionRecord> sessions = List.of(
				session("session-p1", userId, ieltsId, 0),
				session("session-p2", userId, ieltsId, 1),
				session("session-p3", userId, ieltsId, 2));
		when(sessionRepository.findBySceneId(ieltsId)).thenReturn(sessions);
		when(sessionRepository.findCompletedByUserAndSceneType(
				userId,
				SceneType.IELTS_SCENE)).thenReturn(sessions);

		SessionMessageRepository messageRepository =
				mock(SessionMessageRepository.class);
		when(messageRepository.findMessages("session-p1")).thenReturn(
				List.of(new Message(0, "Examiner question", null)));
		for (PracticeSessionRecord session : sessions.subList(1, sessions.size())) {
			when(messageRepository.findMessages(session.sessionId())).thenReturn(
					List.of(
							new Message(0, "Examiner question", null),
							new Message(1, "Candidate answer with enough detail", null)));
		}
		TurnEvaluationRepository turnRepository =
				mock(TurnEvaluationRepository.class);
		when(turnRepository.findAll("session-p1")).thenReturn(List.of());
		for (PracticeSessionRecord session : sessions.subList(1, sessions.size())) {
			when(turnRepository.findAll(session.sessionId()))
					.thenReturn(List.of(scorableTurn(
							ieltsId,
							session.sessionId())));
		}

		IeltsEvaluationLlmClient ieltsLlmClient =
				mock(IeltsEvaluationLlmClient.class);
		when(ieltsLlmClient.assessPart(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class),
				org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(new IeltsTextAssessment(
						null,
						new BigDecimal("7.0"),
						new BigDecimal("6.0"),
						new BigDecimal("7.0"),
						"回答能够持续展开，衔接基本清楚。",
						"能够使用话题词汇，但词汇精度有限。",
						"简单句控制稳定，并尝试使用复杂句。",
						"单项表达基本完整。",
						List.of("能够作答"),
						List.of("增加细节"),
						"HIGH"));
		when(ieltsLlmClient.assessFullTest(
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				eq("7.0"))).thenReturn(new IeltsTextAssessment(
						null,
						new BigDecimal("7.0"),
						new BigDecimal("6.0"),
						new BigDecimal("7.0"),
						"三个 Part 均能保持基本连贯。",
						"跨话题词汇够用，但精度不稳定。",
						"能使用不同句式，偶有控制错误。",
						"整场表达连贯。",
						List.of("能持续作答"),
						List.of("提高词汇精度"),
						"HIGH"));
		IeltsEvaluationRepository evaluationRepository =
				mock(IeltsEvaluationRepository.class);
		when(evaluationRepository.findPart("session-p1")).thenReturn(Optional.of(
				savedPart(
						ieltsId,
						"session-p1",
						com.unispeaking.domain.vo.scene.IeltsPart.PART_1,
						"6.5")));
		when(evaluationRepository.findPart("session-p2")).thenReturn(Optional.of(
				savedPart(
						ieltsId,
						"session-p2",
						com.unispeaking.domain.vo.scene.IeltsPart.PART_2,
						"6.5")));
		when(evaluationRepository.findPart("session-p3"))
				.thenReturn(Optional.empty());
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		var topicRepository = mock(
				com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository.class);
		when(topicRepository.findTopicsByIds(any())).thenReturn(List.of(
				new com.unispeaking.domain.po.scene.IeltsTopic(
						"topic-p1", "Work or Studies",
						com.unispeaking.domain.vo.scene.IeltsTopicType.PART_1_POOL,
						"REQUIRED", "XDF", "READY"),
				new com.unispeaking.domain.po.scene.IeltsTopic(
						"topic-p2", "A useful object",
						com.unispeaking.domain.vo.scene.IeltsTopicType.PART_2_3_BUNDLE,
						"OBJECT", "XDF", "READY"),
				new com.unispeaking.domain.po.scene.IeltsTopic(
						"topic-p3", "Technology and society",
						com.unispeaking.domain.vo.scene.IeltsTopicType.PART_2_3_BUNDLE,
						"ABSTRACT", "XDF", "READY")));

		EvaluationProcessor service = new EvaluationProcessor(
				mock(PronunciationAssessmentClient.class),
				mock(EvaluationLlmClient.class),
				mock(ActiveSessionRegistry.class),
				mock(SceneRepository.class),
				messageRepository,
				turnRepository,
				mock(SessionEvaluationRepository.class),
				mock(SceneSentenceReadingRepository.class),
				practiceRepository,
				topicRepository,
				mock(IeltsSceneFlowServiceImpl.class),
				sessionRepository,
				evaluationRepository,
				ieltsLlmClient,
				authService,
				mock(com.unispeaking.provider.ObjectStorageProvider.class),
				new com.unispeaking.infrastructure.config.ObjectStorageProperties(),
				mock(com.unispeaking.component.recording.IeltsRecordingStore.class));

		var result = service.generateIeltsEvaluation(ieltsId, "session-p3");

		assertEquals("FINAL", result.assessmentType());
		assertEquals(new BigDecimal("7.0"), result.pronunciationScore());
		assertEquals(new BigDecimal("7.0"), result.overallBandScore());
		assertEquals(
				"三个 Part 均能保持基本连贯。",
				result.fluencyCoherenceReason());
		assertEquals(
				"基于本次 2 轮有效原始语音，音频模型的平均发音得分为 80.0/100，按 9 分制折算为 7.0。",
				result.pronunciationReason());
		assertEquals(3, result.partEvaluations().size());
		assertEquals(
				new BigDecimal("6.5"),
				result.partEvaluations().get(1).lexicalResourceScore());
		assertEquals(
				new BigDecimal("6.0"),
				result.partEvaluations().get(2).lexicalResourceScore());
		verify(ieltsLlmClient, times(1)).assessPart(
				eq(com.unispeaking.domain.vo.scene.IeltsPart.PART_3),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.nullable(String.class),
				org.mockito.ArgumentMatchers.anyString());
		verify(ieltsLlmClient, times(1)).assessFullTest(
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				eq("7.0"));
		ArgumentCaptor<com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult>
				captor = ArgumentCaptor.forClass(
						com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult.class);
		verify(evaluationRepository).saveFinal(eq(ieltsId), captor.capture());
		assertEquals(result, captor.getValue());
		verify(evaluationRepository).savePart(
				eq(ieltsId),
				eq("session-p3"),
				any());

		IeltsEvaluationEntity saved = new IeltsEvaluationEntity();
		saved.setEvaluationId("ielts_mock_" + ieltsId);
		saved.setIeltsId(ieltsId);
		saved.setOverallBandScore(new BigDecimal("7.0"));
		saved.setFluencyCoherenceScore(new BigDecimal("7.0"));
		saved.setCreatedAt(OffsetDateTime.parse("2026-08-05T08:00:00Z"));
		when(evaluationRepository.findFinal(ieltsId))
				.thenReturn(Optional.of(saved));
		assertEquals(
				new BigDecimal("7.0"),
				service.getLatestIeltsEstimatedScore());
		assertEquals(
				"A useful object",
				service.getIeltsEvaluationHistory().getFirst()
						.topicTitles().get(
								com.unispeaking.domain.vo.scene.IeltsPart.PART_2));
	}

	private PracticeSessionRecord session(
			String sessionId,
			UUID userId,
			String ieltsId,
			int minute) {
		Instant startedAt = Instant.parse("2026-08-04T08:00:00Z")
				.plusSeconds(minute * 60L);
		return new PracticeSessionRecord(
				sessionId,
				userId,
				ieltsId,
				SceneType.IELTS_SCENE,
				SessionStatus.COMPLETED,
				startedAt,
				startedAt.plusSeconds(60));
	}

	private CustomTurnEvaluation scorableTurn(
			String sceneId,
			String sessionId) {
		return new CustomTurnEvaluation(
				sceneId,
				sessionId,
				1,
				"Candidate answer with enough detail",
				new BigDecimal("82"),
				new BigDecimal("78"),
				new BigDecimal("76"),
				new BigDecimal("85"),
				new BigDecimal("80"),
				new BigDecimal("79"),
				"表达清楚",
				"A more natural expression.",
				List.of());
	}

	private IeltsPartEvaluationEntity savedPart(
			String ieltsId,
			String sessionId,
			com.unispeaking.domain.vo.scene.IeltsPart part,
			String lexicalScore) {
		IeltsPartEvaluationEntity entity = new IeltsPartEvaluationEntity();
		entity.setPartEvaluationId("ielts_part_" + sessionId);
		entity.setIeltsId(ieltsId);
		entity.setSessionId(sessionId);
		entity.setPart(part.name());
		entity.setFluencyCoherenceScore(new BigDecimal("7.0"));
		entity.setLexicalResourceScore(new BigDecimal(lexicalScore));
		entity.setGrammaticalRangeAccuracyScore(new BigDecimal("6.5"));
		entity.setPronunciationScore(new BigDecimal("7.0"));
		entity.setSummary("已缓存的单 Part 评分");
		entity.setStrengths(new String[]{"能够持续作答"});
		entity.setImprovements(new String[]{"提高表达准确性"});
		entity.setRecommendedExpressions(new String[]{"A clearer expression."});
		entity.setEvaluationStatus("COMPLETED");
		return entity;
	}
}
