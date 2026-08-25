package com.unispeaking.domain.vo.achievement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AchievementDefinitionsCoverageTest {

	@Test
	void validatesEveryDefinitionFieldAndThreshold() {
		assertThrows(IllegalArgumentException.class, () -> definition(null, 1, "title", "description", BigDecimal.ONE));
		assertThrows(IllegalArgumentException.class, () -> definition(" ", 1, "title", "description", BigDecimal.ONE));
		assertThrows(IllegalArgumentException.class, () -> definition("id", 0, "title", "description", BigDecimal.ONE));
		assertThrows(IllegalArgumentException.class, () -> definition("id", 1, null, "description", BigDecimal.ONE));
		assertThrows(IllegalArgumentException.class, () -> definition("id", 1, " ", "description", BigDecimal.ONE));
		assertThrows(IllegalArgumentException.class, () -> definition("id", 1, "title", null, BigDecimal.ONE));
		assertThrows(IllegalArgumentException.class, () -> definition("id", 1, "title", " ", BigDecimal.ONE));
		assertThrows(NullPointerException.class, () -> definition("id", 1, "title", "description", null));
		assertThrows(IllegalArgumentException.class, () -> definition("id", 1, "title", "description", BigDecimal.ZERO));
	}

	@Test
	void validatesEverySeriesFieldAndMilestoneInvariant() {
		AchievementDefinition first = definition("series-1", 1, "one", "first", BigDecimal.ONE);
		assertThrows(IllegalArgumentException.class, () -> series(null, "category", "title", "unit", List.of(first)));
		assertThrows(IllegalArgumentException.class, () -> series(" ", "category", "title", "unit", List.of(first)));
		assertThrows(IllegalArgumentException.class, () -> series("series", null, "title", "unit", List.of(first)));
		assertThrows(IllegalArgumentException.class, () -> series("series", " ", "title", "unit", List.of(first)));
		assertThrows(IllegalArgumentException.class, () -> series("series", "category", null, "unit", List.of(first)));
		assertThrows(IllegalArgumentException.class, () -> series("series", "category", " ", "unit", List.of(first)));
		assertThrows(IllegalArgumentException.class, () -> series("series", "category", "title", null, List.of(first)));
		assertThrows(IllegalArgumentException.class, () -> series("series", "category", "title", " ", List.of(first)));
		assertThrows(IllegalArgumentException.class, () -> series("series", "category", "title", "unit", null));
		assertThrows(IllegalArgumentException.class, () -> series("series", "category", "title", "unit", List.of()));
		AchievementDefinition wrongLevel = definition("series-2", 2, "one", "first", BigDecimal.ONE);
		assertThrows(IllegalArgumentException.class, () -> series("series", "category", "title", "unit", List.of(wrongLevel)));
		AchievementDefinition wrongId = definition("wrong-1", 1, "one", "first", BigDecimal.ONE);
		assertThrows(IllegalArgumentException.class, () -> series("series", "category", "title", "unit", List.of(wrongId)));
		AchievementDefinition second = definition("series-2", 2, "two", "second", BigDecimal.TEN);
		assertEquals(2, series("series", "category", "title", "unit", List.of(first, second)).milestones().size());
	}

	private AchievementDefinition definition(String id, int level, String title, String description, BigDecimal threshold) {
		return new AchievementDefinition(id, level, title, description, threshold);
	}

	private AchievementSeriesDefinition series(String id, String category, String title, String unit, List<AchievementDefinition> milestones) {
		return new AchievementSeriesDefinition(id, category, title, unit, milestones);
	}
}
