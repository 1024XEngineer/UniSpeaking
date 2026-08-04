package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ObjectStoragePropertiesTest {

	@Test
	void normalizesAvatarAndInterviewRecordingPrefixes() {
		ObjectStorageProperties properties = new ObjectStorageProperties();

		properties.setAvatarPrefix("/profile-avatars/");
		properties.setInterviewRecordingPrefix("/interviews/recordings/");

		assertEquals("profile-avatars", properties.getAvatarPrefix());
		assertEquals(
				"interviews/recordings",
				properties.getInterviewRecordingPrefix());
	}

	@Test
	void keepsStableDefaultsWhenPrefixIsNull() {
		ObjectStorageProperties properties = new ObjectStorageProperties();

		properties.setAvatarPrefix(null);
		properties.setInterviewRecordingPrefix(null);

		assertEquals("avatars", properties.getAvatarPrefix());
		assertEquals(
				"interviews/recordings",
				properties.getInterviewRecordingPrefix());
	}

	@Test
	void rejectsBlankAndPathTraversalPrefixes() {
		ObjectStorageProperties properties = new ObjectStorageProperties();

		assertThrows(
				IllegalArgumentException.class,
				() -> properties.setAvatarPrefix("///"));
		assertThrows(
				IllegalArgumentException.class,
				() -> properties.setInterviewRecordingPrefix("interviews/../recordings"));
		assertThrows(
				IllegalArgumentException.class,
				() -> properties.setInterviewRecordingPrefix("interviews\\recordings"));
	}
}
