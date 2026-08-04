package com.unispeaking.domain.vo.scene;

public enum IeltsPart {
	PART_1,
	PART_2,
	PART_3;

	public IeltsTopicType topicType() {
		return this == PART_1
				? IeltsTopicType.PART_1_POOL
				: IeltsTopicType.PART_2_3_BUNDLE;
	}
}
