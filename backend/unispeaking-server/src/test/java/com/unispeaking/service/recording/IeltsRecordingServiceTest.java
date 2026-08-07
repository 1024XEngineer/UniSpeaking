package com.unispeaking.service.recording;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.config.IeltsRecordingProperties;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.service.auth.AuthService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IeltsRecordingServiceTest {

	@TempDir
	Path tempDirectory;

	@Test
	void storesAndLoadsOwnedRecordingThenDeletesIt() throws Exception {
		UUID userId = UUID.randomUUID();
		PracticeSessionRepository repository = mock(
				PracticeSessionRepository.class);
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(null)).thenReturn(userId.toString());
		when(repository.findBySessionId("ielts_session_1"))
				.thenReturn(Optional.of(new PracticeSessionRecord(
						"ielts_session_1",
						userId,
						"ielts_1",
						SceneType.IELTS_SCENE,
						SessionStatus.COMPLETED,
						Instant.now(),
						Instant.now())));
		IeltsRecordingService service = service(repository, authService);
		byte[] audio = new byte[] {82, 73, 70, 70, 1, 2, 3};

		String url = service.store("ielts_session_1", 1, audio);

		assertEquals(
				"/api/ielts/recordings/ielts_session_1/turn-1.wav",
				url);
		assertArrayEquals(
				audio,
				service.loadOwned("ielts_session_1", "turn-1.wav")
						.getInputStream()
						.readAllBytes());
		service.delete("ielts_session_1", 1);
		assertFalse(Files.exists(
				tempDirectory.resolve("ielts_session_1/turn-1.wav")));
	}

	@Test
	void rejectsTraversalAndAnotherUsersRecording() {
		PracticeSessionRepository repository = mock(
				PracticeSessionRepository.class);
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(null)).thenReturn(UUID.randomUUID().toString());
		when(repository.findBySessionId("ielts_session_1"))
				.thenReturn(Optional.of(new PracticeSessionRecord(
						"ielts_session_1",
						UUID.randomUUID(),
						"ielts_1",
						SceneType.IELTS_SCENE,
						SessionStatus.COMPLETED,
						Instant.now(),
						Instant.now())));
		IeltsRecordingService service = service(repository, authService);

		assertThrows(BusinessException.class,
				() -> service.store("../outside", 1, new byte[] {1}));
		assertThrows(BusinessException.class,
				() -> service.loadOwned("ielts_session_1", "../secret.wav"));
		assertThrows(BusinessException.class,
				() -> service.loadOwned("ielts_session_1", "turn-1.wav"));
	}

	private IeltsRecordingService service(
			PracticeSessionRepository repository,
			AuthService authService) {
		IeltsRecordingProperties properties = new IeltsRecordingProperties();
		properties.setDirectory(tempDirectory.toString());
		return new IeltsRecordingService(properties, repository, authService);
	}
}
