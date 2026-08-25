package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

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
		assertThrows(IllegalArgumentException.class, () -> properties.setAvatarPrefix("a\\b"));
		assertThrows(IllegalArgumentException.class, () -> properties.setAvatarPrefix("a//b"));
		assertThrows(IllegalArgumentException.class, () -> properties.setAvatarPrefix("a/./b"));
		assertThrows(IllegalArgumentException.class, () -> properties.setIeltsRecordingPrefix("a/../b"));
	}

	@Test
	void trimsCredentialsAndCoversEveryConfiguredRequirement() {
		ObjectStorageProperties properties = new ObjectStorageProperties();
		assertFalse(properties.configured());
		properties.setBucket(null);
		properties.setAccessKey(null);
		properties.setSecretKey(null);
		properties.setDomain(null);
		assertEquals("", properties.getBucket());
		properties.setBucket(" bucket ");
		assertFalse(properties.configured());
		properties.setAccessKey(" access ");
		assertFalse(properties.configured());
		properties.setSecretKey(" secret ");
		assertFalse(properties.configured());
		properties.setDomain(" https://cdn.test ");
		assertTrue(properties.configured());
		assertEquals("access", properties.getAccessKey());
		assertEquals("secret", properties.getSecretKey());
		assertEquals("https://cdn.test", properties.getDomain());
		properties.setIeltsRecordingPrefix("/recordings/");
		assertEquals("recordings", properties.getIeltsRecordingPrefix());
		properties.setIeltsRecordingPrefix(null);
		assertEquals("ielts/recordings", properties.getIeltsRecordingPrefix());
		properties.setSignedUrlTtl(Duration.ofMinutes(5));
		assertEquals(Duration.ofMinutes(5), properties.getSignedUrlTtl());
	}
}
