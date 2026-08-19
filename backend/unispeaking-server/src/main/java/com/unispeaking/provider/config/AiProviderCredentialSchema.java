package com.unispeaking.provider.config;

import com.unispeaking.common.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiProviderCredentialSchema {
	private static final Map<String, ProviderDefinition> DEFINITIONS = definitions();

	private AiProviderCredentialSchema() {}

	public static ProviderDefinition definition(String providerId) {
		ProviderDefinition definition = DEFINITIONS.get(normalize(providerId));
		if (definition == null) {
			throw new BusinessException("AI_PROVIDER_CREDENTIAL_SCHEMA_MISSING",
					"Provider 暂不支持后台配置: " + providerId);
		}
		return definition;
	}

	private static Map<String, ProviderDefinition> definitions() {
		Map<String, ProviderDefinition> definitions = new LinkedHashMap<>();
		definitions.put("aliyun", provider("aliyun", "apiKey",
				field("apiKey", "API Key", true, true, "百炼 / DashScope API Key"),
				field("workspaceId", "Workspace ID", true, false, "百炼业务空间 ID，用于构建 CosyVoice 请求地址"),
				field("region", "Region", false, false, "百炼地域，留空时使用服务器配置")));
		definitions.put("deepseek", provider("deepseek", "apiKey",
				field("apiKey", "API Key", true, true, "DeepSeek API Key")));
		definitions.put("doubao", provider("doubao", "apiKey",
				field("appId", "App ID", true, false, "豆包语音应用 ID"),
				field("accessToken", "Access Token", true, true, "豆包语音访问令牌"),
				field("cluster", "Cluster", true, false, "ASR/TTS 接口使用的集群标识")));
		definitions.put("iflytek", provider("iflytek", "apiKey",
				field("appId", "App ID", true, false, "讯飞开放平台应用 ID"),
				field("apiKey", "API Key", true, true, "讯飞 WebAPI API Key"),
				field("apiSecret", "API Secret", true, true, "讯飞 WebAPI API Secret")));
		definitions.put("minimax", provider("minimax", "apiKey",
				field("apiKey", "API Key", true, true, "MiniMax API Key")));
		definitions.put("qiniu", provider("qiniu", "apiKey",
				field("accessKey", "Access Key", true, false, "七牛云 RTI Access Key"),
				field("secretKey", "Secret Key", true, true, "七牛云 RTI Secret Key"),
				field("appId", "App ID", true, false, "七牛云 RTI 应用 ID")));
		definitions.put("qiniu-maas", provider("qiniu-maas", "apiKey",
				field("apiKey", "API Key", true, true, "七牛云 MaaS 独立 API Key")));
		definitions.put("qwen", provider("qwen", "apiKey",
				field("apiKey", "API Key", true, true, "百炼 / DashScope API Key"),
				field("workspaceId", "Workspace ID", false, false, "部分地域的百炼接口需要配置")));
		return Map.copyOf(definitions);
	}

	private static ProviderDefinition provider(
			String providerId, String primaryField, FieldDefinition... fields) {
		return new ProviderDefinition(providerId, primaryField, List.of(fields));
	}

	private static FieldDefinition field(
			String key, String label, boolean required, boolean secret, String description) {
		return new FieldDefinition(key, label, required, secret, description);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
	}

	public record ProviderDefinition(
			String providerId, String primaryField, List<FieldDefinition> fields) {
		public FieldDefinition field(String key) {
			return fields.stream().filter(field -> field.key().equals(key)).findFirst().orElse(null);
		}
	}

	public record FieldDefinition(
			String key, String label, boolean required, boolean secret, String description) {}
}
