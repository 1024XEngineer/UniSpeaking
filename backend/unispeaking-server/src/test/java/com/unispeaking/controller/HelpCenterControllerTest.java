package com.unispeaking.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.service.help.HelpCenterService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HelpCenterControllerTest {

	@Test
	void returnsHelpCategoriesFromTheBackendContract() throws Exception {
		var mvc = MockMvcBuilders
				.standaloneSetup(new HelpCenterController(new HelpCenterService()))
				.build();

		mvc.perform(get("/api/help-center"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.categories.length()").value(8))
				.andExpect(jsonPath("$.data.categories[0].id").value("quick-start"))
				.andExpect(jsonPath("$.data.categories[0].title").value("快速开始"))
				.andExpect(jsonPath("$.data.categories[0].articleCount").value(3));
	}
}
