package com.unispeaking.component.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.common.evaluation.model.IeltsTextAssessment;
import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsContentQuestion;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.config.ObjectStorageProperties;
import com.unispeaking.infrastructure.evaluation.client.EvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.IeltsEvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.PronunciationAssessmentClient;
import com.unispeaking.infrastructure.persistence.repository.evaluation.IeltsEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.provider.ObjectStorageProvider;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class EvaluationProcessorIeltsParallelTest {

	@Test
	void scoresThreeMissingMockPartsInParallel() {
		String userId = "3d8f80be-6390-4db9-a6cf-c10a0145d4c3";
		String ieltsId = "ielts_mock_parallel";
		List<PracticeSessionRecord> sessions = List.of(
				session("part-1", userId, ieltsId, 1),
				session("part-2", userId, ieltsId, 2),
				session("part-3", userId, ieltsId, 3));
		IeltsPracticeRepository practiceRepository = mock(IeltsPracticeRepository.class);
		when(practiceRepository.findPractice(ieltsId)).thenReturn(Optional.of(
				new IeltsPracticeRecord(
						ieltsId,
						UUID.fromString(userId),
						IeltsMode.MOCK_TEST,
						null,
						null,
						"RANDOM",
						"topic-1",
						"topic-2",
						"topic-3",
						new IeltsContent(
								List.of(),
								List.of(new IeltsContentQuestion(
										"Describe a useful object.",
										List.of("what it is"),
										List.of())),
								List.of()))));
		PracticeSessionRepository sessionRepository = mock(PracticeSessionRepository.class);
		when(sessionRepository.findBySceneId(ieltsId)).thenReturn(sessions);
		SessionMessageRepository messageRepository = mock(SessionMessageRepository.class);
		for (PracticeSessionRecord session : sessions) {
			when(messageRepository.findMessages(session.sessionId())).thenReturn(List.of(
					new Message(0, "Examiner question", null),
					new Message(1, "Candidate answer with sufficient detail", null)));
		}
		TurnEvaluationRepository turnRepository = mock(TurnEvaluationRepository.class);
		when(turnRepository.findAll(anyString())).thenReturn(List.of());
		IeltsEvaluationRepository evaluationRepository = mock(IeltsEvaluationRepository.class);
		when(evaluationRepository.findFinal(ieltsId)).thenReturn(Optional.empty());
		when(evaluationRepository.findPart(anyString())).thenReturn(Optional.empty());

		CountDownLatch allPartsStarted = new CountDownLatch(3);
		IeltsEvaluationLlmClient ieltsLlmClient = mock(IeltsEvaluationLlmClient.class);
		when(ieltsLlmClient.assessPart(
				any(),
				anyString(),
				nullable(String.class),
				nullable(String.class))).thenAnswer(invocation -> {
			allPartsStarted.countDown();
			assertTrue(allPartsStarted.await(2, TimeUnit.SECONDS));
			return assessment();
		});
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(null)).thenReturn(userId);
		EvaluationProcessor processor = new EvaluationProcessor(
				mock(PronunciationAssessmentClient.class),
				mock(EvaluationLlmClient.class),
				mock(ActiveSessionRegistry.class),
				mock(SceneRepository.class),
				messageRepository,
				turnRepository,
				mock(SessionEvaluationRepository.class),
				mock(SceneSentenceReadingRepository.class),
				practiceRepository,
				mock(IeltsRepository.class),
				mock(IeltsSceneFlowService.class),
				sessionRepository,
				evaluationRepository,
				ieltsLlmClient,
				authService,
				mock(ObjectStorageProvider.class),
				new ObjectStorageProperties(),
				mock(RecordingStore.class));
		ExecutorService partExecutor = Executors.newFixedThreadPool(3);
		processor.configureIeltsPartEvaluationExecutor(partExecutor);

		try {
			var result = processor.generateIeltsEvaluationForUser(
					ieltsId,
					"part-3",
					userId);
			assertEquals("FINAL", result.assessmentType());
			assertEquals(3, result.partEvaluations().size());
			assertEquals(new BigDecimal("6.5"), result.overallBandScore());
		}
		finally {
			partExecutor.shutdownNow();
		}
	}

	private PracticeSessionRecord session(
			String sessionId,
			String userId,
			String ieltsId,
			int minute) {
		return new PracticeSessionRecord(
				sessionId,
				UUID.fromString(userId),
				ieltsId,
				SceneType.IELTS_SCENE,
				SessionStatus.COMPLETED,
				Instant.parse("2026-08-24T01:0" + minute + ":00Z"),
				Instant.parse("2026-08-24T01:1" + minute + ":00Z"));
	}

	private IeltsTextAssessment assessment() {
		return new IeltsTextAssessment(
				null,
				new BigDecimal("6.5"),
				new BigDecimal("6.5"),
				new BigDecimal("6.5"),
				"Fluency reason",
				"Lexical reason",
				"Grammar reason",
				"Part diagnostic",
				List.of("Clear answer"),
				List.of("Add detail"),
				"HIGH");
	}
}
