package com.unispeaking.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PersistenceArchitectureTest {

	private static final Path PERSISTENCE_SOURCE = Path.of(
			"src/main/java/com/unispeaking/infrastructure/persistence");
	private static final Pattern FORBIDDEN_SQL_API = Pattern.compile(
			"@(Select|Insert|Update|Delete)\\s*\\("
					+ "|\\.(last|apply|inSql|notInSql|setSql)\\s*\\(");

	@Test
	void persistenceCodeDoesNotEmbedSqlFragments() throws IOException {
		List<String> violations = new ArrayList<>();
		try (var files = Files.walk(PERSISTENCE_SOURCE)) {
			for (Path file : files
					.filter(path -> path.toString().endsWith(".java"))
					.toList()) {
				String source = Files.readString(file);
				if (FORBIDDEN_SQL_API.matcher(source).find()) {
					violations.add(file.toString());
				}
			}
		}

		assertTrue(
				violations.isEmpty(),
				"持久化层禁止 SQL 注解和原始 SQL 片段: " + violations);
	}
}
