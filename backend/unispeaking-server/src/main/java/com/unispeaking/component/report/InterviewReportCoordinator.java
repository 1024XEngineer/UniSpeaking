package com.unispeaking.component.report;

import com.unispeaking.common.evaluation.calculation.TurnSpeechScoreCalculation;
import com.unispeaking.common.evaluation.calculation.TurnSpeechScoreCalculator;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.validation.PcmWavValidator;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.dto.evaluation.InterviewReport;
import com.unispeaking.domain.dto.evaluation.InterviewReportResponse;
import com.unispeaking.domain.dto.evaluation.InterviewDimensionScore;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.evaluation.InterviewReportRecord;
import com.unispeaking.domain.po.session.LearnerMessageRecord;
import com.unispeaking.domain.vo.evaluation.InterviewDimension;
import com.unispeaking.domain.vo.evaluation.ReportStatus;
import com.unispeaking.infrastructure.evaluation.client.PronunciationAssessmentClient;
import com.unispeaking.infrastructure.persistence.repository.evaluation.InterviewReportRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.InterviewSceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.component.recording.RecordingStore;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * Interview 报告任务协调器（行即任务，无 task 表）。
 *
 * <p>任务体：turn-aware 读消息 → 逐段 iFlytek 语音评分（发音+流利，有界并行 ≤3，
 * 单段失败降级该段）→ 整场一次 LLM（逻辑/语法/词汇 + overall + summary，注入五维分数并
 * 声明发音/流利为音频证据）→ 聚合写 {@code interview_report} COMPLETED。</p>
 *
 * <p>单跑守卫 {@code runningReportSessionIds}：submit 前 check-add、任务 finally remove；
 * {@code @Scheduled} 僵尸清扫与 GET 惰性重派都跳过 running 集，防止 PROCESSING
 * （在跑/孤儿歧义）双跑导致 COMPLETED→FAILED 状态回归。</p>
 *
 * <p>失败分类 + 自动重试 1 次：{@code PROVIDER_RETRYABLE} 瞬时失败可自动重试
 * （{@code retry_count<1} 时 CAS 递增 + 重提交）；其余留 FAILED 供手动 {@code retryReport}。</p>
 *
 * <p>V1 降级：总音频 {@code session.wav} 只拼接用户录音段（16kHz mono 16-bit PCM），
 * 不含 AI 段；AI 段格式与轮次关联留待后续。</p>
 */
