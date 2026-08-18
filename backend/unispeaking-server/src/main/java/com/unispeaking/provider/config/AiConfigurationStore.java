package com.unispeaking.provider.config;

import com.unispeaking.domain.vo.provider.AiCapability;
import java.util.List;

public interface AiConfigurationStore {
	AiRuntimeConfiguration load();

	default boolean available() {
		return true;
	}

	default List<String> route(String routeKey, AiCapability capability) {
		return load().route(routeKey, capability);
	}
}
