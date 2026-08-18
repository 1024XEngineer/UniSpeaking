package com.unispeaking.admin.provider;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.config.AiModelConfiguration;
import com.unispeaking.provider.config.AiProviderConfiguration;
import com.unispeaking.provider.config.JdbcAiConfigurationStore;
import com.unispeaking.provider.config.AiProviderCredentialStore;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProviderAdminService {
	private final JdbcTemplate jdbc;
	private final JdbcAiConfigurationStore store;
	private final AiProviderRegistry registry;
	private final AiProviderCredentialStore credentialStore;

	public AiProviderAdminService(
			JdbcTemplate jdbc,
			JdbcAiConfigurationStore store,
			AiProviderRegistry registry,
			AiProviderCredentialStore credentialStore) {
		this.jdbc = jdbc;
		this.store = store;
		this.registry = registry;
		this.credentialStore = credentialStore;
	}

	public ConfigurationResponse configuration() {
		var snapshot = store.load();
		List<ProviderView> providers = snapshot.providers().values().stream()
				.sorted(java.util.Comparator.comparing(AiProviderConfiguration::providerId))
				.map(provider -> new ProviderView(
						provider.providerId(), provider.displayName(), provider.adapterType(), provider.baseUrl(),
						provider.enabled(), provider.connectTimeoutMs(), provider.readTimeoutMs(), provider.configVersion()))
				.toList();
		List<ModelView> models = snapshot.models().values().stream()
				.sorted(java.util.Comparator.comparing(AiModelConfiguration::capability)
						.thenComparing(AiModelConfiguration::modelId))
				.map(model -> new ModelView(
						model.modelId(), model.providerId(), model.displayName(), model.capability(), model.enabled(),
						model.billingUnit(), model.inputPricePerMillion(), model.outputPricePerMillion(),
						model.characterPricePerMillion(),
						model.audioInputPricePerMinute(), model.audioOutputPricePerMinute(),
						model.requestPricePerCall(), model.currency()))
				.toList();
		List<RouteView> routes = java.util.Arrays.stream(AiCapability.values())
				.map(capability -> new RouteView("default", capability, snapshot.route("default", capability)))
				.toList();
		return new ConfigurationResponse(providers, models, routes, snapshot.databaseBacked());
	}

	public AiProviderCredentialStore.CredentialStatus credentialStatus(String providerId) {
		provider(providerId);
		return credentialStore.status(providerId);
	}

	@Transactional
	public AiProviderCredentialStore.CredentialStatus replaceCredential(
			String providerId, String secret) {
		provider(providerId);
		return credentialStore.replace(providerId, secret);
	}

	@Transactional
	public ProviderView updateProvider(String providerId, UpdateProviderRequest request) {
		ProviderView before = provider(providerId);
		String displayName = value(request.displayName(), before.displayName());
		boolean enabled = request.enabled() == null ? before.enabled() : request.enabled();
		jdbc.update("update ai_providers set display_name=?, enabled=?, "
				+ "config_version=config_version+1, updated_at=current_timestamp where provider_id=?",
				displayName, enabled, providerId);
		store.invalidate();
		return provider(providerId);
	}

	@Transactional
	public ModelView updateModel(String modelId, UpdateModelRequest request) {
		ModelView before = model(modelId);
		boolean enabled = request.enabled() == null ? before.enabled() : request.enabled();
		String displayName = value(request.displayName(), before.displayName());
		String billingUnit = value(request.billingUnit(), before.billingUnit()).toUpperCase();
		if (!List.of("TOKENS", "AUDIO_MINUTES", "CHARACTERS", "REQUESTS", "MIXED").contains(billingUnit)) {
			throw new BusinessException("AI_BILLING_UNIT_INVALID", "不支持的计费单位");
		}
		BigDecimal inputPrice = nonNegative(request.inputPricePerMillion(), before.inputPricePerMillion());
		BigDecimal outputPrice = nonNegative(request.outputPricePerMillion(), before.outputPricePerMillion());
		BigDecimal characterPrice = nonNegative(request.characterPricePerMillion(), before.characterPricePerMillion());
		BigDecimal audioInputPrice = nonNegative(request.audioInputPricePerMinute(), before.audioInputPricePerMinute());
		BigDecimal audioOutputPrice = nonNegative(request.audioOutputPricePerMinute(), before.audioOutputPricePerMinute());
		BigDecimal requestPrice = nonNegative(request.requestPricePerCall(), before.requestPricePerCall());
		jdbc.update("update ai_models set display_name=?, enabled=?, billing_unit=?, input_price_per_million=?, "
				+ "output_price_per_million=?, character_price_per_million=?, audio_input_price_per_minute=?, audio_output_price_per_minute=?, "
				+ "request_price_per_call=?, updated_at=current_timestamp where model_id=?", displayName, enabled,
				billingUnit, inputPrice, outputPrice, characterPrice, audioInputPrice, audioOutputPrice,
				requestPrice, modelId);
		store.invalidate();
		return model(modelId);
	}

	@Transactional
	public RouteView replaceRoute(AiCapability capability, UpdateRouteRequest request) {
		String routeKey = value(request.routeKey(), "default");
		if (!"default".equals(routeKey)) {
			throw new BusinessException("AI_ROUTE_KEY_UNSUPPORTED", "当前仅支持默认路由");
		}
		List<String> models = request.modelIds() == null ? List.of() : request.modelIds().stream()
				.map(String::trim).filter(value -> !value.isBlank()).toList();
		if (models.isEmpty() || models.size() != new HashSet<>(models).size()) {
			throw new BusinessException("AI_ROUTE_INVALID", "路由至少需要一个不重复的模型");
		}
		var deployed = registry.deployedModels().stream().collect(java.util.stream.Collectors.toMap(
				definition -> definition.modelId(), definition -> definition));
		for (String modelId : models) {
			var definition = deployed.get(modelId);
			if (definition == null || definition.capability() != capability) {
				throw new BusinessException("AI_ROUTE_MODEL_UNAVAILABLE", "当前后端没有部署模型: " + modelId);
			}
			ModelView configured = model(modelId);
			if (!configured.enabled()) {
				throw new BusinessException("AI_ROUTE_MODEL_DISABLED", "路由不能引用已停用模型: " + modelId);
			}
		}
		jdbc.update("update ai_models set route_priority=null, updated_at=current_timestamp where capability=?",
				capability.name());
		for (int index = 0; index < models.size(); index++) {
			jdbc.update("update ai_models set route_priority=?, updated_at=current_timestamp where model_id=?",
					(index + 1) * 10, models.get(index));
		}
		store.invalidate();
		return new RouteView(routeKey, capability, models);
	}

	private ProviderView provider(String providerId) {
		AiProviderConfiguration value = store.load().providers().get(providerId);
		if (value == null) throw new BusinessException("AI_PROVIDER_NOT_FOUND", "Provider 不存在: " + providerId);
		return new ProviderView(value.providerId(), value.displayName(), value.adapterType(), value.baseUrl(),
				value.enabled(), value.connectTimeoutMs(), value.readTimeoutMs(), value.configVersion());
	}

	private ModelView model(String modelId) {
		AiModelConfiguration value = store.load().models().get(modelId);
		if (value == null) throw new BusinessException("AI_MODEL_NOT_FOUND", "模型不存在: " + modelId);
		return new ModelView(value.modelId(), value.providerId(), value.displayName(), value.capability(),
				value.enabled(), value.billingUnit(), value.inputPricePerMillion(), value.outputPricePerMillion(),
				value.characterPricePerMillion(),
				value.audioInputPricePerMinute(), value.audioOutputPricePerMinute(),
				value.requestPricePerCall(), value.currency());
	}

	private static String value(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static BigDecimal nonNegative(BigDecimal value, BigDecimal fallback) {
		BigDecimal resolved = value == null ? fallback : value;
		if (resolved.signum() < 0) throw new BusinessException("AI_MODEL_PRICE_INVALID", "模型价格不能小于 0");
		return resolved;
	}

	public record ConfigurationResponse(List<ProviderView> providers, List<ModelView> models, List<RouteView> routes, boolean databaseBacked) {}
	public record ProviderView(String providerId, String displayName, String adapterType, String baseUrl, boolean enabled, int connectTimeoutMs, int readTimeoutMs, long configVersion) {}
	public record ModelView(String modelId, String providerId, String displayName, AiCapability capability, boolean enabled, String billingUnit, BigDecimal inputPricePerMillion, BigDecimal outputPricePerMillion, BigDecimal characterPricePerMillion, BigDecimal audioInputPricePerMinute, BigDecimal audioOutputPricePerMinute, BigDecimal requestPricePerCall, String currency) {}
	public record RouteView(String routeKey, AiCapability capability, List<String> modelIds) {}
	public record UpdateProviderRequest(String displayName, Boolean enabled) {}
	public record UpdateModelRequest(String displayName, Boolean enabled, String billingUnit, BigDecimal inputPricePerMillion, BigDecimal outputPricePerMillion, BigDecimal characterPricePerMillion, BigDecimal audioInputPricePerMinute, BigDecimal audioOutputPricePerMinute, BigDecimal requestPricePerCall) {}
	public record UpdateRouteRequest(String routeKey, List<String> modelIds) {}
	public record UpdateCredentialRequest(String secret) {}
}
