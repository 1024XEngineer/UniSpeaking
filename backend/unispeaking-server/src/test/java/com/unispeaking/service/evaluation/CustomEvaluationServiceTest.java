package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.evaluation.EvaluationProcessor;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.dto.evaluation.CustomEvaluationDetail;
import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationCommand;
import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import com.unispeaking.domain.dto.evaluation.SentenceEvaluationResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.SessionDetail;
import com.unispeaking.domain.vo.scene.SceneType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomEvaluationServiceTest {

	private static final String SCENE_ID = "custom-1";

	@Test
	void delegatesTurnSentenceAndDialogueOperations() {
		EvaluationProcessor delegate = mock(EvaluationProcessor.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		CustomEvaluationService service = new CustomEvaluationService(delegate, lifecycle);
		DialogueTurnEvaluationCommand command =
				new DialogueTurnEvaluationCommand("session-1", 1, null, "hello");
		DialogueTurnEvaluationResult turn = new DialogueTurnEvaluationResult(
				1, "hello", score("80"), score("81"), score("82"), score("83"),
				score("84"), score("85"), "反馈", "Hello there", List.of());
		SentenceEvaluationResponse sentence = new SentenceEvaluationResponse(
				score("90"), true, List.of());
		DialogueEvaluationResult dialogue = new DialogueEvaluationResult(
				List.of(new Message(0, "hello", null)), List.of(turn));
		when(delegate.evaluateDialogueTurn(command)).thenReturn(turn);
		when(delegate.evaluateSentenceReading("sentence-1", new byte[] {1, 2}))
				.thenReturn(sentence);
		when(delegate.getDialogueEvaluation("session-1")).thenReturn(dialogue);

		assertSame(turn, service.evaluateTurn(command));
		assertSame(sentence, service.evaluateSentence("sentence-1", new byte[] {1, 2}));
		assertSame(dialogue, service.getDialogueEvaluation("session-1"));
		verify(delegate).evaluateDialogueTurn(command);
		verify(delegate).evaluateSentenceReading(eq("sentence-1"), any(byte[].class));
		verify(delegate).getDialogueEvaluation("session-1");
		verifyNoInteractions(lifecycle);
	}

	@Test
	void generatesReportFromTheLatestSessionForScene() {
		EvaluationProcessor delegate = mock(EvaluationProcessor.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		CustomEvaluationService service = new CustomEvaluationService(delegate, lifecycle);
		SessionDetail oldSession = session("old-session", List.of(new Message(0, "old", null)));
		SessionDetail latestSession = session("latest-session", List.of(
				new Message(0, "question", null), new Message(1, "answer", null)));
		when(lifecycle.getBySceneId(SCENE_ID)).thenReturn(List.of(oldSession, latestSession));
		DialogueReportResult report = report();
		when(delegate.generateDialogueReport("latest-session", latestSession.dialogue()))
				.thenReturn(report);

		assertSame(report, service.generateReport(SCENE_ID));
		verify(delegate).generateDialogueReport("latest-session", latestSession.dialogue());
	}

	@Test
	void getsEvaluationUsingLatestSessionAndBuildsReportAndDialogue() {
		EvaluationProcessor delegate = mock(EvaluationProcessor.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		CustomEvaluationService service = new CustomEvaluationService(delegate, lifecycle);
		SessionDetail latestSession = session("latest-session", List.of(new Message(1, "answer", null)));
		when(lifecycle.getBySceneId(SCENE_ID)).thenReturn(List.of(latestSession));
		DialogueReportResult report = report();
		DialogueEvaluationResult dialogue = new DialogueEvaluationResult(latestSession.dialogue(), List.of());
		when(delegate.generateDialogueReport("latest-session", latestSession.dialogue())).thenReturn(report);
		when(delegate.getDialogueEvaluation("latest-session")).thenReturn(dialogue);

		CustomEvaluationDetail result = service.getEvaluation(SCENE_ID);

		assertSame(report, result.report());
		assertSame(dialogue, result.dialogue());
		verify(delegate).getDialogueEvaluation("latest-session");
		verify(delegate).generateDialogueReport("latest-session", latestSession.dialogue());
	}

	@Test
	void rejectsReportAndEvaluationWhenSceneHasNoSessions() {
		EvaluationProcessor delegate = mock(EvaluationProcessor.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		CustomEvaluationService service = new CustomEvaluationService(delegate, lifecycle);
		when(lifecycle.getBySceneId(SCENE_ID)).thenReturn(List.of());

		BusinessException reportFailure = assertThrows(
				BusinessException.class, () -> service.generateReport(SCENE_ID));
		BusinessException detailFailure = assertThrows(
				BusinessException.class, () -> service.getEvaluation(SCENE_ID));

		assertEquals("SESSION_NOT_FOUND", reportFailure.code());
		assertEquals("SESSION_NOT_FOUND", detailFailure.code());
		verifyNoInteractions(delegate);
	}

	@Test
	void propagatesDelegateFailuresWithoutReplacingThem() {
		EvaluationProcessor delegate = mock(EvaluationProcessor.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		CustomEvaluationService service = new CustomEvaluationService(delegate, lifecycle);
		SessionDetail latestSession = session("latest-session", List.of());
		when(lifecycle.getBySceneId(SCENE_ID)).thenReturn(List.of(latestSession));
		IllegalStateException failure = new IllegalStateException("provider failed");
		when(delegate.generateDialogueReport("latest-session", latestSession.dialogue()))
				.thenThrow(failure);

		assertSame(failure, assertThrows(
				IllegalStateException.class, () -> service.generateReport(SCENE_ID)));
	}

	private SessionDetail session(String id, List<Message> dialogue) {
		return new SessionDetail(id, SCENE_ID, SceneType.CUSTOM_SCENE, "DIALOGUE", dialogue);
	}

	private DialogueReportResult report() {
		return new DialogueReportResult(
				score("80"), score("81"), score("82"), score("83"), score("84"),
				score("83"), "summary", List.of("strength"), List.of("improvement"));
	}

	private BigDecimal score(String value) {
		return new BigDecimal(value);
	}
}
