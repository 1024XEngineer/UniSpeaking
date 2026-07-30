package com.unispeaking.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProfileSchemaContractTest {

	@Test
	void declaresAccountLifecycleAndAchievementStorage() throws IOException {
		String schema = readSchema();

		assertAll(
				() -> assertTrue(schema.contains("avatar_object_key VARCHAR(512)")),
				() -> assertTrue(schema.contains("deletion_requested_at TIMESTAMPTZ")),
				() -> assertTrue(schema.contains("deletion_scheduled_at TIMESTAMPTZ")),
				() -> assertTrue(schema.contains("'PENDING_DELETION'")),
				() -> assertTrue(schema.contains(
						"CREATE TABLE IF NOT EXISTS achievement_definitions")),
				() -> assertTrue(schema.contains(
						"CREATE TABLE IF NOT EXISTS user_achievement_progress")));
	}

	private String readSchema() throws IOException {
		try (InputStream stream = getClass().getResourceAsStream("/db/schema.sql")) {
			if (stream == null) {
				throw new IllegalStateException("Missing /db/schema.sql test resource");
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
