package com.unispeaking.service.scene;

import com.unispeaking.component.scene.DailyPickCatalog;
import com.unispeaking.domain.dto.scene.DailyPickResponse;
import com.unispeaking.domain.dto.scene.DailyPicksResponse;
import com.unispeaking.domain.vo.scene.DailyPickTopic;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DailyPickService {

	static final ZoneId RECOMMENDATION_ZONE = ZoneId.of("Asia/Shanghai");
	private static final int PICKS_PER_DAY = 3;

	private final DailyPickCatalog catalog;
	private final Clock clock;
	private final RandomGenerator random;

	@Autowired
	public DailyPickService(DailyPickCatalog catalog) {
		this(catalog, Clock.system(RECOMMENDATION_ZONE), RandomGenerator.getDefault());
	}

	DailyPickService(DailyPickCatalog catalog, Clock clock) {
		this(catalog, clock, RandomGenerator.getDefault());
	}

	DailyPickService(DailyPickCatalog catalog, Clock clock, RandomGenerator random) {
		this.catalog = catalog;
		this.clock = clock;
		this.random = random;
	}

	public DailyPicksResponse getDailyPicks() {
		return getDailyPicks(Set.of());
	}

	public DailyPicksResponse getDailyPicks(Set<String> excludedIds) {
		LocalDate date = LocalDate.now(clock.withZone(RECOMMENDATION_ZONE));
		Set<String> exclusions = excludedIds == null ? Set.of() : excludedIds;
		Map<String, List<DailyPickTopic>> topicsByCategory = catalog.topics().stream()
				.filter(topic -> !exclusions.contains(topic.id()))
				.collect(Collectors.groupingBy(DailyPickTopic::category));
		List<String> categories = new ArrayList<>(topicsByCategory.keySet());
		shuffle(categories);
		if (categories.size() < PICKS_PER_DAY) {
			throw new IllegalStateException("Daily pick catalog must provide at least three categories");
		}
		List<DailyPickResponse> picks = new ArrayList<>(PICKS_PER_DAY);
		for (int index = 0; index < PICKS_PER_DAY; index++) {
			List<DailyPickTopic> categoryTopics = topicsByCategory.get(categories.get(index));
			DailyPickTopic topic = categoryTopics.get(random.nextInt(categoryTopics.size()));
			picks.add(toResponse(topic, index + 1));
		}
		return new DailyPicksResponse(
				date,
				RECOMMENDATION_ZONE.getId(),
				date.plusDays(1).atStartOfDay(RECOMMENDATION_ZONE).toInstant(),
				picks);
	}

	private <T> void shuffle(List<T> values) {
		for (int index = values.size() - 1; index > 0; index--) {
			Collections.swap(values, index, random.nextInt(index + 1));
		}
	}

	private DailyPickResponse toResponse(DailyPickTopic topic, int position) {
		return new DailyPickResponse(
				topic.id(),
				position,
				topic.title(),
				topic.category(),
				topic.duration(),
				topic.level(),
				topic.goal(),
				topic.sceneInput());
	}
}
