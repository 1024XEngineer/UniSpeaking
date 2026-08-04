package com.unispeaking.infrastructure.persistence.codec.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class IeltsJsonbCodecTest {

	private final IeltsJsonbCodec codec = new IeltsJsonbCodec(
			new ObjectMapper());

	@Test
	void decodesCuePointsAndRecommendedExpressions() {
		assertEquals(
				"where it is",
				codec.decodeCuePoints("[\"where it is\"]").getFirst());

		var expression = codec.decodeExpressions("""
				[{"type":"EXPRESSION","expression":"in my view",\
				"translation":"在我看来","usageNote":"表达观点"}]
				""").getFirst();

		assertEquals("in my view", expression.expression());
		assertEquals("在我看来", expression.translation());
	}

	@Test
	void blankJsonProducesEmptyLists() {
		assertTrue(codec.decodeCuePoints(null).isEmpty());
		assertTrue(codec.decodeExpressions(" ").isEmpty());
	}

	@Test
	void invalidJsonProducesStableBusinessError() {
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> codec.decodeCuePoints("not-json"));

		assertEquals("IELTS_DATA_INVALID", exception.code());
	}
}
