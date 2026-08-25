package com.unispeaking.domain.vo.achievement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AchievementSeriesProgressTest {
    private final AchievementDefinition first = new AchievementDefinition(
            "series-1", 1, "First", "desc", BigDecimal.ONE);
    private final AchievementDefinition second = new AchievementDefinition(
            "series-2", 2, "Second", "desc", BigDecimal.TEN);
    private final AchievementSeriesDefinition series = new AchievementSeriesDefinition(
            "series", "category", "Series", "times", List.of(first, second));

    @Test
    void reportsEmptyReachedAndCompletedStates() {
        AchievementSeriesProgress empty = new AchievementSeriesProgress(
                series, BigDecimal.ZERO, null, first);
        assertEquals(0, empty.currentLevel());
        assertNull(empty.currentTitle());
        assertFalse(empty.completed());

        AchievementSeriesProgress completed = new AchievementSeriesProgress(
                series, BigDecimal.TEN, List.of(first, second), null);
        assertEquals(2, completed.currentLevel());
        assertEquals("Second", completed.currentTitle());
        assertTrue(completed.completed());
    }

    @Test
    void rejectsMissingSeriesAndInvalidCurrentValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new AchievementSeriesProgress(null, BigDecimal.ZERO, List.of(), first));
        assertThrows(IllegalArgumentException.class,
                () -> new AchievementSeriesProgress(series, null, List.of(), first));
        assertThrows(IllegalArgumentException.class,
                () -> new AchievementSeriesProgress(series, BigDecimal.ONE.negate(), List.of(), first));
    }
}
