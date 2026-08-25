package com.unispeaking.common.prompt.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.vo.scene.RecommendedExpression;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DialogueTurnEvaluationPromptInputTest {
    @Test
    void trimsRequiredAndOptionalValuesAndUsesConvenienceConstructor() {
        var input = new DialogueTurnEvaluationPromptInput(
                " free_chat ", " ", null, " user ", " goal ", List.of(), " ai ", " transcript ");
        assertEquals("free_chat", input.practiceMode());
        assertNull(input.background());
        assertNull(input.aiRole());
        assertEquals("user", input.userRole());
        assertEquals("goal", input.learningGoal());
        assertEquals("ai", input.aiText());
        assertEquals(List.of(), input.recommendedExpressions());
    }

    @Test
    void rejectsEveryMissingRequiredCollectionAndNullElementShape() {
        assertThrows(EvaluationException.class, () -> input(null, "text", List.of(), List.of()));
        assertThrows(EvaluationException.class, () -> input(" ", "text", List.of(), List.of()));
        assertThrows(EvaluationException.class, () -> input("mode", null, List.of(), List.of()));
        assertThrows(EvaluationException.class, () -> input("mode", " ", List.of(), List.of()));
        assertThrows(EvaluationException.class, () -> input("mode", "text", null, List.of()));
        assertThrows(EvaluationException.class, () -> input(
                "mode", "text", Arrays.asList((DialogueTurnEvaluationHistory) null), List.of()));
        assertThrows(EvaluationException.class, () -> input("mode", "text", List.of(), null));
        assertThrows(EvaluationException.class, () -> input(
                "mode", "text", List.of(), Arrays.asList((RecommendedExpression) null)));
    }

    private DialogueTurnEvaluationPromptInput input(
            String mode, String transcript, List<DialogueTurnEvaluationHistory> history,
            List<RecommendedExpression> expressions) {
        return new DialogueTurnEvaluationPromptInput(
                mode, null, null, null, null, history, null, transcript, expressions);
    }
}
