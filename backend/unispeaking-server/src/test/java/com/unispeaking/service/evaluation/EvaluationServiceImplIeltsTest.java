package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import com.unispeaking.infrastructure.persistence.repository.evaluation.IeltsEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.evaluation.impl.EvaluationServiceImpl;
import com.unispeaking.service.scene.SceneFlowService;
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
	void scoresWholeMockTestOnceAndPersistsAgainstFinalSession() {
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
		when(ieltsLlmClient.assessFullTest(
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				eq("7.0"))).thenReturn(new IeltsTextAssessment(
						null,
						new BigDecimal("7.0"),
						new BigDecimal("6.0"),
						new BigDecimal("7.0"),
						"整场表达连贯。",
						List.of("能持续作答"),
						List.of("提高词汇精度"),
						"HIGH"));
		IeltsEvaluationRepository evaluationRepository =
				mock(IeltsEvaluationRepository.class);
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(null)).thenReturn(userId.toString());

		EvaluationServiceImpl service = new EvaluationServiceImpl(
				mock(PronunciationAssessmentClient.class),
				mock(EvaluationLlmClient.class),
				mock(ActiveSessionRegistry.class),
				mock(SceneRepository.class),
				messageRepository,
				turnRepository,
				mock(SessionEvaluationRepository.class),
				mock(SceneSentenceReadingRepository.class),
				practiceRepository,
				mock(SceneFlowService.class),
				sessionRepository,
				evaluationRepository,
				ieltsLlmClient,
				authService);

		var result = service.generateIeltsEvaluation(ieltsId, "session-p3");

		assertEquals("FINAL", result.assessmentType());
		assertEquals(new BigDecimal("7.0"), result.pronunciationScore());
		assertEquals(new BigDecimal("7.0"), result.overallBandScore());
		assertEquals(3, result.partEvaluations().size());
		assertEquals(
				new BigDecimal("7.5"),
				result.partEvaluations().get(1).overallBandScore());
		assertEquals(
				new BigDecimal("7.5"),
				result.partEvaluations().get(2).overallBandScore());
		ArgumentCaptor<com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult>
				captor = ArgumentCaptor.forClass(
						com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult.class);
		verify(evaluationRepository).save(
				eq(ieltsId),
				eq("session-p3"),
				captor.capture());
		assertEquals(result, captor.getValue());

		IeltsEvaluationEntity saved = new IeltsEvaluationEntity();
		saved.setSessionId("session-p3");
		saved.setOverallBandScore(new BigDecimal("7.0"));
		saved.setCreatedAt(OffsetDateTime.parse("2026-08-05T08:00:00Z"));
		when(evaluationRepository.find("session-p3"))
				.thenReturn(Optional.of(saved));
		assertEquals(
				new BigDecimal("7.0"),
				service.getLatestIeltsEstimatedScore());
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
}
