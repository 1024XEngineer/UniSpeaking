package com.unispeaking.component.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.scene.CustomSceneGenerationResponse;
import com.unispeaking.domain.dto.scene.CustomSceneRequest;
import com.unispeaking.domain.po.scene.CustomSceneGenerationTask;
import com.unispeaking.domain.vo.task.AsyncTaskStatus;
import com.unispeaking.infrastructure.persistence.repository.scene.CustomSceneGenerationTaskRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.CustomSceneService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CustomSceneGenerationCoordinatorTest {

	private static final String USER_ID = "3d8f80be-6390-4db9-a6cf-c10a0145d4c3";

	private final CustomSceneGenerationTaskRepository repository =
			mock(CustomSceneGenerationTaskRepository.class);
	private final CustomSceneService sceneService = mock(CustomSceneService.class);
	private final AuthService authService = mock(AuthService.class);
	private final AtomicReference<CustomSceneGenerationTask> stored =
			new AtomicReference<>();
	private CustomSceneGenerationCoordinator coordinator;

	@BeforeEach
	void setUp() {
		when(authService.requireUserId(
				org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(USER_ID);
		doAnswer(invocation -> {
			stored.set(invocation.getArgument(0));
			return null;
		}).when(repository).create(any());
		when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(
				stored.get()));
		doAnswer(invocation -> {
			CustomSceneGenerationTask task = stored.get();
			stored.set(copy(
					task,
					AsyncTaskStatus.COMPLETED,
					invocation.getArgument(1),
					null));
			return null;
		}).when(repository).markCompleted(any(), anyString());
		doAnswer(invocation -> {
			CustomSceneGenerationTask task = stored.get();
			stored.set(copy(
					task,
					AsyncTaskStatus.FAILED,
					null,
					invocation.getArgument(1)));
			return null;
		}).when(repository).markFailed(any(), anyString());
		coordinator = new CustomSceneGenerationCoordinator(
				repository,
				sceneService,
				authService,
				Runnable::run,
				new ObjectMapper());
	}

	@Test
	void completesPersistedTaskAndReturnsOriginalSceneResult() {
		when(sceneService.generateForUser(any(), eq(USER_ID), anyString()))
				.thenAnswer(invocation -> result(invocation.getArgument(2)));

		var response = coordinator.submit(request());

		assertEquals(AsyncTaskStatus.COMPLETED, response.status());
		assertEquals(response.sceneId(), response.result().sceneId());
		assertEquals("Airport check-in", response.result().title());
	}

	@Test
	void marksTaskFailedWithoutExposingProviderException() {
		when(sceneService.generateForUser(any(), eq(USER_ID), anyString()))
				.thenThrow(new IllegalStateException("provider secret"));

		var response = coordinator.submit(request());

		assertEquals(AsyncTaskStatus.FAILED, response.status());
		assertEquals("场景生成失败，请稍后重试", response.failureReason());
	}

	@Test
	void hidesAnotherUsersTask() {
		when(sceneService.generateForUser(any(), eq(USER_ID), anyString()))
				.thenAnswer(invocation -> result(invocation.getArgument(2)));
		var submitted = coordinator.submit(request());
		when(authService.requireUserId(null)).thenReturn(UUID.randomUUID().toString());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> coordinator.get(submitted.taskId()));

		assertEquals("CUSTOM_SCENE_GENERATION_TASK_NOT_FOUND", exception.code());
	}

	private CustomSceneRequest request() {
		return new CustomSceneRequest(
				null,
				null,
				"Airport check-in",
				null,
				null,
				null,
				null,
				null);
	}

	private CustomSceneGenerationResponse result(String sceneId) {
		return new CustomSceneGenerationResponse(
				sceneId,
				"Airport check-in",
				"Travel",
				"An airport counter",
				"Agent",
				"Passenger",
				"Complete check-in",
				8,
				List.of(),
				List.of(),
				List.of(),
				"prompt");
	}

	private CustomSceneGenerationTask copy(
			CustomSceneGenerationTask task,
			AsyncTaskStatus status,
			String resultJson,
			String failureReason) {
		return new CustomSceneGenerationTask(
				task.taskId(),
				task.userId(),
				task.sceneId(),
				task.sceneInput(),
				task.userPreference(),
				status,
				resultJson,
				failureReason,
				task.createdAt(),
				task.updatedAt());
	}
}
