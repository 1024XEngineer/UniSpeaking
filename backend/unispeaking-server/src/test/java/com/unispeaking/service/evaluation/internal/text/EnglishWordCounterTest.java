package com.unispeaking.service.evaluation.internal.text;

import static com.unispeaking.service.evaluation.internal.text.EnglishWordCounter.Classification.EMPTY;
import static com.unispeaking.service.evaluation.internal.text.EnglishWordCounter.Classification.SCORABLE;
import static com.unispeaking.service.evaluation.internal.text.EnglishWordCounter.Classification.TOO_SHORT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 验证有效英文词的词法边界及 0、1 至 3、4 词以上三种业务分类。
 */
class EnglishWordCounterTest {

	@Test
	void classifiesNullBlankAndTextWithoutEnglishWordsAsEmpty() {
		assertAll(
				() -> assertAnalysis(null, 0, EMPTY),
				() -> assertAnalysis("", 0, EMPTY),
				() -> assertAnalysis(" \t\n", 0, EMPTY),
				() -> assertAnalysis("!? ... 123 45.6 中文", 0, EMPTY));
	}

	@Test
	void appliesShortAndScorableThresholdsAtExactBoundaries() {
		assertAll(
				() -> assertAnalysis("Hello", 1, TOO_SHORT),
				() -> assertAnalysis("Hello world", 2, TOO_SHORT),
				() -> assertAnalysis("One two three", 3, TOO_SHORT),
				() -> assertAnalysis("One two three four", 4, SCORABLE),
				() -> assertAnalysis("One two three four five", 5, SCORABLE));
	}

	@Test
	void countsStraightAndCurlyContractionsAsSingleWords() {
		assertAll(
				() -> assertAnalysis("I'm ready", 2, TOO_SHORT),
				() -> assertAnalysis("I’m ready", 2, TOO_SHORT),
				() -> assertAnalysis("I'd've known", 2, TOO_SHORT));
	}

	@Test
	void countsSingleAndMultipleHyphenatedSegmentsAsSingleWords() {
		assertAll(
				() -> assertAnalysis("twenty-one", 1, TOO_SHORT),
				() -> assertAnalysis("mother-in-law", 1, TOO_SHORT),
				() -> assertAnalysis(
						"a well-known twenty-one-year-old speaker",
						4,
						SCORABLE));
	}

	@Test
	void ignoresRepeatedPunctuationAndExtraWhitespace() {
		assertAnalysis(
				"  Hello,,,world!!!\nThis\tworks.  ",
				4,
				SCORABLE);
	}

	@Test
	void treatsNumbersAndOtherPunctuationAsSeparators() {
		assertAll(
				() -> assertAnalysis("one2two 3 three", 3, TOO_SHORT),
				() -> assertAnalysis("hello_world", 2, TOO_SHORT),
				() -> assertAnalysis("one--two", 2, TOO_SHORT));
	}

	@Test
	void doesNotCountAsciiFragmentsEmbeddedInOtherLanguageWords() {
		assertAll(
				() -> assertAnalysis("café", 0, EMPTY),
				() -> assertAnalysis("中文hello中文", 0, EMPTY),
				() -> assertAnalysis("café hello", 1, TOO_SHORT));
	}

	private static void assertAnalysis(
			String transcript,
			int expectedWordCount,
			EnglishWordCounter.Classification expectedClassification) {
		EnglishWordCounter.Analysis analysis =
				EnglishWordCounter.analyze(transcript);

		assertAll(
				() -> assertEquals(
						expectedWordCount,
						analysis.validWordCount()),
				() -> assertEquals(
						expectedClassification,
						analysis.classification()));
	}
}
