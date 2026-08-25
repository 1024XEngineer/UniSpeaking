package com.unispeaking.infrastructure.persistence.entity.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationDetailsJsonValidationTest {

    @Test
    void acceptsCompleteReadingDetailsAndUnmatchedPhoneme() {
        var unmatched = new ReadingDetailsJson.Phoneme(
                0, "t", "d", score("0"), -1, -1);
        var word = new ReadingDetailsJson.Word(
                0, "test", WordReadStatus.NORMAL, score("100"), score("80"), null,
                List.of(unmatched));
        var details = new ReadingDetailsJson(
                score("100"), score("0"), score("80"), score("90"), score("70"),
                EndingTone.FALL, List.of(word));

        assertEquals(1, details.words().size());
        assertEquals(-1, details.words().getFirst().phonemes().getFirst().startPosition());
        assertEquals(1, new ReadingDetailsJson.Phoneme(
                1, "e", "e", score("50")).endPosition());
    }

    @Test
    void rejectsInvalidReadingTopLevelAndWordValues() {
        var phoneme = new ReadingDetailsJson.Phoneme(0, "t", "t", score("80"));
        assertThrows(NullPointerException.class, () -> new ReadingDetailsJson(
                score("1"), score("1"), score("1"), score("1"), score("1"),
                null, List.of(validReadingWord(phoneme))));
        assertThrows(NullPointerException.class, () -> new ReadingDetailsJson(
                score("1"), score("1"), score("1"), score("1"), score("1"),
                EndingTone.FALL, null));
        assertThrows(IllegalArgumentException.class, () -> new ReadingDetailsJson(
                score("1"), score("1"), score("1"), score("1"), score("1"),
                EndingTone.FALL, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ReadingDetailsJson(
                score("-1"), score("1"), score("1"), score("1"), score("1"),
                EndingTone.FALL, List.of(validReadingWord(phoneme))));
        assertThrows(IllegalArgumentException.class, () -> new ReadingDetailsJson(
                score("101"), score("1"), score("1"), score("1"), score("1"),
                EndingTone.FALL, List.of(validReadingWord(phoneme))));

        assertThrows(IllegalArgumentException.class, () -> new ReadingDetailsJson.Word(
                -1, "word", WordReadStatus.NORMAL, score("1"), score("1"), false,
                List.of(phoneme)));
        assertThrows(IllegalArgumentException.class, () -> new ReadingDetailsJson.Word(
                0, " ", WordReadStatus.NORMAL, score("1"), score("1"), false,
                List.of(phoneme)));
        assertThrows(NullPointerException.class, () -> new ReadingDetailsJson.Word(
                0, "word", null, score("1"), score("1"), false, List.of(phoneme)));
        assertThrows(NullPointerException.class, () -> new ReadingDetailsJson.Word(
                0, "word", WordReadStatus.NORMAL, score("1"), score("1"), false, null));
        assertThrows(IllegalArgumentException.class, () -> new ReadingDetailsJson.Word(
                0, "word", WordReadStatus.NORMAL, score("1"), score("1"), false,
                List.of()));
    }

    @Test
    void rejectsInvalidReadingPhonemeValuesAndPositions() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReadingDetailsJson.Phoneme(-1, "t", "t", score("1")));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadingDetailsJson.Phoneme(0, null, "t", score("1")));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadingDetailsJson.Phoneme(0, "t", "", score("1")));
        assertThrows(NullPointerException.class,
                () -> new ReadingDetailsJson.Phoneme(0, "t", "t", null));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadingDetailsJson.Phoneme(0, "t", "t", score("1"), -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadingDetailsJson.Phoneme(0, "t", "t", score("1"), 2, 2));
    }

    @Test
    void acceptsEmptyPronunciationDetailsButValidatesNestedValues() {
        assertEquals(List.of(), new PronunciationDetailsJson(List.of()).words());
        var phoneme = new PronunciationDetailsJson.Phoneme(0, "t", "t", score("100"));
        var word = new PronunciationDetailsJson.Word(0, "test", score("0"), List.of(phoneme));
        assertEquals(1, new PronunciationDetailsJson(List.of(word)).words().size());

        assertThrows(NullPointerException.class, () -> new PronunciationDetailsJson(null));
		assertThrows(NullPointerException.class,
				() -> new PronunciationDetailsJson(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class,
                () -> new PronunciationDetailsJson.Word(-1, "test", score("1"), List.of(phoneme)));
        assertThrows(IllegalArgumentException.class,
                () -> new PronunciationDetailsJson.Word(0, " ", score("1"), List.of(phoneme)));
        assertThrows(IllegalArgumentException.class,
                () -> new PronunciationDetailsJson.Word(0, "test", score("-1"), List.of(phoneme)));
        assertThrows(IllegalArgumentException.class,
                () -> new PronunciationDetailsJson.Word(0, "test", score("101"), List.of(phoneme)));
        assertThrows(NullPointerException.class,
                () -> new PronunciationDetailsJson.Word(0, "test", score("1"), null));
        assertThrows(IllegalArgumentException.class,
                () -> new PronunciationDetailsJson.Word(0, "test", score("1"), List.of()));
    }

    @Test
    void rejectsInvalidPronunciationPhonemeValuesAndPositions() {
        assertThrows(IllegalArgumentException.class,
                () -> new PronunciationDetailsJson.Phoneme(-1, "t", "t", score("1")));
        assertThrows(IllegalArgumentException.class,
                () -> new PronunciationDetailsJson.Phoneme(0, null, "t", score("1")));
        assertThrows(IllegalArgumentException.class,
                () -> new PronunciationDetailsJson.Phoneme(0, "t", " ", score("1")));
        assertThrows(NullPointerException.class,
                () -> new PronunciationDetailsJson.Phoneme(0, "t", "t", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PronunciationDetailsJson.Phoneme(0, "t", "t", score("1"), -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PronunciationDetailsJson.Phoneme(0, "t", "t", score("1"), 2, 2));
    }

    private ReadingDetailsJson.Word validReadingWord(ReadingDetailsJson.Phoneme phoneme) {
        return new ReadingDetailsJson.Word(
                0, "word", WordReadStatus.NORMAL, score("1"), score("1"), false,
                List.of(phoneme));
    }

    private BigDecimal score(String value) {
        return new BigDecimal(value);
    }
}
