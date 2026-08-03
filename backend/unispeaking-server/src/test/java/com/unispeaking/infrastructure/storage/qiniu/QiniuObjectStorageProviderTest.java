package com.unispeaking.infrastructure.storage.qiniu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.unispeaking.infrastructure.config.ObjectStorageProperties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QiniuObjectStorageProviderTest {

	private static final String BUCKET = "profile-bucket";
	private static final String OBJECT_KEY = "avatars/user/avatar.png";
	private UploadManager uploadManager;
	private BucketManager bucketManager;
	private QiniuObjectStorageProvider provider;

	@BeforeEach
	void setUp() {
		ObjectStorageProperties properties = new ObjectStorageProperties();
		properties.setAccessKey("test-access-key");
		properties.setSecretKey("test-secret-key");
		properties.setBucket(BUCKET);
		properties.setDomain("https://profile.example.com");
		uploadManager = mock(UploadManager.class);
		bucketManager = mock(BucketManager.class);
		provider = new QiniuObjectStorageProvider(
				Auth.create("test-access-key", "test-secret-key"),
				uploadManager,
				bucketManager,
				properties);
	}

	@Test
	void uploadsWithAKeyScopedTokenAndClosesTheResponse() throws Exception {
		byte[] content = new byte[] {1, 2, 3};
		Response response = mock(Response.class);
		when(uploadManager.put(
				eq(content),
				eq(OBJECT_KEY),
				anyString(),
				isNull(),
				eq("image/png"),
				eq(false))).thenReturn(response);

		provider.put(OBJECT_KEY, content, "image/png");

		verify(uploadManager).put(
				eq(content),
				eq(OBJECT_KEY),
				anyString(),
				isNull(),
				eq("image/png"),
				eq(false));
		verify(response).close();
	}

	@Test
	void createsAnHttpsPrivateDownloadUrl() {
		URI result = provider.signGetUrl(OBJECT_KEY, Duration.ofMinutes(5));

		assertEquals("https", result.getScheme());
		assertEquals("profile.example.com", result.getHost());
		assertEquals("/" + OBJECT_KEY, result.getPath());
		assertTrue(result.getQuery().contains("e="));
		assertTrue(result.getQuery().contains("token=test-access-key:"));
	}

	@Test
	void deletesFromTheConfiguredBucket() throws Exception {
		provider.delete(OBJECT_KEY);

		verify(bucketManager).delete(BUCKET, OBJECT_KEY);
	}

	@Test
	void rejectsAnInsecureDownloadDomain() {
		ObjectStorageProperties properties = new ObjectStorageProperties();
		properties.setDomain("http://profile.example.com");

		assertThrows(
				IllegalArgumentException.class,
				() -> new QiniuObjectStorageProvider(
						Auth.create("access-key", "secret-key"),
						uploadManager,
						bucketManager,
						properties));
	}
}
