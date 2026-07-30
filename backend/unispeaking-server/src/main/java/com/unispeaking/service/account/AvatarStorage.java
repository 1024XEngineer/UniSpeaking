package com.unispeaking.service.account;

public interface AvatarStorage {

	void put(String objectKey, String contentType, byte[] bytes);

	void delete(String objectKey);
}