@Component
public class InterviewReportCoordinator {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			InterviewReportCoordinator.class);
	private static final int AUDIO_SCORING_PARALLELISM = 3;
	private static final Duration TURN_SCORE_TIMEOUT = Duration.ofSeconds(60);
	private static final Duration STUCK_SWEEP_TIMEOUT = Duration.ofMinutes(10);
	private static final Duration STALE_REDISPATCH_THRESHOLD = Duration.ofMinutes(2);

	private final InterviewReportRepository interviewReportRepository;
	private final SessionMessageRepository sessionMessageRepository;
	private final InterviewSceneRepository interviewSceneRepository;
	private final PronunciationAssessmentClient pronunciationClient;
	private final AiProviderRegistry providerRegistry;
	private final RecordingStore interviewRecordingStore;
	private final Executor interviewEvaluationExecutor;
	private final ObjectMapper objectMapper;
	private final ObjectReader strictReader;
	private final Set<String> runningReportSessionIds =
			ConcurrentHashMap.newKeySet();
	/** 自动重试置位：任务体结束后（让出运行槽）立即重提交，防孤儿 PROCESSING。 */
	private final Set<String> retryPending =
			ConcurrentHashMap.newKeySet();
	private final ExecutorService audioScoringExecutor =
			Executors.newFixedThreadPool(AUDIO_SCORING_PARALLELISM, runnable -> {
				Thread thread = new Thread(
						runnable,
						"interview-audio-scoring");
				thread.setDaemon(true);
				return thread;
			});

	public InterviewReportCoordinator(
			InterviewReportRepository interviewReportRepository,
			SessionMessageRepository sessionMessageRepository,
			InterviewSceneRepository interviewSceneRepository,
			PronunciationAssessmentClient pronunciationClient,
			AiProviderRegistry providerRegistry,
			@Qualifier("interviewRecordingStore") RecordingStore interviewRecordingStore,
			@Qualifier("interviewEvaluationExecutor") Executor interviewEvaluationExecutor,
			ObjectMapper objectMapper) {
		this.interviewReportRepository = Objects.requireNonNull(
				interviewReportRepository,
				"interviewReportRepository must not be null");
		this.sessionMessageRepository = Objects.requireNonNull(
				sessionMessageRepository,
				"sessionMessageRepository must not be null");
		this.interviewSceneRepository = Objects.requireNonNull(
				interviewSceneRepository,
				"interviewSceneRepository must not be null");
		this.pronunciationClient = Objects.requireNonNull(
				pronunciationClient,
				"pronunciationClient must not be null");
		this.providerRegistry = Objects.requireNonNull(
				providerRegistry,
				"providerRegistry must not be null");
		this.interviewRecordingStore = Objects.requireNonNull(
				interviewRecordingStore,
				"interviewRecordingStore must not be null");
		this.interviewEvaluationExecutor = Objects.requireNonNull(
				interviewEvaluationExecutor,
				"interviewEvaluationExecutor must not be null");
		this.objectMapper = Objects.requireNonNull(
				objectMapper,
				"objectMapper must not be null");
		this.strictReader = objectMapper.reader()
				.with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
	}

	/** 提交报告任务（单跑守卫：已在跑则直接返回，防止双跑状态回归）。 */
	public void submit(String sessionId, String sceneId, String userId) {
		if (!runningReportSessionIds.add(sessionId)) {
			return;
		}
		try {
			interviewEvaluationExecutor.execute(() -> {
				try {
					process(sessionId, sceneId, userId);
				}
				finally {
					// 先让出运行槽，再在 autoRetry 已置位时立即重提交，
					// 避免"行已回 PROCESSING 但无任务在跑"的孤儿态。
					runningReportSessionIds.remove(sessionId);
					if (retryPending.remove(sessionId)) {
						submit(sessionId, sceneId, userId);
					}
				}
			});
		}
		catch (RejectedExecutionException exception) {
			runningReportSessionIds.remove(sessionId);
			LOGGER.warn(
					"interview report task rejected sessionId={} (queue full)",
					sessionId);
		}
	}

	/** GET report 读时惰性重派：PROCESSING 且过期 → 重新提交（跳过 running 集）。 */
	public void redispatchIfStale(String sessionId, String sceneId, String userId) {
		InterviewReportRecord record = interviewReportRepository
				.findById(sessionId)
				.orElse(null);
		if (record == null || record.status() != ReportStatus.PROCESSING) {
			return;
		}
		if (record.updatedAt() == null
				|| record.updatedAt().isBefore(
						OffsetDateTime.now().minus(STALE_REDISPATCH_THRESHOLD))) {
			submit(sessionId, sceneId, userId);
		}
	}

	/** 僵尸恢复：清扫滞留 PROCESSING 超时行（跳过 running 集）。 */
	@Scheduled(fixedDelayString = "${interview.report-sweep-fixed-delay:300000}")
	public void sweepStuckProcessing() {
		try {
			List<InterviewReportRecord> stuck = interviewReportRepository
					.findStuckProcessingBefore(
							OffsetDateTime.now().minus(STUCK_SWEEP_TIMEOUT));
			for (InterviewReportRecord record : stuck) {
				submit(
						record.sessionId(),
						record.sceneId(),
						record.userId());
			}
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
					"interview report sweep failed error={}",
					exception.getMessage());
		}
	}

	private void process(
			String sessionId,
			String sceneId,
			String userId) {
		try {
			List<LearnerMessageRecord> turns =
					sessionMessageRepository.findMessagesWithAudioObjectKeys(sessionId);
			AudioScoring audio = scoreAudioDimensions(sessionId, turns);
			if (audio.turnsWithAudio() > 0
					&& audio.providerFailedTurns() == audio.turnsWithAudio()) {
				throw new ReportTaskException(FailureReason.PROVIDER_RETRYABLE);
			}
			List<String> topics = readTopics(sceneId);
			LlmAssessment llm = assessTextDimensions(
					sessionMessageRepository.findMessages(sessionId),
					topics,
					audio.fluency(),
					audio.pronunciation());
			InterviewReportRecord completed = toCompletedRecord(
					sessionId,
					sceneId,
					userId,
					audio,
					llm,
					turns.size());
			interviewReportRepository.markCompleted(completed);
			LOGGER.info(
					"interview report completed sessionId={} scoredTurns={} totalTurns={} overall={}",
					sessionId,
					audio.scoredTurns(),
					turns.size(),
					completed.overallScore());
			buildAndStoreSessionAudio(sessionId, turns);
		}
		catch (ReportTaskException exception) {
			handleTaskFailure(sessionId, sceneId, userId, exception.reason());
		}
		catch (RuntimeException exception) {
			LOGGER.error(
					"interview report task failed sessionId={} error={}",
					sessionId,
					exception.getMessage());
			handleTaskFailure(
					sessionId,
					sceneId,
					userId,
					FailureReason.PROVIDER_RETRYABLE);
		}
	}

	private void handleTaskFailure(
			String sessionId,
			String sceneId,
			String userId,
			FailureReason reason) {
		try {
			interviewReportRepository.markFailed(sessionId, reason.code());
		}
		catch (RuntimeException exception) {
			LOGGER.error(
					"interview report markFailed failed sessionId={} error={}",
					sessionId,
					exception.getMessage());
			return;
		}
		LOGGER.warn(
				"interview report failed sessionId={} reason={}",
				sessionId,
				reason.code());
		if (reason.retryable()) {
			autoRetry(sessionId, sceneId, userId);
		}
	}

	private void autoRetry(
			String sessionId,
			String sceneId,
			String userId) {
		try {
			InterviewReportRecord record = interviewReportRepository
					.findById(sessionId)
					.orElse(null);
			if (record == null || record.status() != ReportStatus.FAILED
					|| record.retryCount() >= 1) {
				return;
			}
			if (interviewReportRepository.retryFromFailed(
					sessionId,
					record.retryCount())) {
				retryPending.add(sessionId);
			}
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
					"interview report auto-retry failed sessionId={} error={}",
					sessionId,
					exception.getMessage());
		}
	}

	private AudioScoring scoreAudioDimensions(
			String sessionId,
			List<LearnerMessageRecord> turns) {
		List<ScoringTask> tasks = new ArrayList<>();
		for (LearnerMessageRecord turn : turns) {
			if (turn.audioObjectKey() == null || turn.audioObjectKey().isBlank()) {
				continue;
			}
			byte[] audio = interviewRecordingStore.readAudio(
					sessionId,
					turn.audioObjectKey());
			if (audio == null || !isValidSpeechAudio(turn.content(), audio)) {
				continue;
			}
			tasks.add(new ScoringTask(turn, audio));
		}
		List<Future<TurnScore>> futures = new ArrayList<>();
		for (ScoringTask task : tasks) {
			futures.add(audioScoringExecutor.submit(
					() -> scoreTurn(sessionId, task)));
		}
		AtomicReference<BigDecimal> fluencySum = new AtomicReference<>();
		AtomicReference<BigDecimal> pronunciationSum = new AtomicReference<>();
		AtomicInteger scoredTurns = new AtomicInteger();
		AtomicInteger providerFailedTurns = new AtomicInteger();
		for (Future<TurnScore> future : futures) {
			try {
				TurnScore score = future.get(
						TURN_SCORE_TIMEOUT.toMillis(),
						TimeUnit.MILLISECONDS);
				if (score != null) {
					fluencySum.accumulateAndGet(
							score.fluency(),
							BigDecimal::add);
					pronunciationSum.accumulateAndGet(
							score.pronunciation(),
							BigDecimal::add);
					scoredTurns.incrementAndGet();
				}
			}
			catch (ExecutionException exception) {
				Throwable cause = exception.getCause();
				if (cause instanceof EvaluationException evaluationException
						&& isRetryable(evaluationException)) {
					providerFailedTurns.incrementAndGet();
				}
			}
			catch (Exception exception) {
				providerFailedTurns.incrementAndGet();
			}
		}
		BigDecimal fluency = scoredTurns.get() == 0
				? null
				: average(
						fluencySum.get(),
						scoredTurns.get());
		BigDecimal pronunciation = scoredTurns.get() == 0
				? null
				: average(
						pronunciationSum.get(),
						scoredTurns.get());
		return new AudioScoring(
				fluency,
				pronunciation,
				turns.size(),
				tasks.size(),
				providerFailedTurns.get(),
				scoredTurns.get());
	}

	private TurnScore scoreTurn(
			String sessionId,
			ScoringTask task) {
		PronunciationAssessmentResult assessment = pronunciationClient.evaluate(
				task.turn().content(),
				task.audio());
		try {
			TurnSpeechScoreCalculation calculation =
					TurnSpeechScoreCalculator.calculate(assessment);
			return new TurnScore(
					calculation.fluencyScore(),
					calculation.accuracyScore());
		}
		catch (EvaluationException exception) {
			// 无有效音素 → 该段降级（不算 provider 故障，供"全轮无有效语音"覆盖降级）。
			if (exception.errorCode()
					== EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE) {
				return null;
			}
			throw exception;
		}
	}

	private boolean isValidSpeechAudio(String transcript, byte[] audio) {
		if (transcript == null || transcript.isBlank()) {
			return false;
		}
		try {
			PcmWavValidator.validate(audio);
			return true;
		}
		catch (EvaluationException exception) {
			return false;
		}
	}

	private boolean isRetryable(EvaluationException exception) {
		return switch (exception.errorCode()) {
			case PROVIDER_NOT_CONFIGURED,
					PROVIDER_CALL_FAILED,
					PROVIDER_REJECTED,
					PROVIDER_RESPONSE_INVALID,
					PROVIDER_RESPONSE_INCOMPLETE,
					PROMPT_TEMPLATE_INVALID -> true;
			default -> false;
		};
	}

	private LlmAssessment assessTextDimensions(
			List<Message> dialogue,
			List<String> topics,
			BigDecimal fluency,
			BigDecimal pronunciation) {
		String prompt = buildLlmPrompt(dialogue, topics, fluency, pronunciation);
		String content;
		try {
			content = providerRegistry.executeLlmTaskRouted(prompt, null).response();
		}
		catch (RuntimeException exception) {
			throw new ReportTaskException(FailureReason.PROVIDER_RETRYABLE);
		}
		try {
			return parseLlmAssessment(content);
		}
		catch (RuntimeException exception) {
			throw new ReportTaskException(FailureReason.LLM_UNPARSEABLE);
		}
	}

	private String buildLlmPrompt(
			List<Message> dialogue,
			List<String> topics,
			BigDecimal fluency,
			BigDecimal pronunciation) {
		StringBuilder transcript = new StringBuilder();
		for (Message message : dialogue) {
			transcript.append(message.owner() == 0
							? "EXAMINER: "
							: "CANDIDATE: ")
					.append(message.content())
					.append('\n');
		}
		String topicList = topics == null || topics.isEmpty()
				? "(not available)"
				: topics.stream()
						.map(topic -> "- " + topic)
						.collect(java.util.stream.Collectors.joining("\n"));
		String fluencyValue = fluency == null
				? "null (insufficient valid speech)"
				: fluency.toPlainString();
		String pronunciationValue = pronunciation == null
				? "null (insufficient valid speech)"
				: pronunciation.toPlainString();
		return """
				You are evaluating a candidate's spoken English in a mock job interview.

				Interview topics covered:
				%s

				Full interview dialogue:
				%s

				Audio-derived dimension scores (computed from the candidate's recorded speech audio
				by a speech scoring engine):
				- fluency_score: %s
				- pronunciation_intelligibility_score: %s

				IMPORTANT EVIDENCE BOUNDARY: fluency and pronunciation intelligibility are derived
				from the audio evidence only. Do NOT infer them from the transcript text. Use the
				provided scores and write evaluation/advice consistent with them.

				Evaluate the remaining dimensions from the transcript only:
				- LOGIC_COHERENCE (0-100)
				- GRAMMAR_CONTROL (0-100)
				- VOCABULARY_EXPRESSION (0-100)

				Produce an overall_score (0-100) as your comprehensive judgment across all five
				dimensions, and a short summary narrative of the candidate's spoken English.

				Return exactly one JSON object and no Markdown or explanatory prose.
				The JSON shape must be:
				{
				  "logic_coherence": {"score": 80, "evaluation": "...", "advice": "..."},
				  "grammar_control": {"score": 80, "evaluation": "...", "advice": "..."},
				  "vocabulary_expression": {"score": 80, "evaluation": "...", "advice": "..."},
				  "fluency": {"evaluation": "...", "advice": "..."},
				  "pronunciation_intelligibility": {"evaluation": "...", "advice": "..."},
				  "overall_score": 80,
				  "summary": "..."
				}
				""".formatted(
						topicList,
						transcript.toString().strip(),
						fluencyValue,
						pronunciationValue);
	}

	private LlmAssessment parseLlmAssessment(String content) {
		JsonNode root = strictReader.readTree(unwrapJsonFence(content));
		if (root == null || !root.isObject()) {
			throw new IllegalArgumentException("LLM assessment is not a JSON object");
		}
		JsonNode logic = root.path("logic_coherence");
		JsonNode grammar = root.path("grammar_control");
		JsonNode vocabulary = root.path("vocabulary_expression");
		JsonNode fluency = root.path("fluency");
		JsonNode pronunciation = root.path("pronunciation_intelligibility");
		BigDecimal overall = requiredScore(root.path("overall_score"), "overall_score");
		String summary = requiredText(root.path("summary"), "summary");
		return new LlmAssessment(
				requiredScore(logic.path("score"), "logic_coherence.score"),
				requiredText(logic.path("evaluation"), "logic_coherence.evaluation"),
				requiredText(logic.path("advice"), "logic_coherence.advice"),
				requiredScore(grammar.path("score"), "grammar_control.score"),
				requiredText(grammar.path("evaluation"), "grammar_control.evaluation"),
				requiredText(grammar.path("advice"), "grammar_control.advice"),
				requiredScore(vocabulary.path("score"), "vocabulary_expression.score"),
				requiredText(vocabulary.path("evaluation"), "vocabulary_expression.evaluation"),
				requiredText(vocabulary.path("advice"), "vocabulary_expression.advice"),
				requiredText(fluency.path("evaluation"), "fluency.evaluation"),
				requiredText(fluency.path("advice"), "fluency.advice"),
				requiredText(pronunciation.path("evaluation"), "pronunciation_intelligibility.evaluation"),
				requiredText(pronunciation.path("advice"), "pronunciation_intelligibility.advice"),
				overall,
				summary);
	}

	private BigDecimal requiredScore(JsonNode node, String field) {
		if (!node.isNumber()) {
			throw new IllegalArgumentException("missing numeric field " + field);
		}
		BigDecimal score = node.decimalValue();
		if (score.compareTo(BigDecimal.ZERO) < 0
				|| score.compareTo(new BigDecimal("100")) > 0) {
			throw new IllegalArgumentException("score out of range " + field);
		}
		return score.setScale(1, RoundingMode.HALF_UP);
	}

	private String requiredText(JsonNode node, String field) {
		if (!node.isTextual() || node.asString("").isBlank()) {
			throw new IllegalArgumentException("missing text field " + field);
		}
		return node.asString("").strip();
	}

	private String unwrapJsonFence(String content) {
		String value = content == null ? "" : content.strip();
		if (value.startsWith("```json\n") && value.endsWith("\n```")) {
			value = value.substring(8, value.length() - 4).strip();
		}
		if (value.isBlank() || value.contains("```")) {
			throw new IllegalArgumentException("LLM response is not a bare JSON object");
		}
		return value;
	}

	private InterviewReportRecord toCompletedRecord(
			String sessionId,
			String sceneId,
			String userId,
			AudioScoring audio,
			LlmAssessment llm,
			int totalTurns) {
		String coverageNote = audio.scoredTurns() == 0
				? " 有效语音不足，发音与流利度维度未能评分。"
				: audio.scoredTurns() < totalTurns
						? " 部分轮次缺少有效语音，发音与流利度基于可用轮次。"
						: "";
		String summary = llm.summary() + coverageNote;
		return new InterviewReportRecord(
				sessionId,
				sceneId,
				userId,
				ReportStatus.COMPLETED,
				llm.overall(),
				summary,
				audio.fluency(),
				llm.fluencyEvaluation(),
				llm.fluencyAdvice(),
				audio.pronunciation(),
				llm.pronunciationEvaluation(),
				llm.pronunciationAdvice(),
				llm.logicScore(),
				llm.logicEvaluation(),
				llm.logicAdvice(),
				llm.grammarScore(),
				llm.grammarEvaluation(),
				llm.grammarAdvice(),
				llm.vocabularyScore(),
				llm.vocabularyEvaluation(),
				llm.vocabularyAdvice(),
				0,
				null,
				null,
				null);
	}

	private List<String> readTopics(String sceneId) {
		return interviewSceneRepository.findById(sceneId)
				.map(definition -> parseStoredTopics(definition.interviewContextJson()))
				.orElse(List.of());
	}

	private List<String> parseStoredTopics(String interviewContextJson) {
		try {
			JsonNode root = objectMapper.readTree(interviewContextJson);
			JsonNode topics = root.path("interviewTopics");
			if (!topics.isArray()) {
				return List.of();
			}
			List<String> values = new ArrayList<>();
			for (JsonNode topic : topics) {
				if (topic.isTextual() && !topic.asString("").isBlank()) {
					values.add(topic.asString("").strip());
				}
			}
			return List.copyOf(values);
		}
		catch (RuntimeException exception) {
			return List.of();
		}
	}

	private void buildAndStoreSessionAudio(
			String sessionId,
			List<LearnerMessageRecord> turns) {
		try {
			List<byte[]> validSegments = new ArrayList<>();
			for (LearnerMessageRecord turn : turns) {
				if (turn.audioObjectKey() == null || turn.audioObjectKey().isBlank()) {
					continue;
				}
				byte[] audio = interviewRecordingStore.readAudio(
						sessionId,
						turn.audioObjectKey());
				if (audio == null) {
					continue;
				}
				try {
					PcmWavValidator.validate(audio);
					validSegments.add(audio);
				}
				catch (EvaluationException exception) {
					// 无法归一/无效段降级：只拼有效段。
				}
			}
			if (validSegments.isEmpty()) {
				return;
			}
			interviewRecordingStore.storeSessionAudio(
					sessionId,
					concatWav(validSegments));
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
					"interview session audio concatenation skipped sessionId={} error={}",
					sessionId,
					exception.getMessage());
		}
	}

	/** 拼接多个已校验 16kHz mono 16-bit PCM WAV 的 data chunk 为单个 session.wav。 */
	private byte[] concatWav(List<byte[]> segments) {
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		for (byte[] wav : segments) {
			data.writeBytes(extractDataChunk(wav));
		}
		byte[] pcm = data.toByteArray();
		ByteBuffer buffer = ByteBuffer.allocate(44 + pcm.length);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
		buffer.putInt(36 + pcm.length);
		buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
		buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
		buffer.putInt(16);
		buffer.putShort((short) 1);
		buffer.putShort((short) 1);
		buffer.putInt(16_000);
		buffer.putInt(32_000);
		buffer.putShort((short) 2);
		buffer.putShort((short) 16);
		buffer.put("data".getBytes(StandardCharsets.US_ASCII));
		buffer.putInt(pcm.length);
		buffer.put(pcm);
		return buffer.array();
	}

	private byte[] extractDataChunk(byte[] wav) {
		int offset = 12;
		while (offset + 8 <= wav.length) {
			String id = new String(wav, offset, 4, StandardCharsets.US_ASCII);
			int size = (wav[offset + 4] & 0xff)
					| ((wav[offset + 5] & 0xff) << 8)
					| ((wav[offset + 6] & 0xff) << 16)
					| ((wav[offset + 7] & 0xff) << 24);
			if ("data".equals(id)) {
				if (offset + 8 + size > wav.length) {
					throw new IllegalArgumentException("truncated data chunk");
				}
				return Arrays.copyOfRange(
						wav,
						offset + 8,
						offset + 8 + size);
			}
			offset += 8 + size + (size & 1);
		}
		throw new IllegalArgumentException("missing data chunk");
	}

	private BigDecimal average(BigDecimal sum, int count) {
		return sum.divide(
				BigDecimal.valueOf(count),
				1,
				RoundingMode.HALF_UP);
	}

	/** 对外只读投影（服务层复用）：由记录行生成响应。 */
	public InterviewReportResponse toResponse(InterviewReportRecord record) {
		List<InterviewDimensionScore> dimensions = List.of(
				new InterviewDimensionScore(
						InterviewDimension.FLUENCY,
						record.fluencyScore(),
						record.fluencyEvaluation(),
						record.fluencyAdvice()),
				new InterviewDimensionScore(
						InterviewDimension.PRONUNCIATION_INTELLIGIBILITY,
						record.pronunciationIntelligibilityScore(),
						record.pronunciationIntelligibilityEvaluation(),
						record.pronunciationIntelligibilityAdvice()),
				new InterviewDimensionScore(
						InterviewDimension.LOGIC_COHERENCE,
						record.logicCoherenceScore(),
						record.logicCoherenceEvaluation(),
						record.logicCoherenceAdvice()),
				new InterviewDimensionScore(
						InterviewDimension.GRAMMAR_CONTROL,
						record.grammarControlScore(),
						record.grammarControlEvaluation(),
						record.grammarControlAdvice()),
				new InterviewDimensionScore(
						InterviewDimension.VOCABULARY_EXPRESSION,
						record.vocabularyExpressionScore(),
						record.vocabularyExpressionEvaluation(),
						record.vocabularyExpressionAdvice()));
		InterviewReport report = record.status() == ReportStatus.COMPLETED
				? new InterviewReport(
						record.sessionId(),
						record.sceneId(),
						record.overallScore(),
						record.summary(),
						dimensions,
						record.updatedAt() == null
								? null
								: record.updatedAt().toInstant())
				: null;
		return new InterviewReportResponse(
				record.sessionId(),
				record.sceneId(),
				record.status(),
				report,
				record.failureReason());
	}

	private enum FailureReason {
		PROVIDER_RETRYABLE(true),
		AUDIO_MISSING(false),
		AUDIO_EXPIRED(false),
		LLM_UNPARSEABLE(false);

		private final boolean retryable;

		FailureReason(boolean retryable) {
			this.retryable = retryable;
		}

		String code() {
			return name();
		}

		boolean retryable() {
			return retryable;
		}
	}

	private static final class ReportTaskException extends RuntimeException {
		private final FailureReason reason;

		private ReportTaskException(FailureReason reason) {
			super(reason.code());
			this.reason = reason;
		}

		private FailureReason reason() {
			return reason;
		}
	}

	private record ScoringTask(LearnerMessageRecord turn, byte[] audio) {
	}

	private record TurnScore(BigDecimal fluency, BigDecimal pronunciation) {
	}

	private record AudioScoring(
			BigDecimal fluency,
			BigDecimal pronunciation,
			int totalTurns,
			int turnsWithAudio,
			int providerFailedTurns,
			int scoredTurns) {
	}

	private record LlmAssessment(
			BigDecimal logicScore,
			String logicEvaluation,
			String logicAdvice,
			BigDecimal grammarScore,
			String grammarEvaluation,
			String grammarAdvice,
			BigDecimal vocabularyScore,
			String vocabularyEvaluation,
			String vocabularyAdvice,
			String fluencyEvaluation,
			String fluencyAdvice,
			String pronunciationEvaluation,
			String pronunciationAdvice,
			BigDecimal overall,
			String summary) {
	}
}
