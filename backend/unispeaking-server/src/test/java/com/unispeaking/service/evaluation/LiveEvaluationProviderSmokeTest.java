package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.infrastructure.ai.iflytek.IflytekScoringProvider;
import com.unispeaking.infrastructure.ai.qwen.QwenLlmProvider;
import com.unispeaking.service.evaluation.internal.audio.PcmWavValidator;
import com.unispeaking.service.evaluation.internal.calculation.ConversationScoreCalculation;
import com.unispeaking.service.evaluation.internal.calculation.ConversationScoreCalculator;
import com.unispeaking.service.evaluation.internal.calculation.TurnSpeechScoreCalculation;
import com.unispeaking.service.evaluation.internal.calculation.TurnSpeechScoreCalculator;
import com.unispeaking.service.evaluation.internal.model.ConversationLanguageAssessment;
import com.unispeaking.service.evaluation.internal.model.PronunciationAssessmentResult;
import com.unispeaking.service.evaluation.internal.model.TurnLanguageFeedback;
import com.unispeaking.service.evaluation.internal.provider.iflytek.IflytekSuntoneAssessmentParser;
import com.unispeaking.service.evaluation.internal.provider.llm.ConversationLanguageAssessmentParser;
import com.unispeaking.service.evaluation.internal.provider.llm.EvaluationJsonDocumentParser;
import com.unispeaking.service.evaluation.internal.provider.llm.TurnLanguageFeedbackParser;
import com.unispeaking.service.prompt.evaluation.ConversationReportEvaluationPromptBuilder;
import com.unispeaking.service.prompt.evaluation.ConversationReportEvaluationPromptTemplateLoader;
import com.unispeaking.service.prompt.evaluation.DialogueTurnEvaluationPromptBuilder;
import com.unispeaking.service.prompt.evaluation.DialogueTurnEvaluationPromptInput;
import com.unispeaking.service.prompt.evaluation.DialogueTurnEvaluationPromptTemplateLoader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 不依赖数据库的真实讯飞与百炼评分测试。
 *
 * <p>默认不运行。显式设置 LIVE_EVALUATION_PROVIDER_TEST=true 后才访问
 * Provider。日志包含合成测试数据和真实评分响应，但不包含密钥、鉴权 URL、
 * 音频 Base64、应用 ID、工作空间 ID或供应商会话 ID。</p>
 */
@EnabledIfEnvironmentVariable(
		named = "LIVE_EVALUATION_PROVIDER_TEST",
		matches = "(?i)true")
class LiveEvaluationProviderSmokeTest {

	private static final int MAX_RESPONSE_BYTES =
			2 * 1024 * 1024;
	private static final String REFERENCE_TEXT =
			"I went hiking with my friends last weekend.";

	@Test
	void providersReturnDetailedScoringLog() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		Path audioPath = Path.of(
				requiredEnv("LIVE_EVALUATION_WAV"))
				.toAbsolutePath()
				.normalize();
		byte[] audio = Files.readAllBytes(audioPath);
		PcmWavValidator.validate(audio);
		String referenceText = optionalEnv(
				"LIVE_EVALUATION_REFERENCE_TEXT",
				REFERENCE_TEXT);
		List<Message> dialogue = testDialogue();

		TurnQwenRun turnQwen = runTurnQwen(
				objectMapper,
				referenceText);
		QwenRun qwen = runQwen(objectMapper, dialogue);
		IflytekRun iflytek = runIflytek(
				objectMapper,
				referenceText,
				audio);
		ConversationScoreCalculation report =
				ConversationScoreCalculator.calculate(
						List.of(iflytek.speech().toContribution()),
						qwen.assessment());

		assertScore(qwen.assessment().grammarScore());
		assertScore(qwen.assessment().vocabularyScore());
		assertScore(
				qwen.assessment().textNaturalnessScore());
		assertFalse(qwen.assessment().summary().isBlank());
		assertTrue(
				containsChinese(
						turnQwen.feedback().feedbackSummary()));
		assertFalse(
				turnQwen.feedback().suggestedExpression().isBlank());
		assertChineseReportReasons(
				readTree(objectMapper, qwen.rawOutput()));
		assertScore(iflytek.assessment().overallScore());
		assertTrue(iflytek.speech().validPhonemeCount() > 0);
		assertTrue(
				iflytek.speech().effectiveDurationUnits() > 0);
		assertScore(report.finalScore());

