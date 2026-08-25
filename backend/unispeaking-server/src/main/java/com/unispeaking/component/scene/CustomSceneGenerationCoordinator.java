package com.unispeaking.component.scene;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.util.SceneIdGenerator;
import com.unispeaking.domain.dto.scene.CustomSceneGenerationResponse;
import com.unispeaking.domain.dto.scene.CustomSceneGenerationTaskResponse;
import com.unispeaking.domain.dto.scene.CustomSceneRequest;
import com.unispeaking.domain.po.scene.CustomSceneGenerationTask;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.task.AsyncTaskStatus;
import com.unispeaking.infrastructure.persistence.repository.scene.CustomSceneGenerationTaskRepository;
import com.unispeaking.provider.AiInvocationContext;
import com.unispeaking.provider.AiInvocationContexts;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.CustomSceneService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CustomSceneGenerationCoordinator {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			CustomSceneGenerationCoordinator.class);
	private static final Duration STUCK_TASK_TIMEOUT = Duration.ofMinutes(10);
	private static final Duration STALE_REDISPATCH_THRESHOLD = Duration.ofMinutes(2);
	private static final String FAILED_MESSAGE = "场景生成失败，请稍后重试";

	private final CustomSceneGenerationTaskRepository taskRepository;
	private final CustomSceneService customSceneService;
	private final AuthService authService;
	private final Executor executor;
	private final ObjectMapper objectMapper;
	private final Set<UUID> runningTaskIds = ConcurrentHashMap.newKeySet();

	public CustomSceneGenerationCoordinator(
			CustomSceneGenerationTaskRepository taskRepository,
			CustomSceneService customSceneService,
			AuthService authService,
			@Qualifier("customSceneGenerationExecutor") Executor executor,
			ObjectMapper objectMapper) {
		this.taskRepository = taskRepository;
		this.customSceneService = customSceneService;
		this.authService = authService;
		this.executor = executor;
		this.objectMapper = objectMapper;
	}

	public CustomSceneGenerationTaskResponse submit(CustomSceneRequest request) {
		String userId = authService.requireUserId(request.userId());
		OffsetDateTime now = OffsetDateTime.now();
		CustomSceneGenerationTask task = new CustomSceneGenerationTask(
				UUID.randomUUID(),
				userId,
				SceneIdGenerator.generate(SceneType.CUSTOM_SCENE),
				request.sceneInput().trim(),
				request.userPreference(),
				AsyncTaskStatus.PROCESSING,
				null,
				null,
				now,
				now);
		taskRepository.create(task);
		dispatch(task);
		return toResponse(taskRepository.findById(task.taskId()).orElse(task));
	}

	public CustomSceneGenerationTaskResponse get(UUID taskId) {
		String userId = authService.requireUserId(null);
		CustomSceneGenerationTask task = requireOwnedTask(taskId, userId);
		if (task.status() == AsyncTaskStatus.PROCESSING
				&& (task.updatedAt() == null
						|| task.updatedAt().isBefore(
								OffsetDateTime.now().minus(STALE_REDISPATCH_THRESHOLD)))) {
			dispatch(task);
		}
		return toResponse(task);
	}

	@Scheduled(fixedDelayString = "${custom-scene.generation-sweep-fixed-delay:300000}")
	public void sweepStuckTasks() {
		try {
			List<CustomSceneGenerationTask> stuck = taskRepository.findProcessingBefore(
					OffsetDateTime.now().minus(STUCK_TASK_TIMEOUT));
			stuck.forEach(this::dispatch);
		}
		catch (RuntimeException exception) {
			LOGGER.warn("custom scene generation task sweep failed");
		}
	}

	private void dispatch(CustomSceneGenerationTask task) {
		if (!runningTaskIds.add(task.taskId())) return;
		try {
			executor.execute(() -> {
				try {
					process(task);
				}
				finally {
					runningTaskIds.remove(task.taskId());
				}
			});
		}
		catch (RejectedExecutionException exception) {
			runningTaskIds.remove(task.taskId());
			taskRepository.markFailed(task.taskId(), "生成任务繁忙，请稍后重试");
			LOGGER.warn("custom scene generation task rejected taskId={}", task.taskId());
		}
	}

	private void process(CustomSceneGenerationTask task) {
		try {
			CustomSceneRequest request = new CustomSceneRequest(
					task.userId(),
					task.userPreference(),
					task.sceneInput(),
					null,
					null,
					null,
					null,
					null);
			CustomSceneGenerationResponse result = AiInvocationContexts.call(
					AiInvocationContext.create(
							task.userId(),
							task.sceneId(),
							"custom_scene_generation"),
					() -> customSceneService.generateForUser(
							request,
							task.userId(),
							task.sceneId()));
			taskRepository.markCompleted(
					task.taskId(),
					objectMapper.writeValueAsString(result));
		}
		catch (RuntimeException exception) {
			taskRepository.markFailed(task.taskId(), FAILED_MESSAGE);
			LOGGER.error(
					"custom scene generation task failed taskId={} sceneId={} errorType={}",
					task.taskId(),
					task.sceneId(),
					exception.getClass().getSimpleName());
		}
	}

	private CustomSceneGenerationTask requireOwnedTask(UUID taskId, String userId) {
		CustomSceneGenerationTask task = taskRepository.findById(taskId)
				.orElseThrow(() -> new BusinessException(
						"CUSTOM_SCENE_GENERATION_TASK_NOT_FOUND",
						"场景生成任务不存在"));
		if (!task.userId().equals(userId)) {
			throw new BusinessException(
					"CUSTOM_SCENE_GENERATION_TASK_NOT_FOUND",
					"场景生成任务不存在");
		}
		return task;
	}

	private CustomSceneGenerationTaskResponse toResponse(
			CustomSceneGenerationTask task) {
		CustomSceneGenerationResponse result = null;
		if (task.status() == AsyncTaskStatus.COMPLETED) {
			try {
				result = objectMapper.readValue(
						task.resultJson(),
						CustomSceneGenerationResponse.class);
			}
			catch (RuntimeException exception) {
				throw new BusinessException(
						"CUSTOM_SCENE_GENERATION_RESULT_INVALID",
						"场景生成结果读取失败");
			}
		}
		return new CustomSceneGenerationTaskResponse(
				task.taskId(),
				task.sceneId(),
				task.status(),
				result,
				task.failureReason());
	}
}
