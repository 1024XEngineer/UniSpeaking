package com.unispeaking.infrastructure.config;

import java.time.Duration;
import java.util.Arrays;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("object-storage.qiniu")
public class ObjectStorageProperties {
	private String bucket = "";
	private String accessKey = "";
	private String secretKey = "";
	private String domain = "";
	private String avatarPrefix = "avatars";
	private String ieltsRecordingPrefix = "ielts/recordings";
	private Duration signedUrlTtl = Duration.ofHours(1);

	public boolean configured() {
		return !bucket.isBlank() && !accessKey.isBlank()
				&& !secretKey.isBlank() && !domain.isBlank();
	}
	public String getBucket() { return bucket; }
	public void setBucket(String value) { bucket = value == null ? "" : value.trim(); }
	public String getAccessKey() { return accessKey; }
	public void setAccessKey(String value) { accessKey = value == null ? "" : value.trim(); }
	public String getSecretKey() { return secretKey; }
	public void setSecretKey(String value) { secretKey = value == null ? "" : value.trim(); }
	public String getDomain() { return domain; }
	public void setDomain(String value) { domain = value == null ? "" : value.trim(); }
	public String getAvatarPrefix() { return avatarPrefix; }
	public void setAvatarPrefix(String value) {
		avatarPrefix = normalizeObjectPrefix(value, "avatars");
	}
	public String getIeltsRecordingPrefix() { return ieltsRecordingPrefix; }
	public void setIeltsRecordingPrefix(String value) {
		ieltsRecordingPrefix = normalizeObjectPrefix(value, "ielts/recordings");
	}
	public Duration getSignedUrlTtl() { return signedUrlTtl; }
	public void setSignedUrlTtl(Duration value) { signedUrlTtl = value; }

	private String normalizeObjectPrefix(String value, String defaultValue) {
		String normalized = (value == null ? defaultValue : value.trim())
				.replaceAll("^/+|/+$", "");
		if (normalized.isBlank()) {
			throw new IllegalArgumentException(
					"Object storage prefix must not be blank");
		}
		if (normalized.contains("\\")
				|| Arrays.stream(normalized.split("/"))
						.anyMatch(segment -> segment.isBlank()
								|| ".".equals(segment)
								|| "..".equals(segment))) {
			throw new IllegalArgumentException(
					"Object storage prefix must not contain path traversal");
		}
		return normalized;
	}
}
