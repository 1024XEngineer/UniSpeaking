package com.unispeaking.component.recording;

import com.unispeaking.infrastructure.config.RecordingProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** TTL 清扫：每天定时删除超过 {@link RecordingProperties#getTtl()} 的录音文件。 */
@Component
public class RecordingCleanupScheduler {

	private final RecordingProperties properties;
	private final RecordingStore ieltsRecordingStore;
	private final RecordingStore interviewRecordingStore;

	public RecordingCleanupScheduler(
			RecordingProperties properties,
			@Qualifier("ieltsRecordingStore") RecordingStore ieltsRecordingStore,
			@Qualifier("interviewRecordingStore") RecordingStore interviewRecordingStore) {
		this.properties = properties;
		this.ieltsRecordingStore = ieltsRecordingStore;
		this.interviewRecordingStore = interviewRecordingStore;
	}

	@Scheduled(cron = "${recording.ttl-cleanup-cron:0 30 3 * * *}")
	public void cleanupExpiredRecordings() {
		ieltsRecordingStore.cleanupExpired(properties.getTtl());
		interviewRecordingStore.cleanupExpired(properties.getTtl());
	}
}
