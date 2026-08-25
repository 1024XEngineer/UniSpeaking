package com.unispeaking.component.evaluation;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationResult;
import com.unispeaking.domain.dto.evaluation.IeltsEvaluationTaskResponse;
import com.unispeaking.domain.dto.evaluation.IeltsPartEvaluation;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.domain.vo.task.AsyncTaskStatus;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsEvaluationEntity;
import com.unispeaking.infrastructure.persistence.entity.evaluation.IeltsPartEvaluationEntity;
import com.unispeaking.infrastructure.persistence.repository.evaluation.IeltsEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.service.auth.AuthService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class IeltsEvaluationCoordinator {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			IeltsEvaluationCoordinator.class);
	private static final Duration STALE_REDISPATCH_THRESHOLD = Duration.ofMinutes(2);
	private static final String FAILED_MESSAGE = "IELTS 评分失败，请稍后重试";

	private final EvaluationProcessor evaluationProcessor;
	private final IeltsEvaluationRepository evaluationRepository;
	private final IeltsPracticeRepository practiceRepository;
	private final PracticeSessionRepository sessionRepository;
	private final AuthService authService;
	private final Executor executor;
	private final Set<String> runningTaskKeys = ConcurrentHashMap.newKeySet();

	public IeltsEvaluationCoordinator(
			EvaluationProcessor evaluationProcessor,
			IeltsEvaluationRepository evaluationRepository,
			IeltsPracticeRepository practiceRepository,
			PracticeSessionRepository sessionRepository,
			AuthService authService,
			@Qualifier("ieltsEvaluationExecutor") Executor executor) {
		this.evaluationProcessor = evaluationProcessor;
		this.evaluationRepository = evaluationRepository;
		this.practiceRepository = practiceRepository;
		this.sessionRepository = sessionRepository;
		this.authService = authService;
		this.executor = executor;
	}

	public IeltsEvaluationTaskResponse submit(String ieltsId, String sessionId) {
		String userId = authService.requireUserId(null);
		TaskScope scope = resolveScope(ieltsId, sessionId, userId);
		if (scope.finalTask()) {
			evaluationRepository.ensureFinalPending(ieltsId);
		}
		else {
			evaluationRepository.ensurePartPending(
					ieltsId,
					sessionId,
					scope.part());
		}
		dispatch(scope);
		return response(scope);
	}

	public IeltsEvaluationTaskResponse get(String ieltsId, String sessionId) {
		String userId = authService.requireUserId(null);
		TaskScope scope = resolveScope(ieltsId, sessionId, userId);
		IeltsEvaluationTaskResponse response = response(scope);
		if (response.status() == AsyncTaskStatus.PROCESSING
				&& isStale(scope)) {
			dispatch(scope);
		}
		return response;
	}

	private void dispatch(TaskScope scope) {
		if (isCompleted(scope) || !runningTaskKeys.add(scope.key())) return;
		try {
			executor.execute(() -> {
				try {
					process(scope);
				}
				finally {
					runningTaskKeys.remove(scope.key());
				}
			});
		}
		catch (RejectedExecutionException exception) {
			runningTaskKeys.remove(scope.key());
			markFailed(scope, "评分任务繁忙，请稍后重试");
			LOGGER.warn("IELTS evaluation task rejected taskKey={}", scope.key());
		}
	}

	private void process(TaskScope scope) {
		try {
			evaluationProcessor.generateIeltsEvaluationForUser(
					scope.ieltsId(),
					scope.sessionId(),
					scope.userId());
		}
		catch (RuntimeException exception) {
			markFailed(scope, FAILED_MESSAGE);
			LOGGER.error(
					"IELTS evaluation task failed taskKey={} errorType={}",
					scope.key(),
					exception.getClass().getSimpleName());
		}
	}

	private TaskScope resolveScope(
			String ieltsId,
			String sessionId,
			String userId) {
		IeltsPracticeRecord practice = practiceRepository.findPractice(ieltsId)
				.orElseThrow(() -> new EvaluationException(
						EvaluationErrorCode.SESSION_NOT_FOUND));
		if (!practice.userId().toString().equals(userId)) {
			throw new EvaluationException(EvaluationErrorCode.SESSION_NOT_FOUND);
		}
		List<PracticeSessionRecord> sessions = sessionRepository.findBySceneId(ieltsId)
				.stream()
				.filter(session -> session.sceneType() == SceneType.IELTS_SCENE)
				.filter(session -> session.status() == SessionStatus.COMPLETED)
				.toList();
		int sessionIndex = -1;
		for (int index = 0; index < sessions.size(); index++) {
			PracticeSessionRecord session = sessions.get(index);
			if (session.sessionId().equals(sessionId)
					&& session.userId().toString().equals(userId)) {
				sessionIndex = index;
				break;
			}
		}
		if (sessionIndex < 0) {
			throw new EvaluationException(EvaluationErrorCode.SESSION_NOT_FOUND);
		}
		boolean finalTask = practice.mode() == IeltsMode.MOCK_TEST && sessionIndex >= 2;
		IeltsPart part = finalTask
				? IeltsPart.PART_3
				: practice.selectedPart() == null
				? partByIndex(sessionIndex)
				: practice.selectedPart();
		return new TaskScope(ieltsId, sessionId, userId, part, finalTask);
	}

	private IeltsEvaluationTaskResponse response(TaskScope scope) {
		if (scope.finalTask()) {
			IeltsEvaluationEntity entity = evaluationRepository.findFinal(scope.ieltsId())
					.orElseThrow(() -> new EvaluationException(
							EvaluationErrorCode.RESULT_NOT_FOUND));
			return new IeltsEvaluationTaskResponse(
					scope.ieltsId(),
					scope.sessionId(),
					status(entity.getEvaluationStatus()),
					"COMPLETED".equals(entity.getEvaluationStatus())
							? toFinalResult(entity)
							: null,
					entity.getFailureReason());
		}
		IeltsPartEvaluationEntity entity = evaluationRepository
				.findPart(scope.sessionId())
				.orElseThrow(() -> new EvaluationException(
						EvaluationErrorCode.RESULT_NOT_FOUND));
		return new IeltsEvaluationTaskResponse(
				scope.ieltsId(),
				scope.sessionId(),
				status(entity.getEvaluationStatus()),
				"COMPLETED".equals(entity.getEvaluationStatus())
						? toPartResult(entity)
						: null,
				entity.getFailureReason());
	}

	private boolean isCompleted(TaskScope scope) {
		return scope.finalTask()
				? evaluationRepository.findFinal(scope.ieltsId())
						.map(entity -> "COMPLETED".equals(entity.getEvaluationStatus()))
						.orElse(false)
				: evaluationRepository.findPart(scope.sessionId())
						.map(entity -> "COMPLETED".equals(entity.getEvaluationStatus()))
						.orElse(false);
	}

	private boolean isStale(TaskScope scope) {
		OffsetDateTime updatedAt = scope.finalTask()
				? evaluationRepository.findFinal(scope.ieltsId())
						.map(IeltsEvaluationEntity::getUpdatedAt)
						.orElse(null)
				: evaluationRepository.findPart(scope.sessionId())
						.map(IeltsPartEvaluationEntity::getUpdatedAt)
						.orElse(null);
		return updatedAt == null
				|| updatedAt.isBefore(
						OffsetDateTime.now().minus(STALE_REDISPATCH_THRESHOLD));
	}

	private void markFailed(TaskScope scope, String reason) {
		if (scope.finalTask()) {
			evaluationRepository.markFinalFailed(scope.ieltsId(), reason);
		}
		else {
			evaluationRepository.markPartFailed(scope.sessionId(), reason);
		}
	}

	private IeltsEvaluationResult toPartResult(IeltsPartEvaluationEntity entity) {
		return new IeltsEvaluationResult(
				IeltsPart.valueOf(entity.getPart()),
				"DIAGNOSTIC",
				null,
				entity.getFluencyCoherenceScore(),
				entity.getLexicalResourceScore(),
				entity.getGrammaticalRangeAccuracyScore(),
				entity.getPronunciationScore(),
				entity.getSummary(),
				values(entity.getStrengths()),
				values(entity.getImprovements()),
				List.of(),
				values(entity.getRecommendedExpressions()),
				entity.getFluencyCoherenceReason(),
				entity.getLexicalResourceReason(),
				entity.getGrammaticalRangeAccuracyReason(),
				entity.getPronunciationReason());
	}

	private IeltsEvaluationResult toFinalResult(IeltsEvaluationEntity entity) {
		List<IeltsPartEvaluation> parts = evaluationRepository.findParts(entity.getIeltsId())
				.stream()
				.map(this::toPartEvaluation)
				.toList();
		return new IeltsEvaluationResult(
				null,
				"FINAL",
				entity.getOverallBandScore(),
				entity.getFluencyCoherenceScore(),
				entity.getLexicalResourceScore(),
				entity.getGrammaticalRangeAccuracyScore(),
				entity.getPronunciationScore(),
				entity.getSummary(),
				values(entity.getStrengths()),
				values(entity.getImprovements()),
				parts,
				values(entity.getRecommendedExpressions()),
				entity.getFluencyCoherenceReason(),
				entity.getLexicalResourceReason(),
				entity.getGrammaticalRangeAccuracyReason(),
				entity.getPronunciationReason());
	}

	private IeltsPartEvaluation toPartEvaluation(IeltsPartEvaluationEntity entity) {
		return new IeltsPartEvaluation(
				IeltsPart.valueOf(entity.getPart()),
				entity.getFluencyCoherenceScore(),
				entity.getLexicalResourceScore(),
				entity.getGrammaticalRangeAccuracyScore(),
				entity.getPronunciationScore(),
				entity.getSummary(),
				values(entity.getStrengths()),
				values(entity.getImprovements()),
				values(entity.getRecommendedExpressions()),
				entity.getFluencyCoherenceReason(),
				entity.getLexicalResourceReason(),
				entity.getGrammaticalRangeAccuracyReason(),
				entity.getPronunciationReason());
	}

	private List<String> values(String[] values) {
		return values == null ? List.of() : Arrays.asList(values);
	}

	private AsyncTaskStatus status(String status) {
		return switch (status) {
			case "PENDING" -> AsyncTaskStatus.PROCESSING;
			case "COMPLETED" -> AsyncTaskStatus.COMPLETED;
			case "FAILED" -> AsyncTaskStatus.FAILED;
			default -> throw new EvaluationException(EvaluationErrorCode.RESULT_INCOMPLETE);
		};
	}

	private IeltsPart partByIndex(int index) {
		return switch (index) {
			case 0 -> IeltsPart.PART_1;
			case 1 -> IeltsPart.PART_2;
			case 2 -> IeltsPart.PART_3;
			default -> throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		};
	}

	private record TaskScope(
			String ieltsId,
			String sessionId,
			String userId,
			IeltsPart part,
			boolean finalTask) {
		private String key() {
			return (finalTask ? "FINAL:" + ieltsId : "PART:" + sessionId);
		}
	}
}