		Map<String, Object> log = buildLog(
				objectMapper,
				audioPath,
				audio.length,
				referenceText,
				dialogue,
				turnQwen,
				qwen,
				iflytek,
				report);
		Path logPath = Path.of(optionalEnv(
						"LIVE_EVALUATION_LOG",
						"target/evaluation-live-test-report.json"))
				.toAbsolutePath()
				.normalize();
		Files.createDirectories(logPath.getParent());
		Files.writeString(
				logPath,
				objectMapper.writerWithDefaultPrettyPrinter()
						.writeValueAsString(log),
				StandardCharsets.UTF_8);

		System.out.printf(
				"iFlytek live scores: overall=%s accuracy=%s "
						+ "fluency=%s audioNaturalness=%s phonemes=%d%n",
				iflytek.assessment().overallScore(),
				iflytek.speech().accuracyScore(),
				iflytek.speech().fluencyScore(),
				iflytek.speech().audioNaturalnessScore(),
				iflytek.speech().validPhonemeCount());
		System.out.printf(
				"Single-turn LLM feedback: feedbackSummary=%s "
						+ "suggestedExpression=%s%n",
				turnQwen.feedback().feedbackSummary(),
				turnQwen.feedback().suggestedExpression());
		System.out.printf(
				"Qwen live scores: grammar=%s vocabulary=%s "
						+ "textNaturalness=%s%n",
				qwen.assessment().grammarScore(),
				qwen.assessment().vocabularyScore(),
				qwen.assessment().textNaturalnessScore());
		System.out.printf(
				"Six-score report: accuracy=%s fluency=%s grammar=%s "
						+ "vocabulary=%s naturalness=%s finalScore=%s%n",
				report.accuracyScore(),
				report.fluencyScore(),
				report.grammarScore(),
				report.vocabularyScore(),
				report.naturalnessScore(),
				report.finalScore());
		System.out.println(
				"Sanitized detailed log: " + logPath);
	}

	private TurnQwenRun runTurnQwen(
			ObjectMapper objectMapper,
			String transcript) {
		QwenProviderConfig provider =
				createQwenProvider(objectMapper);
		DialogueTurnEvaluationPromptInput input =
				new DialogueTurnEvaluationPromptInput(
						"FREE_CHAT",
						"周末活动英语口语交流",
						"英语口语陪练伙伴",
						"英语学习者",
						"练习使用自然的过去时描述周末活动",
						List.of(),
						"What did you do last weekend?",
						transcript);
		String prompt =
				new DialogueTurnEvaluationPromptBuilder(
						objectMapper,
						new DialogueTurnEvaluationPromptTemplateLoader())
						.build(input);
		String rawOutput =
				provider.provider().executeLlmTask(prompt, null);
		TurnLanguageFeedback feedback =
				new TurnLanguageFeedbackParser(
						new EvaluationJsonDocumentParser(
								objectMapper))
						.parse(rawOutput);
		return new TurnQwenRun(
				provider.model(),
				input,
				prompt,
				rawOutput,
				feedback);
	}

	private QwenRun runQwen(
			ObjectMapper objectMapper,
			List<Message> dialogue) {
		QwenProviderConfig provider =
				createQwenProvider(objectMapper);
		String prompt =
				new ConversationReportEvaluationPromptBuilder(
						objectMapper,
						new ConversationReportEvaluationPromptTemplateLoader())
						.build(dialogue);
		String rawOutput =
				provider.provider().executeLlmTask(prompt, null);
		ConversationLanguageAssessment assessment =
				new ConversationLanguageAssessmentParser(
						new EvaluationJsonDocumentParser(
								objectMapper))
						.parse(rawOutput);
		return new QwenRun(
				provider.model(),
				prompt,
				rawOutput,
				assessment);
	}

	private QwenProviderConfig createQwenProvider(
			ObjectMapper objectMapper) {
		String workspace = requiredEnv(
				"BAILIAN_WORKSPACE_ID");
		String region = optionalEnv(
				"BAILIAN_REGION",
				"cn-beijing");
		String model = optionalEnv(
				"QWEN_LLM_MODEL",
				"qwen3.5-plus");
		URI endpoint = URI.create(
				"https://" + workspace + "." + region
						+ ".maas.aliyuncs.com/compatible-mode/v1/"
						+ "chat/completions");
		QwenLlmProvider provider = new QwenLlmProvider(
				HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(10))
						.build(),
				objectMapper,
				requiredEnv("DASHSCOPE_API_KEY"),
				endpoint,
				model,
				Duration.ofSeconds(90),
				MAX_RESPONSE_BYTES);
		return new QwenProviderConfig(model, provider);
	}

	private IflytekRun runIflytek(
			ObjectMapper objectMapper,
			String referenceText,
			byte[] audio) {
		String endpoint = optionalEnv(
				"XFYUN_SUNTONE_ENDPOINT",
				"wss://cn-east-1.ws-api.xf-yun.com/v1/private/"
						+ "s8e098720");
		String language = optionalEnv(
				"XFYUN_SUNTONE_LANGUAGE",
				"en");
		String category = optionalEnv(
				"XFYUN_SUNTONE_CATEGORY",
				"sent");
		IflytekScoringProvider provider =
				new IflytekScoringProvider(
						objectMapper,
						requiredEnv("XFYUN_APP_ID"),
						requiredEnv("XFYUN_API_KEY"),
						requiredEnv("XFYUN_API_SECRET"),
						URI.create(endpoint),
						language,
						category,
						10,
						90,
						10 * 1024 * 1024);
		String rawEnvelope =
				provider.evaluatePronunciation(
						referenceText,
						box(audio),
						null);
		PronunciationAssessmentResult assessment =
				new IflytekSuntoneAssessmentParser(objectMapper)
						.parse(rawEnvelope);
		TurnSpeechScoreCalculation speech =
				TurnSpeechScoreCalculator.calculate(assessment);
		JsonNode envelope = readTree(
				objectMapper,
				rawEnvelope);
		String encoded = envelope.path("payload")
				.path("result")
				.path("text")
				.asString();
		JsonNode decoded = readTree(
				objectMapper,
				new String(
						Base64.getDecoder().decode(encoded),
						StandardCharsets.UTF_8));
		return new IflytekRun(
				language,
				category,
				envelope,
				decoded,
				assessment,
				speech);
	}

	private Map<String, Object> buildLog(
			ObjectMapper objectMapper,
			Path audioPath,
			int audioBytes,
			String referenceText,
			List<Message> dialogue,
			TurnQwenRun turnQwen,
			QwenRun qwen,
			IflytekRun iflytek,
			ConversationScoreCalculation report) {
		Map<String, Object> log = new LinkedHashMap<>();
		log.put(
				"_comment",
				"评分模块真实接口测试日志。按 input、singleTurnEvaluation、"
						+ "dialogueReportEvaluation 顺序阅读；所有 _comment "
						+ "字段均为阅读注释，不属于正式业务 DTO。");
		log.put("generatedAt", Instant.now().toString());
		log.put("testStatus", "PASSED");
		log.put("databaseUsed", false);
		log.put(
				"scopeNote",
				"真实 Provider 连通性与评分结构测试；音频和对话均为合成样本，"
						+ "分数仅用于证明调用链跑通，不用于模型质量校准。");
		log.put(
				"security",
				Map.of(
						"credentialsLogged",
						false,
						"authorizationUrlLogged",
						false,
						"audioBase64Logged",
						false,
						"providerIdentifiersLogged",
						false));
		Map<String, Object> input = new LinkedHashMap<>();
		input.put(
				"_comment",
				"本次测试使用合成音频和合成对话。audio 只记录元数据，"
						+ "不记录体积较大的 Base64 内容。");
		input.put("dialogue", dialogue);
		input.put(
				"singleTurnTranscript",
				referenceText);
		input.put(
				"audio",
				Map.of(
						"_comment",
						"evaluateDialogueTurn 接收的原始音频。",
						"path",
						audioPath.toString(),
						"bytes",
						audioBytes,
						"format",
						"PCM WAV, 16 kHz, 16-bit, mono"));
		log.put("input", input);

		Map<String, Object> singleTurn =
				new LinkedHashMap<>();
		singleTurn.put(
				"_comment",
				"单轮结果由两次独立交互组成：讯飞负责语音与逐音素评测；"
						+ "通用大模型只负责当前句的 feedbackSummary 和 "
						+ "suggestedExpression。");
		singleTurn.put(
				"speechProviderInteraction",
				buildIflytekLog(referenceText, iflytek));
		singleTurn.put(
				"languageModelInteraction",
				buildTurnQwenLog(objectMapper, turnQwen));
		singleTurn.put(
				"result",
				buildTurnResult(
						referenceText,
						iflytek,
						turnQwen.feedback()));
		log.put("singleTurnEvaluation", singleTurn);

		Map<String, Object> dialogueReport =
				new LinkedHashMap<>();
		dialogueReport.put(
				"_comment",
				"整场报告交互：通用大模型返回 grammar、vocabulary、"
						+ "text_naturalness 及中文理由；评分模块再与语音分"
						+ "融合，输出五维分和 finalScore。");
		dialogueReport.put(
				"languageModelInteraction",
				buildReportQwenLog(objectMapper, qwen));
		dialogueReport.put(
				"result",
				Map.of(
						"_comment",
						"正式 DialogueReportResult 只公开以下五维分数、"
								+ "总分和文字反馈。",
						"accuracyScore",
						report.accuracyScore(),
						"fluencyScore",
						report.fluencyScore(),
						"grammarScore",
						report.grammarScore(),
						"vocabularyScore",
						report.vocabularyScore(),
						"naturalnessScore",
						report.naturalnessScore(),
						"finalScore",
						report.finalScore(),
						"summary",
						qwen.assessment().summary(),
						"strengths",
						qwen.assessment().strengths(),
						"improvements",
						qwen.assessment().improvements()));
		log.put("dialogueReportEvaluation", dialogueReport);
		return log;
	}

	private Map<String, Object> buildIflytekLog(
			String referenceText,
			IflytekRun iflytek) {
		Map<String, Object> iflytekLog =
				new LinkedHashMap<>();
		iflytekLog.put(
				"_comment",
				"科大讯飞 Suntone 的真实交互。鉴权参数、App ID、"
						+ "会话 ID 和音频 Base64 已脱敏。");
		iflytekLog.put(
				"request",
				Map.of(
						"_comment",
						"实际业务参数；Provider 会在内存中把 WAV 转为 MP3。",
						"model",
						"iflytek-suntone",
						"audioTransport",
						"in-memory WAV to 16 kHz MP3; audio omitted",
						"parameter",
						Map.of(
								"lang",
								iflytek.language(),
								"core",
								iflytek.category(),
								"refText",
								referenceText,
								"scale",
								100,
								"precision",
								0.1,
								"phoneme_output",
								1,
								"output_rawtext",
								1,
								"dict_type",
								"IPA88",
								"dict_dialect",
								"en_us")));
		iflytekLog.put(
				"sanitizedProviderEnvelope",
				Map.of(
						"_comment",
						"讯飞外层响应；text 原本是 Base64，正文已在下一节解码。",
						"value",
						sanitizedEnvelope(iflytek.envelope())));
		iflytekLog.put(
				"decodedProviderOutput",
				Map.of(
						"_comment",
						"讯飞 Base64 解码后的原始评分 JSON，包含逐词和逐音素明细。",
						"refText",
						iflytek.decoded().path("refText")
								.asString(referenceText),
						"result",
						iflytek.decoded().path("result")));
		Map<String, Object> normalized =
				new LinkedHashMap<>();
		normalized.put(
				"overall",
				iflytek.assessment().overallScore());
		normalized.put(
				"rhythm",
				iflytek.assessment().rhythmScore());
		normalized.put(
				"tone",
				iflytek.assessment().toneScore());
		normalized.put(
				"integrity",
				iflytek.assessment().integrityScore());
		normalized.put(
				"pronunciation",
				iflytek.assessment().pronunciationScore());
		normalized.put(
				"providerFluency",
				iflytek.assessment().fluencyScore());
		normalized.put(
				"wordCount",
				iflytek.assessment().words().size());
		normalized.put(
				"validPhonemeCount",
				iflytek.speech().validPhonemeCount());
		normalized.put(
				"_comment",
				"评分模块映射后的句级结果；tone 为 null 表示供应商未返回，"
						+ "自然度计算会按文档重新归一化权重。");
		iflytekLog.put("normalizedAssessment", normalized);
		iflytekLog.put(
				"calculatedSpeechScores",
				Map.of(
						"_comment",
						"按音素时长加权公式得到的单轮语音派生分。",
						"phonemeWeightedAverage",
						iflytek.speech().phonemeAverage(),
						"accuracy",
						iflytek.speech().accuracyScore(),
						"fluency",
						iflytek.speech().fluencyScore(),
						"audioNaturalness",
						iflytek.speech()
								.audioNaturalnessScore(),
						"effectiveDurationUnits10ms",
						iflytek.speech()
								.effectiveDurationUnits()));
		return iflytekLog;
	}

	private Map<String, Object> buildTurnQwenLog(
			ObjectMapper objectMapper,
			TurnQwenRun turnQwen) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put(
				"_comment",
				"单轮通用大模型真实交互。只评价 currentTranscript，"
						+ "不参与语音、流利度、节奏或语调评分。");
		result.put(
				"request",
				Map.of(
						"_comment",
						"发送给百炼的完整单轮 Prompt 和结构化上下文。",
						"model",
						turnQwen.model(),
						"enableThinking",
						false,
						"promptInput",
						turnQwen.input(),
						"prompt",
						turnQwen.prompt()));
		result.put(
				"rawModelOutput",
				Map.of(
						"_comment",
						"百炼返回的原始 assistant content，未改写。",
						"content",
						turnQwen.rawOutput()));
		result.put(
				"parsedModelOutput",
				Map.of(
						"_comment",
						"严格 JSON 解析后的两个业务字段。",
						"value",
						readTree(
								objectMapper,
								turnQwen.rawOutput())));
		return result;
	}

	private Map<String, Object> buildReportQwenLog(
			ObjectMapper objectMapper,
			QwenRun qwen) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put(
				"_comment",
				"整场对话通用大模型真实交互。英文只用于原句证据和推荐表达，"
						+ "理由与数据质量说明要求使用简体中文。");
		result.put(
				"request",
				Map.of(
						"_comment",
						"发送给百炼的完整整场评分 Prompt。",
						"model",
						qwen.model(),
						"enableThinking",
						false,
						"prompt",
						qwen.prompt()));
		result.put(
				"rawModelOutput",
				Map.of(
						"_comment",
						"百炼返回的原始 assistant content，未改写。",
						"content",
						qwen.rawOutput()));
		result.put(
				"parsedModelOutput",
				Map.of(
						"_comment",
						"严格 JSON 解析后的原始评分结构。",
						"value",
						readTree(objectMapper, qwen.rawOutput())));
		result.put(
				"normalizedAssessment",
				Map.of(
						"_comment",
						"评分模块归一化后用于整场公式的文本侧结果。",
						"grammar",
						qwen.assessment().grammarScore(),
						"vocabulary",
						qwen.assessment().vocabularyScore(),
						"textNaturalness",
						qwen.assessment()
								.textNaturalnessScore(),
						"summary",
						qwen.assessment().summary(),
						"strengths",
						qwen.assessment().strengths(),
						"improvements",
						qwen.assessment().improvements()));
		return result;
	}

	private Map<String, Object> buildTurnResult(
			String transcript,
			IflytekRun iflytek,
			TurnLanguageFeedback feedback) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put(
				"_comment",
				"对应 DialogueTurnEvaluationResult 的完整单轮结果。"
						+ "语音分来自讯飞，两个反馈字段来自通用大模型。");
		result.put("turnNo", 1);
		result.put("transcript", transcript);
		result.put(
				"overallScore",
				iflytek.assessment().overallScore());
		result.put(
				"rhythmScore",
				iflytek.assessment().rhythmScore());
		result.put(
				"toneScore",
				iflytek.assessment().toneScore());
		result.put(
				"integrityScore",
				iflytek.assessment().integrityScore());
		result.put(
				"pronunciationScore",
				iflytek.assessment().pronunciationScore());
		result.put(
				"fluencyScore",
				iflytek.assessment().fluencyScore());
		result.put(
				"feedbackSummary",
				feedback.feedbackSummary());
		result.put(
				"suggestedExpression",
				feedback.suggestedExpression());
		result.put(
				"words",
				iflytek.assessment().words().stream()
						.map(word -> Map.of(
								"word",
								word.word(),
								"wordScore",
								word.pronunciationScore(),
								"phonemes",
								word.phonemes().stream()
										.map(phoneme -> Map.of(
												"expectedPhoneme",
												phoneme.expectedPhoneme(),
												"actualPhoneme",
												phoneme.actualPhoneme(),
												"score",
												phoneme.pronunciationScore(),
												"startPosition10ms",
												phoneme.startPosition(),
												"endPosition10ms",
												phoneme.endPosition()))
										.toList()))
						.toList());
		return result;
	}

	private Map<String, Object> sanitizedEnvelope(
			JsonNode envelope) {
		JsonNode header = envelope.path("header");
		JsonNode result = envelope.path("payload")
				.path("result");
		return Map.of(
				"header",
				Map.of(
						"code",
						header.path("code").asInt(),
						"message",
						header.path("message").asString(),
						"status",
						header.path("status").asInt()),
				"payload",
				Map.of(
						"result",
						Map.of(
								"seq",
								result.path("seq").asInt(),
								"format",
								result.path("format").asString(),
								"encoding",
								result.path("encoding").asString(),
								"compress",
								result.path("compress").asString(),
								"status",
								result.path("status").asInt(),
								"text",
								"<base64 omitted; decodedProviderOutput below>")));
	}

	private static List<Message> testDialogue() {
		return List.of(
				new Message(
						0,
						"What did you do last weekend?",
						null),
				new Message(
						1,
						"I went hiking with my friends last weekend. "
								+ "We visited a mountain near the city, "
								+ "and I really enjoyed the fresh air. "
								+ "The trail was challenging in a few places, "
								+ "but we helped each other and reached the "
								+ "top before sunset. I would definitely like "
								+ "to go there again next month.",
						null));
	}

	private static JsonNode readTree(
			ObjectMapper objectMapper,
			String json) {
		try {
			return objectMapper.readTree(json);
		}
		catch (Exception exception) {
			throw new AssertionError(
					"Expected valid JSON in live provider test");
		}
	}

	private static Byte[] box(byte[] audio) {
		Byte[] boxed = new Byte[audio.length];
		for (int index = 0; index < audio.length; index++) {
			boxed[index] = audio[index];
		}
		return boxed;
	}

	private static String requiredEnv(String name) {
		String value = System.getenv(name);
		assertNotNull(value, name + " must be configured");
		assertFalse(
				value.isBlank(),
				name + " must be configured");
		return value.trim();
	}

	private static String optionalEnv(
			String name,
			String fallback) {
		String value = System.getenv(name);
		return value == null || value.isBlank()
				? fallback
				: value.trim();
	}

	private static void assertScore(BigDecimal score) {
		assertNotNull(score);
		assertTrue(score.signum() >= 0);
		assertTrue(
				score.compareTo(new BigDecimal("100")) <= 0);
	}

	private static void assertChineseReportReasons(
			JsonNode root) {
		JsonNode dimensions = root.path("dimensions");
		for (String dimension : List.of(
				"grammar",
				"vocabulary",
				"text_naturalness")) {
			for (String collection : List.of(
					"strengths",
					"improvements")) {
				for (JsonNode item : dimensions.path(dimension)
						.path(collection)) {
					assertTrue(
							containsChinese(
									item.path("reason").asString()),
							"Report reasons must use Chinese");
				}
			}
		}
		for (JsonNode note : root.path("data_quality_notes")) {
			assertTrue(
					containsChinese(note.asString()),
					"Data quality notes must use Chinese");
		}
	}

	private static boolean containsChinese(String value) {
		if (value == null) {
			return false;
		}
		return value.codePoints().anyMatch(
				codePoint -> Character.UnicodeScript.of(codePoint)
						== Character.UnicodeScript.HAN);
	}

	private record QwenProviderConfig(
			String model,
			QwenLlmProvider provider) {
	}

	private record TurnQwenRun(
			String model,
			DialogueTurnEvaluationPromptInput input,
			String prompt,
			String rawOutput,
			TurnLanguageFeedback feedback) {
	}

	private record QwenRun(
			String model,
			String prompt,
			String rawOutput,
			ConversationLanguageAssessment assessment) {
	}

	private record IflytekRun(
			String language,
			String category,
			JsonNode envelope,
			JsonNode decoded,
			PronunciationAssessmentResult assessment,
			TurnSpeechScoreCalculation speech) {
	}
}
