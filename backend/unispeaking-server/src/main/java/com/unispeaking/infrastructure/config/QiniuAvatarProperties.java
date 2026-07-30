package com.unispeaking.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("profile.avatar.qiniu")
public class QiniuAvatarProperties {

	private String accessKey = "";
	private String secretKey = "";
	private String bucket = "";
	private String domain = "";
	private String region = "auto";

	public String getAccessKey() {
		return accessKey;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public String getBucket() {
		return bucket;
	}

	public void setBucket(String bucket) {
		this.bucket = bucket;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public boolean hasStorageCredentials() {
		return hasText(accessKey) && hasText(secretKey) && hasText(bucket);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
