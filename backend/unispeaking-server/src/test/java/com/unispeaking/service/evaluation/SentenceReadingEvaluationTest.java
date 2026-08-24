package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import com.unispeaking.component.evaluation.EvaluationProcessor;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SentenceReadingEvaluationTest {

	@Test
	void usesTheBestRepeatedAttemptForTheReportedPassResult() {
		String sceneId = "custom_486974af79f64105a425b8eb40974b91";
		String sentenceId = "sentence_53109a4e3b524a3cbbc753f1be60d0f7";
		String sentenceText = "Do I need to sign a contract for one year?";
		String userId = "11111111-1111-4111-8111-111111111111";
		byte[] audio = canonicalWav();
		PronunciationAssessmentClient pronunciationClient =
				mock(PronunciationAssessmentClient.class);
		SceneSentenceReadingRepository readingRepository =
				mock(SceneSentenceReadingRepository.class);
		SceneRepository sceneRepository = mock(SceneRepository.class);
		AuthService authService = mock(AuthService.class);
		LearningContentItem sentence = new LearningContentItem(
				sentenceId,
				sentenceText,
				"我需要签一年的合约吗？",
				"");
		CustomSceneDefinition scene = new CustomSceneDefinition(
				sceneId,
				userId,
				"比较手机套餐",
				"通信",
				"营业厅",
				"店员",
				"顾客",
				"比较套餐",
				"",
				"{}",
				List.of(),
				List.of(),
				List.of(sentence));
		PronunciationAssessmentResult current = assessment("62.40");

		when(readingRepository.findSceneIdBySentenceId(sentenceId))
				.thenReturn(Optional.of(sceneId));
		when(authService.requireUserId(null)).thenReturn(userId);
		when(sceneRepository.findCustomDefinitionById(sceneId))
				.thenReturn(Optional.of(scene));
		when(pronunciationClient.evaluate(eq(sentenceText), aryEq(audio)))
				.thenReturn(current);
		when(readingRepository.saveAttempt(sceneId, sentence, current))
				.thenReturn("sentence_reading_1");
		when(readingRepository.summarizeAttempts(sceneId, sentenceId))
				.thenReturn(new SceneSentenceReadingRepository.AttemptSummary(
						11,
						new BigDecimal("76.40")));
		EvaluationProcessor processor = processor(
				pronunciationClient,
				readingRepository,
				sceneRepository,
				authService);

		var response = processor.evaluateSentenceReading(sentenceId, audio);

		assertTrue(response.passed());
		assertEquals(new BigDecimal("76.40"), response.overallScore());
		verify(readingRepository).saveAttempt(sceneId, sentence, current);
	}

	private EvaluationProcessor processor(
			PronunciationAssessmentClient pronunciationClient,
			SceneSentenceReadingRepository readingRepository,
			SceneRepository sceneRepository,
			AuthService authService) {
		return new EvaluationProcessor(
				pronunciationClient,
				mock(EvaluationLlmClient.class),
				mock(ActiveSessionRegistry.class),
				sceneRepository,
				mock(SessionMessageRepository.class),
				mock(TurnEvaluationRepository.class),
				mock(SessionEvaluationRepository.class),
				readingRepository,
				mock(IeltsPracticeRepository.class),
				mock(IeltsRepository.class),
				mock(IeltsSceneFlowService.class),
				mock(PracticeSessionRepository.class),
				mock(IeltsEvaluationRepository.class),
				mock(IeltsEvaluationLlmClient.class),
				authService,
				mock(com.unispeaking.provider.ObjectStorageProvider.class),
				new ObjectStorageProperties(),
				mock(com.unispeaking.component.recording.RecordingStore.class));
	}

	private PronunciationAssessmentResult assessment(String overall) {
		PronunciationPhonemeResult phoneme =
				new PronunciationPhonemeResult(
						0,
						"eɪ",
						"eɪ",
						new BigDecimal("0.30"),
						20,
						30);
		PronunciationWordResult word = new PronunciationWordResult(
				0,
				"a",
				WordReadStatus.NORMAL,
				new BigDecimal("0.30"),
				new BigDecimal("0.30"),
				false,
				List.of(phoneme));
		return new PronunciationAssessmentResult(
				new BigDecimal(overall),
				new BigDecimal("71.10"),
				null,
				new BigDecimal("80.00"),
				new BigDecimal("83.30"),
				new BigDecimal("49.90"),
				EndingTone.FALL,
				List.of(word));
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
}
