package com.unispeaking.component.recording;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.config.IeltsRecordingProperties;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.service.auth.AuthService;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class IeltsRecordingStore {

	private static final Pattern SAFE_SESSION_ID = Pattern.compile(
			"[A-Za-z0-9_-]{1,160}");
	private static final Pattern SAFE_FILE_NAME = Pattern.compile(
			"turn-[1-9][0-9]*\\.wav");
	private static final String API_PREFIX = "/api/ielts/recordings/";

	private final Path root;
	private final long maxBytes;
	private final PracticeSessionRepository practiceSessionRepository;
	private final AuthService authService;

	public IeltsRecordingStore(
			IeltsRecordingProperties properties,
			PracticeSessionRepository practiceSessionRepository,
			AuthService authService) {
		if (properties.getDirectory() == null
				|| properties.getDirectory().isBlank()) {
			throw new IllegalArgumentException(
					"IELTS recording directory must not be blank");
		}
		this.root = Path.of(properties.getDirectory())
				.toAbsolutePath()
				.normalize();
		if (properties.getMaxBytes() < 1) {
			throw new IllegalArgumentException(
					"IELTS recording maxBytes must be positive");
		}
		this.maxBytes = properties.getMaxBytes();
		this.practiceSessionRepository = practiceSessionRepository;
		this.authService = authService;
	}

	public String store(String sessionId, int turnNo, byte[] audio) {
		validateSessionId(sessionId);
		if (turnNo < 1 || audio == null || audio.length == 0
				|| audio.length > maxBytes) {
			throw recordingFailure();
		}
		String fileName = fileName(turnNo);
		Path target = resolve(sessionId, fileName);
		Path temporary = null;
		try {
			Files.createDirectories(target.getParent());
			temporary = Files.createTempFile(
					target.getParent(),
					"turn-" + turnNo + "-",
					".tmp");
			Files.write(temporary, audio);
			try {
				Files.move(
						temporary,
						target,
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException exception) {
				Files.move(
						temporary,
						target,
						StandardCopyOption.REPLACE_EXISTING);
			}
			return API_PREFIX + sessionId + "/" + fileName;
		}
		catch (IOException exception) {
			if (temporary != null) deletePath(temporary);
			throw recordingFailure();
		}
	}

	public Resource loadOwned(String sessionId, String fileName) {
		validateSessionId(sessionId);
		if (fileName == null || !SAFE_FILE_NAME.matcher(fileName).matches()) {
			throw recordingNotFound();
		}
		UUID userId = UUID.fromString(authService.requireUserId(null));
		PracticeSessionRecord session = practiceSessionRepository
				.findBySessionId(sessionId)
				.filter(record -> record.userId().equals(userId))
				.filter(record -> record.sceneType() == SceneType.IELTS_SCENE)
				.orElseThrow(this::recordingNotFound);
		Path path = resolve(session.sessionId(), fileName);
		if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
			throw recordingNotFound();
		}
		return new FileSystemResource(path);
	}

	public void delete(String sessionId, int turnNo) {
		if (!isSafeSessionId(sessionId) || turnNo < 1) return;
		deletePath(resolve(sessionId, fileName(turnNo)));
	}

	private Path resolve(String sessionId, String fileName) {
		Path path = root.resolve(sessionId).resolve(fileName).normalize();
		if (!path.startsWith(root)) throw recordingFailure();
		return path;
	}

	private String fileName(int turnNo) {
		return "turn-" + turnNo + ".wav";
	}

	private void validateSessionId(String sessionId) {
		if (!isSafeSessionId(sessionId)) throw recordingFailure();
	}

	private boolean isSafeSessionId(String sessionId) {
		return sessionId != null && SAFE_SESSION_ID.matcher(sessionId).matches();
	}

	private void deletePath(Path path) {
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ignored) {
			// Best-effort rollback must not hide the original persistence error.
		}
	}

	private BusinessException recordingFailure() {
		return new BusinessException(
				"IELTS_RECORDING_PERSISTENCE_FAILED",
				"雅思训练录音保存失败");
	}

	private BusinessException recordingNotFound() {
		return new BusinessException(
				"IELTS_RECORDING_NOT_FOUND",
				"训练录音不存在");
	}
}
