package com.unispeaking.infrastructure.storage.qiniu;

import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.DownloadUrl;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.config.ObjectStorageProperties;
import com.unispeaking.infrastructure.storage.ObjectStorageProvider;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class QiniuObjectStorageProvider implements ObjectStorageProvider {
	private final Auth auth;
	private final UploadManager uploadManager;
	private final BucketManager bucketManager;
	private final ObjectStorageProperties properties;
	private final String downloadDomain;

	public QiniuObjectStorageProvider(
			Auth auth,
			UploadManager uploadManager,
			BucketManager bucketManager,
			ObjectStorageProperties properties) {
		this.auth = Objects.requireNonNull(auth, "Qiniu auth is required");
		this.uploadManager = Objects.requireNonNull(
				uploadManager, "Qiniu upload manager is required");
		this.bucketManager = Objects.requireNonNull(
				bucketManager, "Qiniu bucket manager is required");
		this.properties = Objects.requireNonNull(
				properties, "Qiniu storage properties are required");
		this.downloadDomain = requireHttpsDomain(properties.getDomain());
	}

	@Override
	public void put(String objectKey, byte[] content, String contentType) {
		Response response = null;
		try {
			response = uploadManager.put(
				content,
				objectKey,
				auth.uploadToken(properties.getBucket(), objectKey),
				null,
				contentType,
				false);
		}
		catch (QiniuException exception) {
			throw storageFailure();
		}
		finally {
			if (response != null) {
				response.close();
			}
		}
	}

	@Override
	public URI signGetUrl(String objectKey, Duration ttl) {
		if (ttl == null || ttl.isZero() || ttl.isNegative()) {
			throw storageFailure();
		}
		try {
			long deadline = Instant.now().plus(ttl).getEpochSecond();
			String url = new DownloadUrl(downloadDomain, true, objectKey)
					.buildURL(auth, deadline);
			return URI.create(url);
		}
		catch (QiniuException | IllegalArgumentException exception) {
			throw storageFailure();
		}
	}

	@Override
	public void delete(String objectKey) {
		try {
			bucketManager.delete(properties.getBucket(), objectKey);
		}
		catch (QiniuException exception) {
			throw storageFailure();
		}
	}

	@Override
	public boolean available() {
		return true;
	}

	private String requireHttpsDomain(String value) {
		String configured = value == null ? "" : value.trim();
		URI uri;
		try {
			uri = URI.create(configured.contains("://")
					? configured
					: "https://" + configured);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException(
					"Qiniu download domain is invalid", exception);
		}
		if (!"https".equalsIgnoreCase(uri.getScheme())
				|| uri.getHost() == null
				|| uri.getUserInfo() != null
				|| uri.getQuery() != null
				|| uri.getFragment() != null
				|| (uri.getPath() != null
						&& !uri.getPath().isBlank()
						&& !"/".equals(uri.getPath()))) {
			throw new IllegalArgumentException(
					"Qiniu download domain must be an HTTPS host");
		}
		return uri.getPort() < 0
				? uri.getHost()
				: uri.getHost() + ":" + uri.getPort();
	}

	private BusinessException storageFailure() {
		return new BusinessException(
				"AVATAR_STORAGE_FAILED",
				"头像存储服务暂时不可用");
	}
}
