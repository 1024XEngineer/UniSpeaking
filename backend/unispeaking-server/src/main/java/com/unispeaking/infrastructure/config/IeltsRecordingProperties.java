package com.unispeaking.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ielts.recording")
public class IeltsRecordingProperties {

	private String directory = "./data/ielts-recordings";
	private long maxBytes = 10L * 1024L * 1024L;

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
}
