package com.unispeaking.infrastructure.config;

import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.service.auth.AuthService;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 录音存储 Bean 装配：IELTS 与 Interview 各一实例（场景类型/API 前缀/文件名校验参数化）。 */
@Configuration
public class RecordingStoreConfig {

	@Bean(name = "ieltsRecordingStore")
	public RecordingStore ieltsRecordingStore(
			RecordingProperties properties,
			PracticeSessionRepository practiceSessionRepository,
			AuthService authService) {
		return new RecordingStore(
				properties,
				practiceSessionRepository,
				authService,
				Set.of(SceneType.IELTS_SCENE),
				"/api/ielts/recordings/",
				RecordingStore.TURN_FILE_NAME,
				"IELTS_RECORDING_NOT_FOUND",
				"IELTS_RECORDING_PERSISTENCE_FAILED");
	}

	@Bean(name = "interviewRecordingStore")
	public RecordingStore interviewRecordingStore(
			RecordingProperties properties,
			PracticeSessionRepository practiceSessionRepository,
			AuthService authService) {
		return new RecordingStore(
				properties,
				practiceSessionRepository,
				authService,
				Set.of(SceneType.INTERVIEW_SCENE),
				"/api/interview-scenes/",
				RecordingStore.INTERVIEW_FILE_NAME,
				"INTERVIEW_RECORDING_NOT_FOUND",
				"INTERVIEW_RECORDING_PERSISTENCE_FAILED");
	}
}
