package com.unispeaking.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 录音存储配置（由 {@code IeltsRecordingProperties} 泛化而来，保留 {@code ielts.recording}
 * 前缀与默认目录以保证 IELTS 行为不变）。{@code ttl} 默认 ≥7 天，须覆盖报告生成 +
 * FAILED 重试窗口（重试要重跑发音评分）。
 */
@ConfigurationProperties(prefix = "ielts.recording")
public class RecordingProperties {

	private String directory = "./data/ielts-recordings";
	private long maxBytes = 10L * 1024L * 1024L;
	private Duration ttl = Duration.ofDays(7);

	public String getDirectory() {
		return directory;
	}

	public void setDirectory(String directory) {
		this.directory = directory;
	}

	public long getMaxBytes() {
		return maxBytes;
	}

	public void setMaxBytes(long maxBytes) {
		this.maxBytes = maxBytes;
	}

	public Duration getTtl() {
		return ttl;
	}

	public void setTtl(Duration ttl) {
		this.ttl = ttl;
	}
}
