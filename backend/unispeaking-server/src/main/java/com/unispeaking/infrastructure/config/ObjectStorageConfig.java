package com.unispeaking.infrastructure.config;

import com.qiniu.storage.BucketManager;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.storage.ObjectStorageProvider;
import com.unispeaking.infrastructure.storage.qiniu.QiniuObjectStorageProvider;
import java.net.URI;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObjectStorageConfig {

	@Bean
	ObjectStorageProvider objectStorageProvider(ObjectStorageProperties properties) {
		if (!properties.configured()) {
			return new UnavailableObjectStorageProvider();
		}
		com.qiniu.storage.Configuration configuration =
				com.qiniu.storage.Configuration.create();
		Auth auth = Auth.create(
				properties.getAccessKey(),
				properties.getSecretKey());
		return new QiniuObjectStorageProvider(
				auth,
				new UploadManager(configuration),
				new BucketManager(auth, configuration),
				properties);
	}

	private static final class UnavailableObjectStorageProvider
			implements ObjectStorageProvider {
		@Override public void put(String key, byte[] content, String type) { throw unavailable(); }
		@Override public URI signGetUrl(String key, Duration ttl) { throw unavailable(); }
		@Override public void delete(String key) { throw unavailable(); }
		@Override public boolean available() { return false; }
		private BusinessException unavailable() {
			return new BusinessException(
					"OBJECT_STORAGE_UNAVAILABLE",
					"对象存储尚未配置");
		}
	}
}
