package com.unispeaking.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class IeltsSeedDataTest {

	@Test
	void partTwoAndThreeTopicTitlesAreEnglishSummaries() throws IOException {
		String migration;
		try (var input = getClass().getResourceAsStream(
				"/db/migration/V3__ielts_question_bank.sql")) {
			if (input == null) {
				throw new IOException("IELTS seed migration is missing");
			}
			migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}

		List<String> topicRows = migration.lines()
				.filter(line -> line.contains("'PART_2_3_BUNDLE'"))
				.filter(line -> line.stripLeading().startsWith("('ielts_group_"))
				.toList();

		assertEquals(167, topicRows.size());
		assertFalse(topicRows.stream().anyMatch(this::containsHanCharacter));
	}

	private boolean containsHanCharacter(String value) {
		return value.codePoints().anyMatch(codePoint ->
				Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
	}
}
