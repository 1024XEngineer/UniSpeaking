package com.unispeaking.common.util.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TitleRelevanceCalculatorTest {

	private final TitleRelevanceCalculator calculator =
			new TitleRelevanceCalculator();

	@Test
	void exactTitleHasMaximumScore() {
		assertEquals(1.0, calculator.score(
				"Home & Accommodation",
				"home accommodation"));
	}

	@Test
	void relatedTitleScoresHigherThanUnrelatedTitle() {
		double related = calculator.score(
				"Home & Accommodation",
				"accommodation");
		double unrelated = calculator.score(
				"Public Transportation",
				"accommodation");

		assertTrue(related > unrelated);
	}

	@Test
	void keywordMatchIgnoresCaseAndPunctuation() {
		assertTrue(calculator.isKeywordMatch(
				"Home & Accommodation",
				"HOME accommodation"));
	}
}
