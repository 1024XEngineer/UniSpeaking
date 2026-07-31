package com.unispeaking.infrastructure.storage.aliyun;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.config.ObjectStorageProperties;
import com.unispeaking.infrastructure.storage.ObjectStorageProvider;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public class AliyunOssObjectStorageProvider implements ObjectStorageProvider {
	private final OSS client;
	private final ObjectStorageProperties properties;

	public AliyunOssObjectStorageProvider(OSS client, ObjectStorageProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@Override
	public void put(String objectKey, byte[] content, String contentType) {
		try {
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(content.length);
			metadata.setContentType(contentType);
			client.putObject(properties.getBucket(), objectKey,
					new ByteArrayInputStream(content), metadata);
		}
		catch (RuntimeException exception) {
			throw storageFailure();
		}
	}

	@Override
	public URI signGetUrl(String objectKey, Duration ttl) {
		try {
			return client.generatePresignedUrl(
					properties.getBucket(),
					objectKey,
					Date.from(Instant.now().plus(ttl))).toURI();
		}
		catch (Exception exception) {
			throw storageFailure();
		}
	}

	@Override
	public void delete(String objectKey) {
		try {
			client.deleteObject(properties.getBucket(), objectKey);
		}
		catch (RuntimeException exception) {
			throw storageFailure();
		}
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public void close() {
		client.shutdown();
	}

	private BusinessException storageFailure() {
		return new BusinessException("AVATAR_STORAGE_FAILED", "头像存储服务暂时不可用");
	}
}
