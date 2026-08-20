package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.component.evaluation.EvaluationProcessor;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
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
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.provider.ObjectStorageProvider;
import org.junit.jupiter.api.Test;

class EvaluationTurnInputValidationTest {

	@Test
	void blankTranscriptIsRejectedWithoutPersistingUnavailableScore() {
		TurnEvaluationRepository turnRepository = mock(TurnEvaluationRepository.class);
		EvaluationProcessor processor = new EvaluationProcessor(
				mock(PronunciationAssessmentClient.class),
				mock(EvaluationLlmClient.class),
				mock(ActiveSessionRegistry.class),
				mock(SceneRepository.class),
				mock(SessionMessageRepository.class),
				turnRepository,
				mock(SessionEvaluationRepository.class),
				mock(SceneSentenceReadingRepository.class),
				mock(IeltsPracticeRepository.class),
				mock(IeltsRepository.class),
				mock(IeltsSceneFlowService.class),
				mock(PracticeSessionRepository.class),
				mock(IeltsEvaluationRepository.class),
				mock(IeltsEvaluationLlmClient.class),
				mock(AuthService.class),
				mock(ObjectStorageProvider.class),
				new ObjectStorageProperties(),
				mock(RecordingStore.class));

		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> processor.evaluateDialogueTurn(
						new DialogueTurnEvaluationCommand("session-1", 1, null, "  \n")));

		assertEquals(EvaluationErrorCode.TRANSCRIPT_REQUIRED, exception.errorCode());
		verifyNoInteractions(turnRepository);
	}
}
