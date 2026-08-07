package com.unispeaking.service.achievement;

import com.unispeaking.component.achievement.AchievementCatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.vo.achievement.AchievementDefinition;
import com.unispeaking.domain.vo.achievement.AchievementSeriesDefinition;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class AchievementCatalogTest {

	private final AchievementCatalog catalog = new AchievementCatalog();

	@Test
	void exposesTenSeriesAndFortyEightUniqueMilestonesInStableOrder() {
		List<AchievementSeriesDefinition> series = catalog.series();

		assertEquals(10, series.size());
		assertEquals("conversation", series.getFirst().seriesId());
		assertEquals("quality-sessions", series.getLast().seriesId());
		List<String> achievementIds = series.stream()
				.flatMap(item -> item.milestones().stream())
				.map(AchievementDefinition::achievementId)
				.toList();
		assertEquals(48, achievementIds.size());
		assertEquals(48, new HashSet<>(achievementIds).size());
	}

	@Test
	void resolvesSeriesAndAchievementsWithoutExposingMutableCollections() {
		AchievementSeriesDefinition conversation = catalog.findSeries("conversation")
				.orElseThrow();

		assertEquals("对话历程", conversation.title());
		assertEquals("初次开口", catalog.findAchievement("conversation-1")
				.orElseThrow()
				.title());
		assertTrue(catalog.findSeries("missing").isEmpty());
		assertTrue(catalog.findAchievement("missing-1").isEmpty());
		assertThrows(
				UnsupportedOperationException.class,
				() -> catalog.series().add(conversation));
		assertThrows(
				UnsupportedOperationException.class,
				() -> conversation.milestones().clear());
	}

	@Test
	void rejectsMalformedSeriesDefinitions() {
		AchievementDefinition first = new AchievementDefinition(
				"sample-1",
				1,
				"一级",
				"完成一级",
				BigDecimal.ONE);
		AchievementDefinition duplicateThreshold = new AchievementDefinition(
				"sample-2",
				2,
				"二级",
				"完成二级",
				BigDecimal.ONE);

		assertThrows(
				IllegalArgumentException.class,
				() -> new AchievementSeriesDefinition(
						"sample",
						"分类",
						"系列",
						"次",
						List.of(first, duplicateThreshold)));
		assertThrows(
				IllegalArgumentException.class,
				() -> new AchievementDefinition(
						"sample-0",
						0,
						"无效",
						"无效节点",
						BigDecimal.ZERO));
	}
}
