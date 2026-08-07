package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.domain.dto.evaluation.SpeechEvaluationCommand;
import com.unispeaking.infrastructure.evaluation.client.EvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.IeltsEvaluationLlmClient;
import com.unispeaking.infrastructure.evaluation.client.PronunciationAssessmentClient;
import com.unispeaking.infrastructure.persistence.repository.evaluation.IeltsEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.evaluation.impl.EvaluationServiceImpl;
import com.unispeaking.service.scene.SceneFlowService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvaluationServiceImplSpeechTest {

	private PronunciationAssessmentClient pronunciationClient;
	private EvaluationLlmClient llmClient;
	private ActiveSessionRegistry activeSessionRegistry;
	private SceneRepository sceneRepository;
	private SessionMessageRepository sessionMessageRepository;
	private TurnEvaluationRepository turnEvaluationRepository;
	private SessionEvaluationRepository sessionEvaluationRepository;
	private SceneSentenceReadingRepository sceneSentenceReadingRepository;
	private IeltsPracticeRepository ieltsPracticeRepository;
	private SceneFlowService sceneFlowService;
	private PracticeSessionRepository practiceSessionRepository;
	private IeltsEvaluationRepository ieltsEvaluationRepository;
	private IeltsEvaluationLlmClient ieltsLlmClient;
	private AuthService authService;
	private EvaluationService service;

	@BeforeEach
	void setUp() {
		pronunciationClient = mock(PronunciationAssessmentClient.class);
		llmClient = mock(EvaluationLlmClient.class);
		activeSessionRegistry = mock(ActiveSessionRegistry.class);
		sceneRepository = mock(SceneRepository.class);
		sessionMessageRepository = mock(SessionMessageRepository.class);
		turnEvaluationRepository = mock(TurnEvaluationRepository.class);
		sessionEvaluationRepository = mock(SessionEvaluationRepository.class);
		sceneSentenceReadingRepository =
				mock(SceneSentenceReadingRepository.class);
		ieltsPracticeRepository = mock(IeltsPracticeRepository.class);
		sceneFlowService = mock(SceneFlowService.class);
		practiceSessionRepository = mock(PracticeSessionRepository.class);
		ieltsEvaluationRepository = mock(IeltsEvaluationRepository.class);
		ieltsLlmClient = mock(IeltsEvaluationLlmClient.class);
		authService = mock(AuthService.class);
		service = new EvaluationServiceImpl(
				pronunciationClient,
				llmClient,
				activeSessionRegistry,
				sceneRepository,
				sessionMessageRepository,
				turnEvaluationRepository,
				sessionEvaluationRepository,
				sceneSentenceReadingRepository,
				ieltsPracticeRepository,
				mock(com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository.class),
				sceneFlowService,
				practiceSessionRepository,
				ieltsEvaluationRepository,
				ieltsLlmClient,
				authService,
				mock(com.unispeaking.infrastructure.storage.ObjectStorageProvider.class),
				new com.unispeaking.infrastructure.config.ObjectStorageProperties(),
				mock(com.unispeaking.service.recording.IeltsRecordingService.class));
	}

	@AfterEach
	void neverTouchesSessionSceneLlmOrPersistenceDependencies() {
		verifyNoInteractions(
				llmClient,
				activeSessionRegistry,
				sceneRepository,
				sessionMessageRepository,
				turnEvaluationRepository,
				sessionEvaluationRepository,
				sceneSentenceReadingRepository,
				ieltsPracticeRepository,
				sceneFlowService,
				practiceSessionRepository,
				ieltsEvaluationRepository,
				ieltsLlmClient,
				authService);
	}

	@Test
	void validatesWavTrimsReferenceTextAndMapsOnlyCalculatedFields() {
		byte[] audio = canonicalWav();
		when(pronunciationClient.evaluate(
				eq("Hello world"),
				aryEq(audio)))
				.thenReturn(completeAssessment());

		var result = service.evaluateSpeech(
				new SpeechEvaluationCommand("  Hello world  ", audio));

		assertAll(
				() -> assertEquals(score("49.0"), result.accuracyScore()),
				() -> assertEquals(score("75.0"), result.fluencyScore()),
				() -> assertEquals(40, result.effectiveDurationUnits()),
				() -> assertEquals(2, result.validPhonemeCount()));
		verify(pronunciationClient).evaluate(
				eq("Hello world"),
				aryEq(audio));
	}

	@Test
	void mapsNullCommandAndNullOrBlankReferenceTextToInvalidRequest() {
		assertAll(
				() -> assertError(
						null,
						EvaluationErrorCode.INVALID_REQUEST),
				() -> assertError(
						new SpeechEvaluationCommand(null, canonicalWav()),
						EvaluationErrorCode.INVALID_REQUEST),
				() -> assertError(
						new SpeechEvaluationCommand(" \t\n ", canonicalWav()),
						EvaluationErrorCode.INVALID_REQUEST));
		verifyNoInteractions(pronunciationClient);
	}

	@Test
	void rejectsMissingOrInvalidAudioBeforeCallingProvider() {
		assertAll(
				() -> assertError(
						new SpeechEvaluationCommand("Hello", null),
						EvaluationErrorCode.AUDIO_REQUIRED),
				() -> assertError(
						new SpeechEvaluationCommand(
								"Hello",
								"not wav".getBytes(StandardCharsets.US_ASCII)),
						EvaluationErrorCode.AUDIO_UNSUPPORTED));
		verifyNoInteractions(pronunciationClient);
	}

	@Test
	void rejectsIncompleteProviderResultWithoutSideEffects() {
		byte[] audio = canonicalWav();
		PronunciationAssessmentResult incomplete =
				new PronunciationAssessmentResult(
						score("80"),
						score("70"),
						score("60"),
						score("90"),
						score("80"),
						null,
						EndingTone.FALL,
						completeAssessment().words());
		when(pronunciationClient.evaluate(eq("Hello"), aryEq(audio)))
				.thenReturn(incomplete);

		assertError(
				new SpeechEvaluationCommand("Hello", audio),
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
		verify(pronunciationClient).evaluate(eq("Hello"), aryEq(audio));
	}

	private void assertError(
			SpeechEvaluationCommand command,
			EvaluationErrorCode expected) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> service.evaluateSpeech(command));

		assertSame(expected, exception.errorCode());
	}

	private PronunciationAssessmentResult completeAssessment() {
		return new PronunciationAssessmentResult(
				score("80"),
				score("70"),
				score("60"),
				score("90"),
				score("80"),
				score("80"),
				EndingTone.FALL,
				List.of(new PronunciationWordResult(
						0,
						"test",
						WordReadStatus.NORMAL,
						score("80"),
						score("80"),
						null,
						List.of(
								new PronunciationPhonemeResult(
										0,
										"t",
										"t",
										score("100"),
										0,
										10),
								new PronunciationPhonemeResult(
										1,
										"e",
										"ae",
										score("40"),
										10,
										40)))));
	}

	private byte[] canonicalWav() {
		byte[] wav = new byte[46];
		writeAscii(wav, 0, "RIFF");
		writeInt(wav, 4, wav.length - 8);
		writeAscii(wav, 8, "WAVE");
		writeAscii(wav, 12, "fmt ");
		writeInt(wav, 16, 16);
		writeShort(wav, 20, 1);
		writeShort(wav, 22, 1);
		writeInt(wav, 24, 16_000);
		writeInt(wav, 28, 32_000);
		writeShort(wav, 32, 2);
		writeShort(wav, 34, 16);
		writeAscii(wav, 36, "data");
		writeInt(wav, 40, 2);
		return wav;
	}

	private void writeAscii(byte[] target, int offset, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(bytes, 0, target, offset, bytes.length);
	}

	private void writeShort(byte[] target, int offset, int value) {
		target[offset] = (byte) value;
		target[offset + 1] = (byte) (value >>> 8);
	}

	private void writeInt(byte[] target, int offset, int value) {
		target[offset] = (byte) value;
		target[offset + 1] = (byte) (value >>> 8);
		target[offset + 2] = (byte) (value >>> 16);
		target[offset + 3] = (byte) (value >>> 24);
	}

	private BigDecimal score(String value) {
		return new BigDecimal(value);
	}
}
