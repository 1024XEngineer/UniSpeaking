package com.unispeaking.infrastructure.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.storage.ObjectStorageProvider;
import com.unispeaking.infrastructure.storage.aliyun.AliyunOssObjectStorageProvider;
import java.net.URI;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObjectStorageConfig {

	@Bean(destroyMethod = "")
	ObjectStorageProvider objectStorageProvider(ObjectStorageProperties properties) {
		if (!properties.configured()) {
			return new UnavailableObjectStorageProvider();
		}
		OSS client = new OSSClientBuilder().build(
				properties.getEndpoint(),
				properties.getAccessKeyId(),
				properties.getAccessKeySecret());
		return new ManagedAliyunProvider(client, properties);
	}

	private static final class ManagedAliyunProvider
			extends AliyunOssObjectStorageProvider {
		private final OSS client;

		private ManagedAliyunProvider(OSS client, ObjectStorageProperties properties) {
			super(client, properties);
			this.client = client;
		}

		@SuppressWarnings("unused")
		public void shutdown() {
			client.shutdown();
		}
	}

	private static final class UnavailableObjectStorageProvider
			implements ObjectStorageProvider {
		@Override public void put(String key, byte[] content, String type) { throw unavailable(); }
		@Override public URI signGetUrl(String key, Duration ttl) { throw unavailable(); }
		@Override public void delete(String key) { throw unavailable(); }
		@Override public boolean available() { return false; }
		private BusinessException unavailable() {
			return new BusinessException(
					"AVATAR_STORAGE_UNAVAILABLE",
					"头像存储尚未配置");
		}
	}
}
