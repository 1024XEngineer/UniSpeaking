package com.unispeaking.domain.po.scene;

import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import java.util.Objects;
import java.util.UUID;

public record IeltsPracticeRecord(
		String ieltsId,
		UUID userId,
		IeltsMode mode,
		IeltsPart selectedPart,
		String selectedTopicId,
		IeltsContent content) {

	public IeltsPracticeRecord {
		Objects.requireNonNull(ieltsId, "ieltsId must not be null");
		Objects.requireNonNull(userId, "userId must not be null");
		Objects.requireNonNull(mode, "mode must not be null");
		if (mode == IeltsMode.PART_PRACTICE) {
			Objects.requireNonNull(selectedPart, "selectedPart must not be null for part practice");
		}
		Objects.requireNonNull(content, "content must not be null");
	}
}
