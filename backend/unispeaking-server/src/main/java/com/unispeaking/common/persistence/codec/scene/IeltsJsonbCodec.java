package com.unispeaking.common.persistence.codec.scene;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.vo.scene.RecommendedExpression;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

@Component
public final class IeltsJsonbCodec {

	private final ObjectReader cuePointReader;
	private final ObjectReader expressionReader;

	public IeltsJsonbCodec(ObjectMapper objectMapper) {
		this.cuePointReader = objectMapper.readerForListOf(String.class);
		this.expressionReader = objectMapper.readerForListOf(
				RecommendedExpression.class);
	}

	public List<String> decodeCuePoints(String json) {
		return read(cuePointReader, json);
	}

	public List<RecommendedExpression> decodeExpressions(String json) {
		return read(expressionReader, json);
	}

	private <T> List<T> read(ObjectReader reader, String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return reader.readValue(json);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(
					"IELTS_DATA_INVALID",
					"雅思题库数据格式错误");
		}
	}
}
