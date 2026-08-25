package com.unispeaking.common.util.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

	@Test
	void handlesNullEmptyNonContainingAndUnicodeGramInputs() {
		assertEquals(0, calculator.score(null, "word"));
		assertEquals(0, calculator.score("word", null));
		assertEquals(0, calculator.score("!!!", "word"));
		assertEquals(0, calculator.score("word", "!!!"));
		assertFalse(calculator.isKeywordMatch(null, "word"));
		assertFalse(calculator.isKeywordMatch("word", null));
		assertFalse(calculator.isKeywordMatch("alpha", "beta"));
		assertTrue(calculator.score("英语学习计划", "英语学习方法") > 0);
		assertEquals(0, calculator.score("a", "b"));
	}
}
