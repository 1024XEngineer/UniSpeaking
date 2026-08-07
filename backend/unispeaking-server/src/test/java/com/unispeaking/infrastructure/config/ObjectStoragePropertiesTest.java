package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ObjectStoragePropertiesTest {

	@Test
	void normalizesAvatarPrefix() {
		ObjectStorageProperties properties = new ObjectStorageProperties();

		properties.setAvatarPrefix("/profile-avatars/");

		assertEquals("profile-avatars", properties.getAvatarPrefix());
	}

	@Test
	void keepsStableAvatarDefaultWhenPrefixIsNull() {
		ObjectStorageProperties properties = new ObjectStorageProperties();

		properties.setAvatarPrefix(null);

		assertEquals("avatars", properties.getAvatarPrefix());
	}

	@Test
	void rejectsBlankAndPathTraversalPrefixes() {
		ObjectStorageProperties properties = new ObjectStorageProperties();

		assertThrows(
				IllegalArgumentException.class,
				() -> properties.setAvatarPrefix("///"));
	}
}
