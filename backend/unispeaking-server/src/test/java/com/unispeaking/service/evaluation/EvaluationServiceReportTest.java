package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.entity.evaluation.CustomTurnEvaluation;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.IeltsEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.component.evaluation.EvaluationProcessor;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.infrastructure.evaluation.client.EvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.IeltsEvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.PronunciationAssessmentClient;
import com.unispeaking.component.session.ActiveSessionRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EvaluationServiceReportTest {

	@Test
	void returnsPersistedReportWithoutReevaluatingTheDialogue() {
		String sessionId = "scene_cached_report";
		CustomSceneSession session = new CustomSceneSession(sessionId, "user_1");
		session.setSceneId("custom_2001");
		session.setSceneType(SceneType.CUSTOM_SCENE);
		ActiveSessionRegistry runtimeStore = mock(ActiveSessionRegistry.class);
		when(runtimeStore.findById(sessionId)).thenReturn(Optional.of(session));
		SessionEvaluationRepository reportRepository =
				mock(SessionEvaluationRepository.class);
		var savedReport = new com.unispeaking.domain.dto.evaluation.DialogueReportResult(
				new BigDecimal("81"), new BigDecimal("82"), new BigDecimal("83"),
				new BigDecimal("84"), new BigDecimal("85"), new BigDecimal("83"),
				"已缓存的报告", List.of("表达清晰"), List.of("补充细节"));
		when(reportRepository.find(sessionId)).thenReturn(Optional.of(savedReport));
		TurnEvaluationRepository turnRepository = mock(TurnEvaluationRepository.class);
		EvaluationLlmClient llmClient = mock(EvaluationLlmClient.class);

		EvaluationProcessor service = new EvaluationProcessor(
				mock(PronunciationAssessmentClient.class), llmClient, runtimeStore,
				mock(SceneRepository.class), mock(SessionMessageRepository.class), turnRepository,
				reportRepository, mock(SceneSentenceReadingRepository.class),
				mock(IeltsPracticeRepository.class),
				mock(com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository.class),
				mock(IeltsSceneFlowService.class), mock(PracticeSessionRepository.class),
				mock(IeltsEvaluationRepository.class), mock(IeltsEvaluationLlmClient.class),
				mock(AuthService.class), mock(com.unispeaking.provider.ObjectStorageProvider.class),
				new com.unispeaking.infrastructure.config.ObjectStorageProperties(),
				mock(com.unispeaking.component.recording.RecordingStore.class));

		assertSame(savedReport, service.generateDialogueReport(sessionId, List.of(
				new Message(0, "How can I help you?", null),
				new Message(1, "I would like a coffee, please.", null))));
		verifyNoInteractions(turnRepository, llmClient);
	}

	@Test
	void allZeroSpeechResultIsNotAggregatedIntoAZeroScoreReport() {
		String sessionId = "scene_zero_score";
		CustomSceneSession session = new CustomSceneSession(sessionId, "user_1");
		session.setSceneId("custom_2001");
		session.setSceneType(SceneType.CUSTOM_SCENE);

		ActiveSessionRegistry runtimeStore = mock(ActiveSessionRegistry.class);
		when(runtimeStore.findById(sessionId)).thenReturn(Optional.of(session));
		List<Message> dialogue = List.of(
				new Message(0, "What is your name?", null),
				new Message(1, "My name is Smith.", null));
		SessionMessageRepository messageRepository =
				mock(SessionMessageRepository.class);
		when(messageRepository.findMessages(sessionId)).thenReturn(dialogue);
		TurnEvaluationRepository turnRepository =
				mock(TurnEvaluationRepository.class);
		when(turnRepository.findAll(sessionId)).thenReturn(List.of(
				new CustomTurnEvaluation(
						"custom_2001",
						sessionId,
						1,
						"My name is Smith.",
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						null,
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						"表达语法正确且清晰",
						"My name is Smith; I have a reservation.",
						List.of())));
		EvaluationLlmClient llmClient = mock(EvaluationLlmClient.class);
		SessionEvaluationRepository reportRepository =
				mock(SessionEvaluationRepository.class);
		when(reportRepository.find(sessionId)).thenReturn(Optional.empty());

		EvaluationProcessor service = new EvaluationProcessor(
				mock(PronunciationAssessmentClient.class),
				llmClient,
				runtimeStore,
				mock(SceneRepository.class),
				messageRepository,
				turnRepository,
				reportRepository,
				mock(SceneSentenceReadingRepository.class),
				mock(IeltsPracticeRepository.class),
				mock(com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository.class),
				mock(IeltsSceneFlowService.class),
				mock(PracticeSessionRepository.class),
				mock(IeltsEvaluationRepository.class),
				mock(IeltsEvaluationLlmClient.class),
				mock(AuthService.class),
				mock(com.unispeaking.provider.ObjectStorageProvider.class),
				new com.unispeaking.infrastructure.config.ObjectStorageProperties(),
				mock(com.unispeaking.component.recording.RecordingStore.class));

		var report = service.generateDialogueReport(sessionId, dialogue);

		assertEquals(
				"本次对话已保存，但有效英文语音不足，暂时无法生成完整五维评分。",
				report.summary());
		verify(reportRepository).save(
				eq("custom_2001"),
				eq(sessionId),
				eq(report));
	}

	@Test
	void finalProviderFailureFallsBackToPersistedTurnScores() {
		String sessionId = "scene_5001";
		CustomSceneSession session = new CustomSceneSession(sessionId, "user_1");
		session.setSceneId("custom_2001");
		session.setSceneType(SceneType.CUSTOM_SCENE);

		ActiveSessionRegistry runtimeStore = mock(ActiveSessionRegistry.class);
		when(runtimeStore.findById(sessionId)).thenReturn(Optional.of(session));
		SessionMessageRepository messageRepository =
				mock(SessionMessageRepository.class);
		List<Message> dialogue = List.of(
				new Message(0, "What would you like to order?", null),
				new Message(1, "I would like a cup of coffee.", null));
		when(messageRepository.findMessages(sessionId)).thenReturn(dialogue);
		TurnEvaluationRepository turnRepository =
				mock(TurnEvaluationRepository.class);
		when(turnRepository.findAll(sessionId)).thenReturn(List.of(
				new CustomTurnEvaluation(
						"custom_2001",
						sessionId,
						1,
						"I would like a cup of coffee.",
						new BigDecimal("82"),
						new BigDecimal("80"),
						new BigDecimal("78"),
						new BigDecimal("88"),
						new BigDecimal("84"),
						new BigDecimal("81"),
						"表达清楚",
						"I'd like a cup of coffee, please.",
						List.of())));
		EvaluationLlmClient llmClient = mock(EvaluationLlmClient.class);
		when(llmClient.assessDialogue(
				org.mockito.ArgumentMatchers.anyList()))
				.thenThrow(new EvaluationException(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE));
		SessionEvaluationRepository reportRepository =
				mock(SessionEvaluationRepository.class);
		when(reportRepository.find(sessionId)).thenReturn(Optional.empty());

		EvaluationProcessor service = new EvaluationProcessor(
				mock(PronunciationAssessmentClient.class),
				llmClient,
				runtimeStore,
				mock(SceneRepository.class),
				messageRepository,
				turnRepository,
				reportRepository,
				mock(SceneSentenceReadingRepository.class),
				mock(IeltsPracticeRepository.class),
				mock(com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository.class),
				mock(IeltsSceneFlowService.class),
				mock(PracticeSessionRepository.class),
				mock(IeltsEvaluationRepository.class),
				mock(IeltsEvaluationLlmClient.class),
				mock(AuthService.class),
				mock(com.unispeaking.provider.ObjectStorageProvider.class),
				new com.unispeaking.infrastructure.config.ObjectStorageProperties(),
				mock(com.unispeaking.component.recording.RecordingStore.class));

		var report = service.generateDialogueReport(sessionId, dialogue);

		assertEquals(new BigDecimal("84.0"), report.accuracyScore());
		assertEquals(new BigDecimal("81.0"), report.fluencyScore());
		assertEquals(new BigDecimal("88.0"), report.grammarScore());
		assertEquals(new BigDecimal("82.0"), report.vocabularyScore());
		assertEquals(new BigDecimal("80.3"), report.naturalnessScore());
		assertEquals(new BigDecimal("83.2"), report.finalScore());
		verify(reportRepository).save(
				eq("custom_2001"),
				eq(sessionId),
				eq(report));
	}
}
