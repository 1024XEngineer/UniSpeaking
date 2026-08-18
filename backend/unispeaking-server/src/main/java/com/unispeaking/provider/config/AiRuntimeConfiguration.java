package com.unispeaking.provider.config;

import com.unispeaking.domain.vo.provider.AiCapability;
import java.util.List;
import java.util.Map;

public record AiRuntimeConfiguration(
		Map<String, AiProviderConfiguration> providers,
		Map<String, AiModelConfiguration> models,
		Map<String, Map<AiCapability, List<String>>> routes,
		boolean databaseBacked) {

	public AiRuntimeConfiguration {
		providers = providers == null ? Map.of() : Map.copyOf(providers);
		models = models == null ? Map.of() : Map.copyOf(models);
		routes = routes == null ? Map.of() : Map.copyOf(routes);
	}

	public List<String> route(String routeKey, AiCapability capability) {
		String normalizedKey = routeKey == null || routeKey.isBlank() ? "default" : routeKey.trim();
		Map<AiCapability, List<String>> policy = routes.get(normalizedKey);
		if (policy == null && !"default".equals(normalizedKey)) policy = routes.get("default");
		return policy == null ? List.of() : policy.getOrDefault(capability, List.of());
	}
}
