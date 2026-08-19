package com.unispeaking.provider.config;

import java.util.List;
import java.util.Map;

public interface AiProviderCredentialStore {
	String credentialOrFallback(String providerId, String fallback);
	String credentialOrFallback(String providerId, String field, String fallback);
	Map<String, String> credentialsOrFallback(String providerId, Map<String, String> fallback);
	CredentialStatus status(String providerId);
	CredentialStatus replace(String providerId, Map<String, String> values);

	default CredentialStatus replace(String providerId, String plaintext) {
		String primaryField = AiProviderCredentialSchema.definition(providerId).primaryField();
		return replace(providerId, Map.of(primaryField, plaintext));
	}

	record CredentialStatus(
			boolean configured,
			String fingerprint,
			boolean writable,
			List<CredentialFieldStatus> fields) {}

	record CredentialFieldStatus(
			String key,
			String label,
			boolean required,
			boolean secret,
			String description,
			boolean configured,
			String fingerprint) {}
}
