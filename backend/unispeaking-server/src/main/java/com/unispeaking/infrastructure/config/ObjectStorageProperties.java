package com.unispeaking.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("object-storage.aliyun")
public class ObjectStorageProperties {
	private String endpoint = "";
	private String bucket = "";
	private String accessKeyId = "";
	private String accessKeySecret = "";
	private String avatarPrefix = "avatars";
	private Duration signedUrlTtl = Duration.ofHours(1);

	public boolean configured() {
		return !endpoint.isBlank() && !bucket.isBlank()
				&& !accessKeyId.isBlank() && !accessKeySecret.isBlank();
	}
	public String getEndpoint() { return endpoint; }
	public void setEndpoint(String value) { endpoint = value == null ? "" : value.trim(); }
	public String getBucket() { return bucket; }
	public void setBucket(String value) { bucket = value == null ? "" : value.trim(); }
	public String getAccessKeyId() { return accessKeyId; }
	public void setAccessKeyId(String value) { accessKeyId = value == null ? "" : value.trim(); }
	public String getAccessKeySecret() { return accessKeySecret; }
	public void setAccessKeySecret(String value) { accessKeySecret = value == null ? "" : value.trim(); }
	public String getAvatarPrefix() { return avatarPrefix; }
	public void setAvatarPrefix(String value) { avatarPrefix = value == null ? "avatars" : value.trim(); }
	public Duration getSignedUrlTtl() { return signedUrlTtl; }
	public void setSignedUrlTtl(Duration value) { signedUrlTtl = value; }
}
