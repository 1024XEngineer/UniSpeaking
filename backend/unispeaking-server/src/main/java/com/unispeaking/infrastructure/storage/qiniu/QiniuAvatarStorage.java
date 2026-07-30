package com.unispeaking.infrastructure.storage.qiniu;

import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.infrastructure.config.QiniuAvatarProperties;
import com.unispeaking.service.account.AvatarStorage;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class QiniuAvatarStorage implements AvatarStorage {

	private static final int QINIU_OBJECT_NOT_FOUND = 612;

	private final QiniuAvatarProperties properties;
	private final Configuration configuration;
	private final UploadManager uploadManager;

	public QiniuAvatarStorage(QiniuAvatarProperties properties) {
		this.properties = properties;
		this.configuration = Configuration.create(resolveRegion(properties.getRegion()));
		this.uploadManager = new UploadManager(configuration);
	}

	@Override
	public void put(String objectKey, String contentType, byte[] bytes) {
		requireConfigured();
		Auth auth = Auth.create(properties.getAccessKey(), properties.getSecretKey());
		String uploadToken = auth.uploadToken(properties.getBucket(), objectKey);
		try {
			Response response = uploadManager.put(
					bytes,
					objectKey,
					uploadToken,
					null,
					contentType,
					false);
			if (!response.isOK()) {
				throw storageFailure();
			}
		}
		catch (QiniuException exception) {
			throw storageFailure();
		}
	}

	@Override
	public void delete(String objectKey) {
		requireConfigured();
		Auth auth = Auth.create(properties.getAccessKey(), properties.getSecretKey());
		BucketManager bucketManager = new BucketManager(auth, configuration);
		try {
			bucketManager.delete(properties.getBucket(), objectKey);
		}
		catch (QiniuException exception) {
			if (exception.code() != QINIU_OBJECT_NOT_FOUND) {
				throw storageFailure();
			}
		}
	}

	private void requireConfigured() {
		if (!properties.hasStorageCredentials()) {
			throw new BusinessException(
					"AVATAR_STORAGE_UNAVAILABLE",
					"七牛云头像存储尚未配置");
		}
	}

	private Region resolveRegion(String configured) {
		String value = configured == null
				? "auto"
				: configured.trim().toLowerCase(Locale.ROOT);
		return switch (value) {
			case "", "auto" -> Region.autoRegion();
			case "z0", "huadong" -> Region.region0();
			case "z1", "huabei" -> Region.region1();
			case "z2", "huanan" -> Region.region2();
			case "na0", "beimei" -> Region.regionNa0();
			case "as0", "xinjiapo" -> Region.regionAs0();
			default -> throw new IllegalArgumentException(
					"Unsupported QINIU_AVATAR_REGION: " + configured);
		};
	}

	private BusinessException storageFailure() {
		return new BusinessException(
				"AVATAR_STORAGE_FAILED",
				"头像存储暂时不可用，请稍后重试");
	}
}
