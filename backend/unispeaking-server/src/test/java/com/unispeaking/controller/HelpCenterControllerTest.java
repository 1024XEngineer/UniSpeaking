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

	@Test
	void returnsCategoryArticlesAndArticleDetails() throws Exception {
		var mvc = MockMvcBuilders
				.standaloneSetup(new HelpCenterController(new HelpCenterService()))
				.build();

		mvc.perform(get("/api/help-center/categories/quick-start"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("快速开始"))
				.andExpect(jsonPath("$.data.articles.length()").value(3))
				.andExpect(jsonPath("$.data.articles[0].id")
						.value("complete-first-time-setup"));

		mvc.perform(get("/api/help-center/articles/complete-first-time-setup"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.categoryId").value("quick-start"))
				.andExpect(jsonPath("$.data.title")
						.value("如何完成首次设置并开始练习？"));
	}

	@Test
	void returnsNotFoundForUnknownHelpResources() throws Exception {
		var mvc = MockMvcBuilders
				.standaloneSetup(new HelpCenterController(new HelpCenterService()))
				.build();

		mvc.perform(get("/api/help-center/categories/missing"))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/help-center/articles/missing"))
				.andExpect(status().isNotFound());
	}
}
