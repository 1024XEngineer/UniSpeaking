package com.unispeaking.domain.vo.scene;

import java.util.List;

public record IeltsContent(
		List<IeltsContentQuestion> part1,
		List<IeltsContentQuestion> part2,
		List<IeltsContentQuestion> part3) {

	public IeltsContent {
		part1 = part1 == null ? List.of() : List.copyOf(part1);
		part2 = part2 == null ? List.of() : List.copyOf(part2);
		part3 = part3 == null ? List.of() : List.copyOf(part3);
	}

	public List<IeltsContentQuestion> questionsFor(IeltsPart part) {
		return switch (part) {
			case PART_1 -> part1;
			case PART_2 -> part2;
			case PART_3 -> part3;
		};
	}
}
