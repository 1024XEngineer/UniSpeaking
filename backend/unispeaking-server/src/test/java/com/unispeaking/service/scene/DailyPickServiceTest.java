package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.component.scene.DailyPickCatalog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DailyPickServiceTest {

	private final DailyPickCatalog catalog = new DailyPickCatalog();

	@Test
	void catalogContainsThreeHundredUniqueTopicsAcrossTenCategories() {
		assertEquals(300, catalog.topics().size());
		assertEquals(300, new HashSet<>(catalog.topics().stream().map(topic -> topic.id()).toList()).size());
		assertEquals(10, new HashSet<>(catalog.topics().stream().map(topic -> topic.category()).toList()).size());
		assertTrue(catalog.topics().stream().noneMatch(topic -> topic.title().contains("·")));
		assertTrue(catalog.topics().stream().noneMatch(topic ->
				topic.title().contains("面试") || topic.sceneInput().contains("面试") || topic.sceneInput().contains("求职")));
	}

	@Test
	void returnsThreeRandomTopicsWithDifferentCategories() {
		Clock clock = Clock.fixed(Instant.parse("2026-08-24T09:30:00Z"), ZoneOffset.UTC);
		DailyPickService service = new DailyPickService(catalog, clock, new Random(7));

		var response = service.getDailyPicks();

		assertEquals("2026-08-24", response.date().toString());
		assertEquals("Asia/Shanghai", response.timezone());
		assertEquals(3, response.picks().size());
		assertEquals(3, new HashSet<>(response.picks().stream().map(pick -> pick.category()).toList()).size());
		assertEquals(Instant.parse("2026-08-24T16:00:00Z"), response.nextRefreshAt());
	}

	@Test
	void productionConstructorUsesAJrePortableRandomGenerator() {
		var response = new DailyPickService(catalog).getDailyPicks();

		assertEquals(3, response.picks().size());
		assertEquals(3, new HashSet<>(response.picks().stream()
				.map(pick -> pick.category())
				.toList()).size());
	}

	@Test
	void excludesTheCurrentBatchWhenRefreshing() {
		Clock clock = Clock.fixed(Instant.parse("2026-08-24T09:30:00Z"), ZoneOffset.UTC);
		DailyPickService service = new DailyPickService(catalog, clock, new Random(11));
		var first = service.getDailyPicks().picks();
		Set<String> currentIds = new HashSet<>(first.stream().map(pick -> pick.id()).toList());

		var refreshed = service.getDailyPicks(currentIds).picks();

		assertEquals(3, refreshed.size());
		assertTrue(refreshed.stream().noneMatch(pick -> currentIds.contains(pick.id())));
		assertEquals(3, new HashSet<>(refreshed.stream().map(pick -> pick.category()).toList()).size());
	}
}
