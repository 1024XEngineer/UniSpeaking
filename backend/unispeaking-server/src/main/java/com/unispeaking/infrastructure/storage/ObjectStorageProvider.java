package com.unispeaking.infrastructure.storage;

import java.net.URI;
import java.time.Duration;

public interface ObjectStorageProvider {
	void put(String objectKey, byte[] content, String contentType);
	URI signGetUrl(String objectKey, Duration ttl);
	void delete(String objectKey);
	boolean available();
}
