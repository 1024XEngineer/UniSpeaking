package com.unispeaking.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.component.scene.DailyPickCatalog;
import com.unispeaking.service.scene.DailyPickService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DailyPickControllerTest {

	@Test
	void returnsTheDailyPickContract() throws Exception {
		var mvc = MockMvcBuilders
				.standaloneSetup(new DailyPickController(new DailyPickService(new DailyPickCatalog())))
				.build();

		mvc.perform(get("/api/daily-picks"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.timezone").value("Asia/Shanghai"))
				.andExpect(jsonPath("$.data.picks.length()").value(3))
				.andExpect(jsonPath("$.data.picks[0].position").value(1))
				.andExpect(jsonPath("$.data.picks[0].sceneInput").isNotEmpty());
	}

	@Test
	void acceptsCurrentTopicIdsAsExclusions() throws Exception {
		var mvc = MockMvcBuilders
				.standaloneSetup(new DailyPickController(new DailyPickService(new DailyPickCatalog())))
				.build();

		mvc.perform(get("/api/daily-picks")
					.param("exclude", "coffee-shop-essential")
					.param("exclude", "hotel-checkin-essential"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.picks.length()").value(3));
	}
}
