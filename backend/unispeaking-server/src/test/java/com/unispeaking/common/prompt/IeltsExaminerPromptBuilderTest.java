package com.unispeaking.common.prompt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsContentQuestion;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.RecommendedExpression;
import java.util.List;
import org.junit.jupiter.api.Test;

class IeltsExaminerPromptBuilderTest {

	private final IeltsExaminerPromptBuilder builder =
			new IeltsExaminerPromptBuilder();

	@Test
	void partOneInjectsExaminerAndOrderedQuestions() {
		IeltsContent content = new IeltsContent(
				List.of(
						question("What do you do at weekends?"),
						question("Do you prefer Saturdays or Sundays?")),
				List.of(),
				List.of());

		String prompt = builder.build(
				IeltsPart.PART_1,
				"Weekends",
				content,
				"Margaret");

		assertTrue(prompt.contains("My name is Margaret"));
		assertTrue(prompt.contains("semantic VAD"));
		assertTrue(prompt.contains("INITIAL_SILENCE_WARNING"));
		assertTrue(prompt.contains("PART1_COMPLETE"));
		assertTrue(prompt.contains("Now, let's talk about Weekends"));
		assertTrue(prompt.indexOf("What do you do at weekends?")
				< prompt.indexOf("Do you prefer Saturdays or Sundays?"));
		assertFalse(prompt.contains("Active IELTS Layer: Part 2"));
		assertFalse(prompt.contains("Active IELTS Layer: Part 3"));
		assertFalse(prompt.contains("I tend to spend"));
		assertFalse(prompt.contains("{{"));
	}

	@Test
	void partTwoBuildsVisibleCueCardWithoutFollowUp() {
		IeltsContentQuestion cueCard = new IeltsContentQuestion(
				"Describe a useful skill you learned.",
				List.of("what the skill is", "how you learned it"),
				List.of());
		IeltsContentQuestion followUp = question(
				"Do you still use this skill today?");

		String prompt = builder.build(
				IeltsPart.PART_2,
				"A useful skill",
				new IeltsContent(
						List.of(),
						List.of(cueCard, followUp),
						List.of()),
				"Daniel");

		assertTrue(prompt.contains("Topic:\nDescribe a useful skill you learned."));
		assertTrue(prompt.contains("You should say:\n- what the skill is"));
		assertFalse(prompt.contains("Do you still use this skill today?"));
		assertTrue(prompt.contains("PREPARATION_COMPLETE"));
		assertTrue(prompt.contains("LONG_TURN_TIME_LIMIT"));
		assertTrue(prompt.contains("Please begin speaking now."));
		assertFalse(prompt.contains("{{"));
	}

	@Test
	void partTwoNeverAsksAFollowUp() {
		String prompt = builder.build(
				IeltsPart.PART_2,
				"A useful skill",
				new IeltsContent(
						List.of(),
						List.of(question("Describe a useful skill.")),
						List.of()));

		assertTrue(prompt.contains("follow-up question"));
		assertTrue(prompt.contains("That is the end of Part 2"));
	}

	@Test
	void partThreeStartsWithSuppliedQuestionAndHasItsOwnEvents() {
		String prompt = builder.build(
				IeltsPart.PART_3,
				"Skills",
				new IeltsContent(
						List.of(),
						List.of(),
						List.of(question("How should skills be taught?"))));

		assertTrue(prompt.contains("first spoken sentence must be that question"));
		assertTrue(prompt.contains("How should skills be taught?"));
		assertTrue(prompt.contains("PART3_COMPLETE"));
		assertTrue(prompt.contains("Never generate a follow-up"));
		assertFalse(prompt.contains("PREPARATION_COMPLETE"));
		assertFalse(prompt.contains("{{"));
	}

	@Test
	void rejectsNullPartNullContentAndEmptyActiveQuestions() {
		IeltsContent empty = new IeltsContent(List.of(), List.of(), List.of());
		assertThrows(IllegalArgumentException.class,
				() -> builder.build(null, "topic", empty));
		assertThrows(IllegalArgumentException.class,
				() -> builder.build(IeltsPart.PART_1, "topic", null));
		for (IeltsPart part : IeltsPart.values()) {
			assertThrows(IllegalArgumentException.class,
					() -> builder.build(part, "topic", empty));
		}
	}

	@Test
	void defaultsBlankTitleAndExaminerAndHandlesCueCardWithoutPoints() {
		String defaults = builder.build(
				IeltsPart.PART_1, " ",
				new IeltsContent(List.of(question("Question?")), List.of(), List.of()),
				null);
		String prompt = builder.build(
				IeltsPart.PART_2, " ",
				new IeltsContent(List.of(), List.of(question("  Describe a place.  ")), List.of()),
				null);

		assertTrue(defaults.contains("My name is Daniel"));
		assertTrue(defaults.contains("this topic"));
		assertTrue(prompt.contains("Topic:\nDescribe a place."));
		assertFalse(prompt.contains("You should say:"));
	}

	private IeltsContentQuestion question(String text) {
		return new IeltsContentQuestion(
				text,
				List.of(),
				List.of(new RecommendedExpression(
						"phrase",
						"I tend to spend...",
						"我通常会……",
						"Use naturally")));
	}
}
