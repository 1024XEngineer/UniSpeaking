package com.unispeaking.domain.po.scene;

import com.unispeaking.domain.vo.scene.IeltsTopicType;

public record IeltsTopic(
		String id,
		String title,
		IeltsTopicType topicType,
		String category,
		String source,
		String status) {
}
