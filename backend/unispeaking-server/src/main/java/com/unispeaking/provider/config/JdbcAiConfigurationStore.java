package com.unispeaking.provider.config;

import com.unispeaking.domain.vo.provider.AiCapability;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

@Component
public final class JdbcAiConfigurationStore implements AiConfigurationStore {
	private static final Logger LOGGER = LoggerFactory.getLogger(JdbcAiConfigurationStore.class);
	private static final Duration CACHE_TTL = Duration.ofSeconds(10);

	private final JdbcTemplate jdbc;
	private final AtomicReference<CachedConfiguration> cache = new AtomicReference<>();

	public JdbcAiConfigurationStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public AiRuntimeConfiguration load() {
		CachedConfiguration current = cache.get();
		Instant now = Instant.now();
		if (current != null && current.expiresAt().isAfter(now)) return current.configuration();
		try {
			AiRuntimeConfiguration loaded = loadFromDatabase();
			cache.set(new CachedConfiguration(loaded, now.plus(CACHE_TTL)));
			return loaded;
		}
		catch (DataAccessException exception) {
			if (current != null) {
				LOGGER.warn("AI configuration refresh failed; using last known database snapshot");
				return current.configuration();
			}
			return new AiRuntimeConfiguration(Map.of(), Map.of(), Map.of(), false);
		}
	}

	public void invalidate() {
		cache.set(null);
	}

	private AiRuntimeConfiguration loadFromDatabase() {
		Map<String, AiProviderConfiguration> providers = new LinkedHashMap<>();
		jdbc.query("select provider_id, display_name, adapter_type, base_url, enabled, "
				+ "connect_timeout_ms, read_timeout_ms, config_version from ai_providers",
				(RowCallbackHandler) rs -> providers.put(rs.getString("provider_id"), new AiProviderConfiguration(
						rs.getString("provider_id"), rs.getString("display_name"),
						rs.getString("adapter_type"), rs.getString("base_url"), rs.getBoolean("enabled"),
						rs.getInt("connect_timeout_ms"), rs.getInt("read_timeout_ms"),
						rs.getLong("config_version"))));

		Map<String, AiModelConfiguration> models = new LinkedHashMap<>();
		Map<String, Map<AiCapability, List<String>>> mutableRoutes = new LinkedHashMap<>();
		jdbc.query("select model_id, provider_id, display_name, capability, enabled, billing_unit, "
				+ "input_price_per_million, output_price_per_million, character_price_per_million, audio_input_price_per_minute, "
				+ "audio_output_price_per_minute, request_price_per_call, price_currency, route_priority from ai_models "
				+ "order by capability, route_priority nulls last, model_id",
				(RowCallbackHandler) rs -> {
					AiCapability capability = AiCapability.valueOf(rs.getString("capability"));
					models.put(rs.getString("model_id"), new AiModelConfiguration(
						rs.getString("model_id"), rs.getString("provider_id"), rs.getString("display_name"),
						capability, rs.getBoolean("enabled"),
						rs.getString("billing_unit"), rs.getBigDecimal("input_price_per_million"),
						rs.getBigDecimal("output_price_per_million"),
						rs.getBigDecimal("character_price_per_million"),
						rs.getBigDecimal("audio_input_price_per_minute"),
						rs.getBigDecimal("audio_output_price_per_minute"),
						rs.getBigDecimal("request_price_per_call"), rs.getString("price_currency")));
					if (rs.getObject("route_priority") != null) {
						mutableRoutes.computeIfAbsent("default", ignored -> new EnumMap<>(AiCapability.class))
							.computeIfAbsent(capability, ignored -> new ArrayList<>())
							.add(rs.getString("model_id"));
					}
				});
		Map<String, Map<AiCapability, List<String>>> routes = new LinkedHashMap<>();
		mutableRoutes.forEach((key, value) -> {
			Map<AiCapability, List<String>> copied = new EnumMap<>(AiCapability.class);
			value.forEach((capability, route) -> copied.put(capability, List.copyOf(route)));
			routes.put(key, Map.copyOf(copied));
		});
		return new AiRuntimeConfiguration(providers, models, routes, true);
	}

	private record CachedConfiguration(AiRuntimeConfiguration configuration, Instant expiresAt) {}
}
