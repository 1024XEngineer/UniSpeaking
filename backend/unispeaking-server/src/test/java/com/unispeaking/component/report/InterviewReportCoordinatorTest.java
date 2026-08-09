package com.unispeaking.component.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.evaluation.InterviewReportRecord;
import com.unispeaking.domain.po.scene.InterviewSceneDefinition;
import com.unispeaking.domain.po.session.LearnerMessageRecord;
import com.unispeaking.domain.vo.evaluation.ReportStatus;
import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.infrastructure.evaluation.client.PronunciationAssessmentClient;
import com.unispeaking.infrastructure.persistence.repository.evaluation.InterviewReportRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.InterviewSceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.AiProviderRegistry.RoutedResult;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link InterviewReportCoordinator} 音频降级/重试预算的 Mockito 单测。
 * {@code interviewEvaluationExecutor} 注入同步执行器（{@code command -> command.run()}），
 * 逐轮语音评分为真实有界池，桩数据返回即完成。
 */
class InterviewReportCoordinatorTest {

	private static final String SESSION_ID = "session-1";
	private static final String SCENE_ID = "interview_1";
	private static final String USER_ID = "user-1";

	private final InterviewReportRepository reportRepository =
			mock(InterviewReportRepository.class);
	private final SessionMessageRepository sessionMessageRepository =
			mock(SessionMessageRepository.class);
	private final InterviewSceneRepository sceneRepository =
			mock(InterviewSceneRepository.class);
	private final PronunciationAssessmentClient pronunciationClient =
			mock(PronunciationAssessmentClient.class);
	private final AiProviderRegistry providerRegistry = mock(AiProviderRegistry.class);
	private final RecordingStore recordingStore = mock(RecordingStore.class);

	private InterviewReportCoordinator coordinator;

	@BeforeEach
	void setUp() {
		coordinator = new InterviewReportCoordinator(
				reportRepository,
				sessionMessageRepository,
				sceneRepository,
				pronunciationClient,
				providerRegistry,
				recordingStore,
				command -> command.run(),
				new ObjectMapper());
	}

	@Test
	void failsWithProviderRetryableWhenAllAudioTurnsFailWithinRetryBudget() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of(turn(1, "first transcript")));
		when(recordingStore.readAudio(SESSION_ID, "turn-1.wav"))
				.thenReturn(wavWithSampleRate(16_000));
		when(pronunciationClient.evaluate(anyString(), any()))
				.thenThrow(new EvaluationException(
						EvaluationErrorCode.PROVIDER_NOT_CONFIGURED));
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.of(record(ReportStatus.PROCESSING, 0)))
				.thenReturn(Optional.of(record(ReportStatus.FAILED, 0)));
		when(reportRepository.retryFromFailed(SESSION_ID, 0)).thenReturn(false);

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(reportRepository).markFailed(SESSION_ID, "PROVIDER_RETRYABLE");
		verify(reportRepository).retryFromFailed(SESSION_ID, 0);
		verify(reportRepository, never()).markCompleted(any());
	}

	@Test
	void degradesToCompletedWhenAllAudioTurnsFailAfterRetryBudgetExhausted() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of(turn(1, "first transcript")));
		when(recordingStore.readAudio(SESSION_ID, "turn-1.wav"))
				.thenReturn(wavWithSampleRate(16_000));
		when(pronunciationClient.evaluate(anyString(), any()))
				.thenThrow(new EvaluationException(
						EvaluationErrorCode.PROVIDER_NOT_CONFIGURED));
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.of(record(ReportStatus.PROCESSING, 1)));
		when(sessionMessageRepository.findMessages(SESSION_ID))
				.thenReturn(List.of(
						new Message(0, "Tell me about yourself", null),
						new Message(1, "I am a backend engineer", null)));
		when(sceneRepository.findById(SCENE_ID))
				.thenReturn(Optional.of(sceneDefinition()));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed(validLlmJson()));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		ArgumentCaptor<InterviewReportRecord> completed =
				ArgumentCaptor.forClass(InterviewReportRecord.class);
		verify(reportRepository).markCompleted(completed.capture());
		verify(reportRepository, never()).markFailed(anyString(), anyString());
		InterviewReportRecord record = completed.getValue();
		assertEquals(ReportStatus.COMPLETED, record.status());
		assertNull(record.fluencyScore());
		assertNull(record.pronunciationIntelligibilityScore());
		assertTrue(record.summary().contains("发音评分服务暂不可用"));
	}

	@Test
	void completesWithPartialTurnsWhenOnlySomeAudioTurnsFail() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of(
						turn(1, "first transcript"),
						turn(2, "second transcript")));
		when(recordingStore.readAudio(SESSION_ID, "turn-1.wav"))
				.thenReturn(wavWithSampleRate(16_000));
		when(recordingStore.readAudio(SESSION_ID, "turn-2.wav"))
				.thenReturn(wavWithSampleRate(16_000));
		when(pronunciationClient.evaluate(eq("first transcript"), any()))
				.thenThrow(new EvaluationException(
						EvaluationErrorCode.PROVIDER_NOT_CONFIGURED));
		when(pronunciationClient.evaluate(eq("second transcript"), any()))
				.thenReturn(validAssessment());
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.of(record(ReportStatus.PROCESSING, 0)));
		when(sessionMessageRepository.findMessages(SESSION_ID))
				.thenReturn(List.of(
						new Message(0, "Tell me about yourself", null),
						new Message(1, "I am a backend engineer", null)));
		when(sceneRepository.findById(SCENE_ID))
				.thenReturn(Optional.of(sceneDefinition()));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed(validLlmJson()));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		ArgumentCaptor<InterviewReportRecord> completed =
				ArgumentCaptor.forClass(InterviewReportRecord.class);
		verify(reportRepository).markCompleted(completed.capture());
		verify(reportRepository, never()).markFailed(anyString(), anyString());
		InterviewReportRecord record = completed.getValue();
		assertEquals(ReportStatus.COMPLETED, record.status());
		assertTrue(record.fluencyScore().compareTo(BigDecimal.ZERO) > 0);
		assertTrue(record.pronunciationIntelligibilityScore().compareTo(BigDecimal.ZERO) > 0);
		assertTrue(record.summary().contains("部分轮次"));
	}

	@Test
	void failsWithLlmUnparseableWhenLlmReturnsNonJson() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of());
		when(sessionMessageRepository.findMessages(SESSION_ID))
				.thenReturn(List.of(new Message(1, "I am a backend engineer", null)));
		when(sceneRepository.findById(SCENE_ID))
				.thenReturn(Optional.of(sceneDefinition()));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed("this is not json"));

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(reportRepository).markFailed(SESSION_ID, "LLM_UNPARSEABLE");
		verify(reportRepository, never()).markCompleted(any());
	}

	@Test
	void failsWithProviderRetryableWhenLlmProviderThrows() {
		when(sessionMessageRepository.findMessagesWithAudioObjectKeys(SESSION_ID))
				.thenReturn(List.of());
		when(sessionMessageRepository.findMessages(SESSION_ID))
				.thenReturn(List.of(new Message(1, "I am a backend engineer", null)));
		when(sceneRepository.findById(SCENE_ID))
				.thenReturn(Optional.of(sceneDefinition()));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenThrow(new BusinessException("QWEN_LLM_CALL_FAILED", "llm unavailable"));
		when(reportRepository.findById(SESSION_ID))
				.thenReturn(Optional.of(record(ReportStatus.FAILED, 0)));
		when(reportRepository.retryFromFailed(SESSION_ID, 0)).thenReturn(false);

		coordinator.submit(SESSION_ID, SCENE_ID, USER_ID);

		verify(reportRepository).markFailed(SESSION_ID, "PROVIDER_RETRYABLE");
		verify(reportRepository, never()).markCompleted(any());
	}

	private LearnerMessageRecord turn(int messageNo, String content) {
		return new LearnerMessageRecord(
				messageNo,
				content,
				"turn-" + messageNo + ".wav");
	}

	private InterviewReportRecord record(ReportStatus status, int retryCount) {
		return new InterviewReportRecord(
				SESSION_ID,
				SCENE_ID,
				USER_ID,
				status,
				null,
				null,
				null, null, null,
				null, null, null,
				null, null, null,
				null, null, null,
				null, null, null,
				retryCount,
				null,
				null,
				null);
	}

	private InterviewSceneDefinition sceneDefinition() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return new InterviewSceneDefinition(
				SCENE_ID,
				USER_ID,
				"{}",
				"final text",
				"{\"interviewTopics\":[\"自我介绍\",\"项目经历\"]}",
				InterviewDifficulty.STANDARD,
				"prompt",
				now,
				now,
				null);
	}

	private RoutedResult<String> routed(String content) {
		return new RoutedResult<>(
				"qwen3.5-plus",
				"qwen",
				AiCapability.LLM,
				content);
	}

	private String validLlmJson() {
		return """
				{
				  "logic_coherence": {"score": 80, "evaluation": "clear structure", "advice": "keep it up"},
				  "grammar_control": {"score": 75, "evaluation": "mostly accurate", "advice": "mind tenses"},
				  "vocabulary_expression": {"score": 85, "evaluation": "rich vocabulary", "advice": "vary more"},
				  "fluency": {"evaluation": "fluent", "advice": "maintain pace"},
				  "pronunciation_intelligibility": {"evaluation": "clear", "advice": "watch intonation"},
				  "overall_score": 80,
				  "summary": "Overall solid performance."
				}
				""";
	}

	private PronunciationAssessmentResult validAssessment() {
		PronunciationPhonemeResult phoneme = new PronunciationPhonemeResult(
				0, "t", "t", new BigDecimal("80.0"), 0, 60);
		PronunciationWordResult word = new PronunciationWordResult(
				0,
				"test",
				WordReadStatus.NORMAL,
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				false,
				List.of(phoneme));
		return new PronunciationAssessmentResult(
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				EndingTone.FALL,
				List.of(word));
	}

	private byte[] wavWithSampleRate(int sampleRate) {
		ByteBuffer wav = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
		wav.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.putInt(38);
		wav.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.putInt(16);
		wav.putShort((short) 1);
		wav.putShort((short) 1);
		wav.putInt(sampleRate);
		wav.putInt(sampleRate * 2);
		wav.putShort((short) 2);
		wav.putShort((short) 16);
		wav.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.putInt(2);
		wav.putShort((short) 0);
		return wav.array();
	}
}
